package com.travelassistant.app.data

import com.travelassistant.app.data.model.Airport
import com.travelassistant.app.data.model.Candle
import com.travelassistant.app.data.model.Provider
import com.travelassistant.app.data.model.ProviderKind
import com.travelassistant.app.data.model.ProviderQuote
import com.travelassistant.app.data.model.Route
import com.travelassistant.app.data.model.RouteBoard
import com.travelassistant.app.data.model.TimeRange
import com.travelassistant.app.data.remote.Ticket
import com.travelassistant.app.data.remote.TravelpayoutsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/**
 * Real price source backed by Travelpayouts (Aviasales) cached prices — free, token-only.
 * Fetches the cheapest tickets per month across the selected window, groups them by
 * departure date into a price curve, and derives the per-airline breakdown from the same
 * data. On-demand (not a 30s feed); [refreshNow] triggers a re-fetch.
 */
class TravelpayoutsPriceRepository(
    private val service: TravelpayoutsService,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : PriceRepository {

    override val isLive: Boolean = false

    private val _lastTick = MutableStateFlow(0L)
    override val lastTick: StateFlow<Long> = _lastTick.asStateFlow()

    override fun refreshNow() {
        _lastTick.value = clock()
    }

    override suspend fun board(origin: Airport, destination: Airport, range: TimeRange): RouteBoard {
        val currency = "brl"
        val today = LocalDate.now()
        val horizon = today.plusDays(range.days.toLong())

        val tickets = ArrayList<Ticket>()
        var month = YearMonth.from(today)
        val lastMonth = YearMonth.from(horizon)
        while (!month.isAfter(lastMonth)) {
            val batch = runCatching {
                service.pricesForMonth(origin.code, destination.code, month.format(YEAR_MONTH), currency)
            }.getOrDefault(emptyList())
            tickets.addAll(batch)
            month = month.plusMonths(1)
        }

        val inWindow = tickets.filter { !it.date.isBefore(today) && !it.date.isAfter(horizon) }
        if (inWindow.isEmpty()) {
            throw IOException("Sem preços para ${origin.code}→${destination.code} nesse período.")
        }

        // One point per departure date = cheapest ticket that day.
        val points = inWindow.groupBy { it.date }
            .map { (date, list) -> date to list.minOf { it.price } }
            .sortedBy { it.first }

        val prices = points.map { it.second }
        val minPrice = prices.min()
        val maxPrice = prices.max()
        val nearest = points.first().second
        val cheapest = points.minByOrNull { it.second }!!
        val changePct = if (nearest != 0.0) (minPrice - nearest) / nearest * 100.0 else 0.0

        val (candles, xLabels) = buildCandles(points, range, clock())
        val quotes = buildQuotes(inWindow, prices)

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
            cheapestDateLabel = cheapest.first.format(DAY_MONTH),
            quotes = quotes,
            lastUpdated = clock(),
        )
    }

    private fun buildQuotes(tickets: List<Ticket>, curve: List<Double>): List<ProviderQuote> =
        tickets.filter { it.airline.isNotBlank() }
            .groupBy { it.airline }
            .mapValues { (_, list) -> list.minOf { it.price } }
            .entries
            .sortedBy { it.value }
            .map { (code, price) ->
                ProviderQuote(
                    provider = Provider(code, Airlines.name(code), ProviderKind.AIRLINE),
                    price = price,
                    changePct = 0.0,
                    spark = curve,
                )
            }

    private fun buildCandles(
        points: List<Pair<LocalDate, Double>>,
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
            val open = prevClose ?: slice.first().second
            val close = slice.last().second
            var high = slice.maxOf { it.second }.coerceAtLeast(max(open, close))
            var low = slice.minOf { it.second }.coerceAtMost(min(open, close))
            if (high == low) { high = close * 1.003; low = close * 0.997 }
            candles.add(Candle(now, open, high, low, close))
            labels.add(slice.first().first.format(DAY_MONTH))
            prevClose = close
            i += perBucket
        }
        return candles to labels
    }

    private companion object {
        val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
        val YEAR_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    }
}
