package com.travelassistant.app.data.model

/** A tracked origin -> destination route, the "trading pair" of the app. */
data class Route(
    val id: String,
    val origin: String,
    val destination: String,
    val originCity: String,
    val destinationCity: String,
    val currency: String = "R$",
) {
    val pair: String get() = "$origin/$destination"
}

/** Kind of price source shown on the breakdown. */
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

/** OHLC candle aggregated over one refresh interval, crypto-chart style. */
data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
) {
    val isBullish: Boolean get() = close >= open
}

/** Current quote for one provider on a route. */
data class ProviderQuote(
    val provider: Provider,
    val price: Double,
    val changePct: Double,
    val spark: List<Double>,
)

/** Row model for the markets list. */
data class RouteMarket(
    val route: Route,
    val price: Double,
    val openPrice: Double,
    val high: Double,
    val low: Double,
    val changePct: Double,
    val spark: List<Double>,
    val lastUpdated: Long,
) {
    val changeAbs: Double get() = price - openPrice
    val isUp: Boolean get() = changePct >= 0.0
}

/** Full detail model for a single route. */
data class RouteDetail(
    val market: RouteMarket,
    val candles: List<Candle>,
    val line: List<PricePoint>,
    val quotes: List<ProviderQuote>,
) {
    /** Cheapest provider = the "best buy" signal. */
    val bestQuote: ProviderQuote? get() = quotes.minByOrNull { it.price }
}
