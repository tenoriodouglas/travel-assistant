package com.travelassistant.app.data

import com.travelassistant.app.data.model.Candle
import com.travelassistant.app.data.model.PricePoint
import com.travelassistant.app.data.model.Provider
import com.travelassistant.app.data.model.ProviderKind
import com.travelassistant.app.data.model.ProviderQuote
import com.travelassistant.app.data.model.Route
import com.travelassistant.app.data.model.RouteDetail
import com.travelassistant.app.data.model.RouteMarket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * In-memory simulated market that behaves like a live price feed. Each route holds a
 * running OHLC candle series and a set of provider quotes (airlines + platforms) that
 * evolve with a bounded random walk on every [tick]. The cadence of ticks is driven by
 * the user-configurable refresh interval from [SettingsRepository].
 *
 * The architecture (repository exposing StateFlows advanced by a single ticking loop)
 * is API-shaped: swapping the random walk for a real pricing API only touches [tick].
 */
class MarketRepository(
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val random = Random(42)
    private val states: List<RouteState> = seedRoutes().map { RouteState(it) }

    private val _markets = MutableStateFlow(states.map { it.toMarket() })
    val markets: StateFlow<List<RouteMarket>> = _markets.asStateFlow()

    /** Emits the timestamp of the latest feed update; the UI observes it to pulse "LIVE". */
    private val _lastTick = MutableStateFlow(clock())
    val lastTick: StateFlow<Long> = _lastTick.asStateFlow()

    private var started = false

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            settings.refreshIntervalSeconds
                .flatMapLatest { intervalSeconds -> tickingFlow(intervalSeconds) }
                .collect { tick() }
        }
    }

    /** Force an immediate feed advance (pull-to-refresh / refresh button). */
    fun refreshNow() {
        scope.launch { tick() }
    }

    /** Latest computed detail for a route, or null if unknown. Re-read on each [lastTick]. */
    fun detailSnapshot(routeId: String): RouteDetail? =
        states.firstOrNull { it.route.id == routeId }?.toDetail()

    private fun tick() {
        val now = clock()
        states.forEach { it.advance(random, now) }
        _markets.value = states.map { it.toMarket() }
        _lastTick.value = now
    }

    private fun tickingFlow(intervalSeconds: Int) = flow {
        val delayMs = intervalSeconds.coerceAtLeast(1) * 1000L
        while (true) {
            delay(delayMs)
            emit(Unit)
        }
    }

    // ---- Simulation state --------------------------------------------------

    private class RouteState(val route: Route) {
        private val providers: List<ProviderSim>
        private val candles = ArrayDeque<Candle>()
        private var base: Double

        init {
            base = SEED_PRICES[route.id] ?: 1800.0
            val warm = Random(route.id.hashCode())
            // Backfill history so the chart opens with context, not a flat line.
            val start = System.currentTimeMillis() - HISTORY * 60_000L
            var price = base
            for (i in 0 until HISTORY) {
                val open = price
                val drift = (warm.nextDouble() - 0.48) * base * 0.012
                price = max(base * 0.6, open + drift)
                val hi = max(open, price) + warm.nextDouble() * base * 0.004
                val lo = min(open, price) - warm.nextDouble() * base * 0.004
                candles.addLast(Candle(start + i * 60_000L, open, hi, lo, price))
            }
            base = price
            providers = providerCatalog(route).map { (p, _) -> ProviderSim(p, price) }
            providers.forEach { it.reset(price, warm) }
        }

        fun advance(random: Random, now: Long) {
            val prev = candles.lastOrNull()?.close ?: base
            val drift = (random.nextDouble() - 0.49) * prev * 0.02
            val close = max(prev * 0.55, prev + drift)
            val hi = max(prev, close) + random.nextDouble() * prev * 0.006
            val lo = min(prev, close) - random.nextDouble() * prev * 0.006
            candles.addLast(Candle(now, prev, hi, lo, close))
            while (candles.size > HISTORY) candles.removeFirst()
            providers.forEach { it.advance(random, close) }
            base = close
        }

        fun toMarket(): RouteMarket {
            val window = candles.takeLast(WINDOW)
            val open = window.firstOrNull()?.open ?: base
            val price = candles.lastOrNull()?.close ?: base
            val high = window.maxOfOrNull { it.high } ?: price
            val low = window.minOfOrNull { it.low } ?: price
            val changePct = if (open != 0.0) (price - open) / open * 100.0 else 0.0
            return RouteMarket(
                route = route,
                price = price,
                openPrice = open,
                high = high,
                low = low,
                changePct = changePct,
                spark = window.map { it.close },
                lastUpdated = candles.lastOrNull()?.time ?: System.currentTimeMillis(),
            )
        }

        fun toDetail(): RouteDetail {
            val candleList = candles.toList()
            return RouteDetail(
                market = toMarket(),
                candles = candleList,
                line = candleList.map { PricePoint(it.time, it.close) },
                quotes = providers.map { it.toQuote() }.sortedBy { it.price },
            )
        }
    }

    private class ProviderSim(val provider: Provider, initial: Double) {
        private var price = initial
        private val spark = ArrayDeque<Double>()

        fun reset(routeClose: Double, random: Random) {
            price = routeClose * (0.94 + random.nextDouble() * 0.16)
            spark.clear()
            repeat(WINDOW) { spark.addLast(price) }
        }

        fun advance(random: Random, routeClose: Double) {
            // Each provider tracks the route with its own spread and jitter.
            val target = routeClose * (0.92 + provider.id.length % 5 * 0.02)
            val pull = (target - price) * 0.3
            val jitter = (random.nextDouble() - 0.5) * routeClose * 0.01
            price = max(routeClose * 0.5, price + pull + jitter)
            spark.addLast(price)
            while (spark.size > WINDOW) spark.removeFirst()
        }

        fun toQuote(): ProviderQuote {
            val ref = spark.firstOrNull() ?: price
            val changePct = if (ref != 0.0) (price - ref) / ref * 100.0 else 0.0
            return ProviderQuote(provider, price, changePct, spark.toList())
        }
    }

    companion object {
        private const val HISTORY = 60
        private const val WINDOW = 40

        private val DOMESTIC = setOf("GRU", "GIG", "BSB", "REC")

        private val SEED_PRICES = mapOf(
            "GRU-JFK" to 4120.0,
            "GRU-LIS" to 3380.0,
            "GRU-MIA" to 2960.0,
            "GIG-SCL" to 1740.0,
            "BSB-GRU" to 620.0,
            "GRU-CDG" to 4890.0,
            "REC-GRU" to 780.0,
            "GRU-EZE" to 1290.0,
        )

        private fun seedRoutes(): List<Route> = listOf(
            Route("GRU-JFK", "GRU", "JFK", "São Paulo", "Nova York"),
            Route("GRU-LIS", "GRU", "LIS", "São Paulo", "Lisboa"),
            Route("GRU-MIA", "GRU", "MIA", "São Paulo", "Miami"),
            Route("GIG-SCL", "GIG", "SCL", "Rio de Janeiro", "Santiago"),
            Route("BSB-GRU", "BSB", "GRU", "Brasília", "São Paulo"),
            Route("GRU-CDG", "GRU", "CDG", "São Paulo", "Paris"),
            Route("REC-GRU", "REC", "GRU", "Recife", "São Paulo"),
            Route("GRU-EZE", "GRU", "EZE", "São Paulo", "Buenos Aires"),
        )

        private fun providerCatalog(route: Route): List<Pair<Provider, Double>> = listOf(
            Provider("latam", "LATAM", ProviderKind.AIRLINE) to 1.00,
            Provider("gol", "GOL", ProviderKind.AIRLINE) to 0.97,
            Provider("azul", "Azul", ProviderKind.AIRLINE) to 1.03,
            Provider("tap", "TAP", ProviderKind.AIRLINE) to 1.06,
            Provider("decolar", "Decolar", ProviderKind.PLATFORM) to 0.95,
            Provider("kayak", "Kayak", ProviderKind.PLATFORM) to 0.96,
            Provider("googleflights", "Google Flights", ProviderKind.PLATFORM) to 0.94,
            Provider("maxmilhas", "MaxMilhas", ProviderKind.PLATFORM) to 0.92,
        ).filter { (p, _) ->
            // Domestic legs drop the long-haul-only carrier.
            val domestic = route.origin in DOMESTIC && route.destination in DOMESTIC
            if (domestic) p.id != "tap" else true
        }
    }
}
