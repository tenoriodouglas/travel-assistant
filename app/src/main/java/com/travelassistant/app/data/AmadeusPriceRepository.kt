package com.travelassistant.app.data

import com.travelassistant.app.data.model.Airport
import com.travelassistant.app.data.model.Granularity
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
 * Real price source backed by Amadeus. Prices come from Flight Cheapest Date Search, falling back
 * to sampling Flight Offers Search; the per-airline breakdown uses Flight Offers Search on the
 * cheapest date. On-demand ([refreshNow]). Amadeus offers have no public deep link, so quotes
 * carry no bookingUrl.
 */
class AmadeusPriceRepository(
    private val service: AmadeusService,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : PriceRepository {

    private val _lastTick = MutableStateFlow(0L)
    override val lastTick: StateFlow<Long> = _lastTick.asStateFlow()

    override fun refreshNow() {
        _lastTick.value = clock()
    }

    override suspend fun board(
        origin: Airport,
        destination: Airport,
        range: TimeRange,
        granularity: Granularity,
    ): RouteBoard {
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

        val perDay = points.map { it.date to it.price }.sortedBy { it.first }
        val (candles, xLabels) = buildBuckets(perDay, granularity, clock())

        val prices = perDay.map { it.second }
        val cheapest = perDay.minByOrNull { it.second }!!
        val nearest = perDay.first().second
        val minPrice = prices.min()
        val maxPrice = prices.max()
        val changePct = if (nearest != 0.0) (minPrice - nearest) / nearest * 100.0 else 0.0

        return RouteBoard(
            route = Route(origin, destination),
            range = range,
            granularity = granularity,
            candles = candles,
            xLabels = xLabels,
            price = minPrice,
            openPrice = nearest,
            high = maxPrice,
            low = minPrice,
            changePct = changePct,
            cheapestDateLabel = cheapest.first.format(DAY_MONTH),
            quotes = buildQuotes(origin, destination, cheapest.first, currency, prices),
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
        cheapestDate: LocalDate,
        currency: String,
        curve: List<Double>,
    ): List<ProviderQuote> {
        val offers = runCatching { service.offers(origin.code, destination.code, cheapestDate, currency) }
            .getOrDefault(emptyList())
        return offers.groupBy { it.airlineCode }
            .mapValues { (_, list) -> list.minOf { it.price } }
            .entries
            .sortedBy { it.value }
            .map { (code, price) ->
                ProviderQuote(
                    provider = Provider(code, Airlines.name(code), ProviderKind.AIRLINE),
                    price = price,
                    changePct = 0.0,
                    spark = curve,
                    bookingUrl = null,
                )
            }
    }

    private companion object {
        val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
    }
}
