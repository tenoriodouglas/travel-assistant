package com.travelassistant.app.data

import com.travelassistant.app.data.model.Airport
import com.travelassistant.app.data.model.Granularity
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

/**
 * Real price source backed by Travelpayouts (Aviasales) cached prices — free, token-only.
 * Fetches the cheapest tickets per month across the selected window, keeps the cheapest fare per
 * day, buckets those into candles by the chosen granularity (each candle low = cheapest in the
 * bucket), and derives a per-airline breakdown with deep links to buy. On-demand ([refreshNow]).
 */
class TravelpayoutsPriceRepository(
    private val service: TravelpayoutsService,
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

        // Cheapest fare per departure day.
        val perDay = inWindow.groupBy { it.date }
            .map { (date, list) -> date to list.minOf { it.price } }
            .sortedBy { it.first }

        val (candles, xLabels) = buildBuckets(perDay, granularity, clock())

        val prices = perDay.map { it.second }
        val minPrice = prices.min()
        val maxPrice = prices.max()
        val nearest = perDay.first().second
        val cheapest = perDay.minByOrNull { it.second }!!
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
            quotes = buildQuotes(inWindow, prices),
            lastUpdated = clock(),
        )
    }

    private fun buildQuotes(tickets: List<Ticket>, curve: List<Double>): List<ProviderQuote> =
        tickets.filter { it.airline.isNotBlank() }
            .groupBy { it.airline }
            .map { (code, list) ->
                val best = list.minByOrNull { it.price }!!
                ProviderQuote(
                    provider = Provider(code, Airlines.name(code), ProviderKind.AIRLINE),
                    price = best.price,
                    changePct = 0.0,
                    spark = curve,
                    bookingUrl = bookingUrl(best.link),
                )
            }
            .sortedBy { it.price }

    private fun bookingUrl(link: String?): String? {
        if (link.isNullOrBlank()) return null
        return if (link.startsWith("http")) link else "https://www.aviasales.com$link"
    }

    private companion object {
        val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
        val YEAR_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    }
}
