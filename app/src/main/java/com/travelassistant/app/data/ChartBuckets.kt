package com.travelassistant.app.data

import com.travelassistant.app.data.model.Candle
import com.travelassistant.app.data.model.Granularity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val ptBr = Locale("pt", "BR")
private val DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM")
private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM/yy", ptBr)

/**
 * Groups per-day cheapest prices into OHLC candles by [Granularity]. Each candle's low is the
 * cheapest fare in that day/week/month bucket — the "menor preço" for that granularity.
 * [points] must be one (date, cheapestPrice) per date, sorted ascending by date.
 */
fun buildBuckets(
    points: List<Pair<LocalDate, Double>>,
    granularity: Granularity,
    now: Long,
): Pair<List<Candle>, List<String>> {
    if (points.isEmpty()) return emptyList<Candle>() to emptyList()

    val grouped = LinkedHashMap<Any, MutableList<Pair<LocalDate, Double>>>()
    for (p in points) {
        val key: Any = when (granularity) {
            Granularity.DAY -> p.first
            Granularity.WEEK -> p.first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            Granularity.MONTH -> YearMonth.from(p.first)
        }
        grouped.getOrPut(key) { mutableListOf() }.add(p)
    }

    val candles = ArrayList<Candle>()
    val labels = ArrayList<String>()
    var prevClose: Double? = null
    for (group in grouped.values) {
        val prices = group.map { it.second }
        val open = prevClose ?: prices.first()
        val close = prices.last()
        var high = max(prices.max(), max(open, close))
        var low = min(prices.min(), min(open, close))
        if (high == low) { high = close * 1.003; low = close * 0.997 }
        candles.add(Candle(now, open, high, low, close))
        labels.add(labelFor(group.first().first, granularity))
        prevClose = close
    }
    return candles to labels
}

private fun labelFor(date: LocalDate, granularity: Granularity): String = when (granularity) {
    Granularity.MONTH -> date.format(MONTH_YEAR)
    else -> date.format(DAY_MONTH)
}
