package com.travelassistant.app.data.model

/** An airport / city the user can pick as origin or destination. */
data class Airport(
    val code: String,       // IATA, e.g. "GRU"
    val city: String,       // e.g. "São Paulo"
    val country: String,    // e.g. "Brasil"
    val countryCode: String, // e.g. "BR"
    val keywords: String = "", // extra search terms (accent-free aliases)
) {
    val label: String get() = "$city ($code)"
}

/** A directional origin -> destination pair (the app's "trading pair"). */
data class Route(
    val origin: Airport,
    val destination: Airport,
    val currency: String = "R$",
) {
    val pair: String get() = "${origin.code}/${destination.code}"
    val id: String get() = "${origin.code}-${destination.code}"
}

/** Future-departure window, chosen with crypto-style range buttons. */
enum class TimeRange(val label: String, val days: Int, val buckets: Int) {
    D7("7D", 7, 7),
    D15("15D", 15, 15),
    M1("1M", 30, 15),
    M3("3M", 90, 18),
    M6("6M", 180, 18),
    Y1("1A", 360, 24),
}

/** How much time each candle aggregates. Each candle shows the cheapest fare in its bucket. */
enum class Granularity(val label: String) {
    DAY("Dia"),
    WEEK("Semana"),
    MONTH("Mês"),
}

enum class ProviderKind { AIRLINE, PLATFORM }

/** An airline or a ticket-buying platform whose price we track. */
data class Provider(
    val id: String,
    val name: String,
    val kind: ProviderKind,
)

/** A single price observation in time (used for the line chart / sparkline). */
data class PricePoint(
    val timestamp: Long,
    val price: Double,
)

/** OHLC candle aggregated over one bucket of future departure dates. */
data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
) {
    val isBullish: Boolean get() = close >= open
}

/** Current quote for one provider on a route (min price in the window + trend). */
data class ProviderQuote(
    val provider: Provider,
    val price: Double,
    val changePct: Double,
    val spark: List<Double>,
    /** Deep link to buy this fare, or null when the provider has no link. */
    val bookingUrl: String? = null,
)

/**
 * Everything shown for the selected origin -> destination + time range.
 * Candles are price-by-future-departure-date buckets; the header numbers
 * summarize the selected window. Recomputed live on every feed tick.
 */
data class RouteBoard(
    val route: Route,
    val range: TimeRange,
    val granularity: Granularity,
    val candles: List<Candle>,
    val xLabels: List<String>,      // departure date label per candle (dd/MM)
    val price: Double,              // cheapest price in the window (actionable)
    val openPrice: Double,          // window reference at session start
    val high: Double,
    val low: Double,
    val changePct: Double,          // vs session-open reference for this range
    val cheapestDateLabel: String,  // best future departure date in the window
    val quotes: List<ProviderQuote>,
    val lastUpdated: Long,
) {
    val isUp: Boolean get() = changePct >= 0.0
    val changeAbs: Double get() = price - openPrice
    val bestQuote: ProviderQuote? get() = quotes.minByOrNull { it.price }
}
