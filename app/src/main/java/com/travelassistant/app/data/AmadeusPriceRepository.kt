package com.travelassistant.app.data

import com.travelassistant.app.data.model.Airport
import com.travelassistant.app.data.model.Candle
import com.travelassistant.app.data.model.Provider
import com.travelassistant.app.data.model.ProviderKind
import com.travelassistant.app.data.model.ProviderQuote
import com.travelassistant.app.data.model.Route
import com.travelassistant.app.data.model.RouteBoard
import com.travelassistant.app.data.model.TimeRange
import com.travelassistant.app.data.remote.AmadeusService
import com.travelassistant.app.data.remote.DatePrice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/**
 * Real price source backed by Amadeus. Prices come from Flight Cheapest Date Search (one call
 * covers many future dates); when a route isn't covered there, it falls back to sampling Flight
 * Offers Search across a handful of dates. The per-airline breakdown uses Flight Offers Search
 * on the cheapest date. This is on-demand (not a 30s feed): [refreshNow] triggers a re-fetch.
 */
class AmadeusPriceRepository(
    private val service: AmadeusService,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : PriceRepository {

    override val isLive: Boolean = false

    private val _lastTick = MutableStateFlow(0L)
    override val lastTick: StateFlow<Long> = _lastTick.asStateFlow()

    override fun refreshNow() {
        _lastTick.value = clock()
    }

    override suspend fun board(origin: Airport, destination: Airport, range: TimeRange): RouteBoard {
        val currency = "BRL"
        val today = LocalDate.now()
        val horizon = today.plusDays(range.days.toLong())

        var points = runCatching { service.cheapestDates(origin.code, destination.code, currency) }
            .getOrDefault(emptyList())
            .filter { !it.date.isBefore(today) && !it.date.isAfter(horizon) }
            .sortedBy { it.date }

        if (points.size < 3) {
            points = sampleOffers(origin, destination, range, today, currency)
        }
        if (points.isEmpty()) {
            throw IOException("A API não retornou preços para ${origin.code}→${destination.code}.")
        }

        val prices = points.map { it.price }
        val cheapest = points.minByOrNull { it.price }!!
        val nearest = points.first().price
        val minPrice = prices.min()
        val maxPrice = prices.max()
        val changePct = if (nearest != 0.0) (minPrice - nearest) / nearest * 100.0 else 0.0

        val (candles, xLabels) = buildCandles(points, range, clock())
        val quotes = buildQuotes(origin, destination, cheapest, currency, prices)

        return RouteBoard(
            route = Route(origin, destination),
            range = range,
            candles = candles,
            xLabels = xLabels,
            price = minPrice,
            openPrice = nearest,
            high = maxPrice,
            low = minPrice,
            changePct = changePct,
            cheapestDateLabel = cheapest.date.format(DAY_MONTH),
            quotes = quotes,
            lastUpdated = clock(),
        )
    }

    private suspend fun sampleOffers(
        origin: Airport,
        destination: Airport,
        range: TimeRange,
        today: LocalDate,
        currency: String,
    ): List<DatePrice> {
        val samples = min(8, range.days)
        if (samples <= 0) return emptyList()
        val step = max(1, range.days / samples)
        val out = ArrayList<DatePrice>()
        var offset = 1
        while (offset <= range.days && out.size < samples) {
            val date = today.plusDays(offset.toLong())
            val cheapest = runCatching { service.offers(origin.code, destination.code, date, currency) }
                .getOrDefault(emptyList())
                .minByOrNull { it.price }
            if (cheapest != null) out.add(DatePrice(date, cheapest.price))
            offset += step
        }
        return out
    }

    private suspend fun buildQuotes(
        origin: Airport,
        destination: Airport,
        cheapest: DatePrice,
        currency: String,
        curve: List<Double>,
    ): List<ProviderQuote> {
        val offers = runCatching { service.offers(origin.code, destination.code, cheapest.date, currency) }
            .getOrDefault(emptyList())
        val byAirline = offers.groupBy { it.airlineCode }
            .mapValues { (_, list) -> list.minOf { it.price } }
        return byAirline.entries
            .sortedBy { it.value }
            .map { (code, price) ->
                ProviderQuote(
                    provider = Provider(code, airlineName(code), ProviderKind.AIRLINE),
                    price = price,
                    changePct = 0.0,
                    spark = curve,
                )
            }
    }

    private fun buildCandles(
        points: List<DatePrice>,
        range: TimeRange,
        now: Long,
    ): Pair<List<Candle>, List<String>> {
        val buckets = min(range.buckets, points.size)
        if (buckets <= 0) return emptyList<Candle>() to emptyList()
        val perBucket = max(1, points.size / buckets)
        val candles = ArrayList<Candle>()
        val labels = ArrayList<String>()
        var prevClose: Double? = null
        var i = 0
        while (i < points.size) {
            val slice = points.subList(i, min(points.size, i + perBucket))
            if (slice.isEmpty()) break
            val open = prevClose ?: slice.first().price
            val close = slice.last().price
            var high = slice.maxOf { it.price }.coerceAtLeast(max(open, close))
            var low = slice.minOf { it.price }.coerceAtMost(min(open, close))
            if (high == low) { high = close * 1.003; low = close * 0.997 }
            candles.add(Candle(now, open, high, low, close))
            labels.add(slice.first().date.format(DAY_MONTH))
            prevClose = close
            i += perBucket
        }
        return candles to labels
    }

    private companion object {
        val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

        // Common validating-carrier codes -> friendly names; unknown codes show the raw code.
        val AIRLINES = mapOf(
            "LA" to "LATAM", "JJ" to "LATAM", "G3" to "GOL", "AD" to "Azul", "TP" to "TAP",
            "AA" to "American", "UA" to "United", "DL" to "Delta", "AF" to "Air France",
            "KL" to "KLM", "IB" to "Iberia", "BA" to "British Airways", "LH" to "Lufthansa",
            "AR" to "Aerolíneas", "AV" to "Avianca", "CM" to "Copa", "AC" to "Air Canada",
            "AZ" to "ITA Airways", "UX" to "Air Europa", "EK" to "Emirates", "AT" to "Royal Air Maroc",
        )

        fun airlineName(code: String): String = AIRLINES[code] ?: code
    }
}
