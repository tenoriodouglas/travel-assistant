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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Real price source backed by Travelpayouts (Aviasales) — free, token-only.
 *
 * Data flow, tuned to fill the whole chart (not just a few candles):
 *  1. The dense price curve comes from the **Calendar of Prices** endpoint, which returns the
 *     cheapest fare for *every* day of a month. All months in the window are fetched in parallel.
 *  2. The per-airline breakdown + "buy" links come from **Prices for Dates** (airline + deep link),
 *     also fetched in parallel.
 *  3. If the calendar is empty for the route, we fall back to the per-day minimum from the tickets.
 *
 * Results are cached per (origin, destination, range) so toggling the granularity (Dia/Semana/Mês)
 * only re-buckets in memory — no extra network. [refreshNow] bumps the tick and clears the cache.
 */
class TravelpayoutsPriceRepository(
    private val service: TravelpayoutsService,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : PriceRepository {

    private val _lastTick = MutableStateFlow(0L)
    override val lastTick: StateFlow<Long> = _lastTick.asStateFlow()

    /** Cached raw fetch per route+range, so granularity changes don't re-hit the network. */
    private data class Fetched(
        val perDay: List<Pair<LocalDate, Double>>,
        val tickets: List<Ticket>,
    )

    private val cache = HashMap<String, Fetched>()

    override fun refreshNow() {
        synchronized(cache) { cache.clear() }
        _lastTick.value = clock()
    }

    override suspend fun board(
        origin: Airport,
        destination: Airport,
        range: TimeRange,
        granularity: Granularity,
    ): RouteBoard {
        val today = LocalDate.now()
        val horizon = today.plusDays(range.days.toLong())
        val key = "${origin.code}-${destination.code}-${range.name}"

        val fetched = synchronized(cache) { cache[key] }
            ?: fetch(origin, destination, today, horizon).also {
                synchronized(cache) { cache[key] = it }
            }

        val perDay = fetched.perDay
        if (perDay.isEmpty()) {
            throw IOException("Sem preços para ${origin.code}→${destination.code} nesse período.")
        }

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
            quotes = buildQuotes(fetched.tickets, prices),
            lastUpdated = clock(),
        )
    }

    /** Fetches the dense curve (calendar) and airline tickets for the whole window, in parallel. */
    private suspend fun fetch(
        origin: Airport,
        destination: Airport,
        today: LocalDate,
        horizon: LocalDate,
    ): Fetched = coroutineScope {
        val currency = "brl"
        val months = buildList {
            var m = YearMonth.from(today)
            val last = YearMonth.from(horizon)
            while (!m.isAfter(last)) { add(m); m = m.plusMonths(1) }
        }
        val gate = Semaphore(MAX_CONCURRENT)

        val calendarJobs = months.map { month ->
            async {
                gate.withPermit {
                    runCatching {
                        service.calendarForMonth(
                            origin.code, destination.code, month.format(YEAR_MONTH), currency,
                        )
                    }.getOrDefault(emptyList())
                }
            }
        }
        val ticketJobs = months.map { month ->
            async {
                gate.withPermit {
                    runCatching {
                        service.pricesForMonth(
                            origin.code, destination.code, month.format(YEAR_MONTH), currency,
                        )
                    }.getOrDefault(emptyList())
                }
            }
        }

        val dayPrices = calendarJobs.awaitAll().flatten()
            .filter { !it.date.isBefore(today) && !it.date.isAfter(horizon) }
        val tickets = ticketJobs.awaitAll().flatten()
            .filter { !it.date.isBefore(today) && !it.date.isAfter(horizon) }

        // Dense curve: cheapest fare per day. Prefer the calendar; fall back to ticket days.
        val perDay = when {
            dayPrices.isNotEmpty() -> dayPrices.groupBy { it.date }
                .map { (date, list) -> date to list.minOf { it.price } }
            else -> tickets.groupBy { it.date }
                .map { (date, list) -> date to list.minOf { it.price } }
        }.sortedBy { it.first }

        Fetched(perDay, tickets)
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
        const val MAX_CONCURRENT = 6
        val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
        val YEAR_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    }
}
