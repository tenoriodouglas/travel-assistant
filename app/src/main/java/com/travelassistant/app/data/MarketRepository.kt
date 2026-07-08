package com.travelassistant.app.data

import com.travelassistant.app.data.model.Airport
import com.travelassistant.app.data.model.Candle
import com.travelassistant.app.data.model.Provider
import com.travelassistant.app.data.model.ProviderKind
import com.travelassistant.app.data.model.ProviderQuote
import com.travelassistant.app.data.model.Route
import com.travelassistant.app.data.model.RouteBoard
import com.travelassistant.app.data.model.TimeRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * In-memory simulated market for arbitrary origin -> destination pairs. For each pair the
 * user opens, it lazily builds a per-future-day price curve (seasonal baseline + bounded
 * random walk) and advances it on every [tick]; the cadence is the user-configurable feed
 * interval. [board] then buckets those future days into OHLC candles for the selected range.
 *
 * The random walk is a stand-in for a real pricing API (Amadeus / Google Flights via SerpApi
 * / Travelpayouts). Wiring one in only means replacing how [PairState.daily] is populated.
 */
class MarketRepository(
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : PriceRepository {
    override val isLive: Boolean = true

    private val random = Random(1_984)
    private val pairs = LinkedHashMap<String, PairState>()

    private val _lastTick = MutableStateFlow(clock())
    /** Emits the timestamp of the latest feed update; the UI observes it to recompute boards. */
    override val lastTick: StateFlow<Long> = _lastTick.asStateFlow()

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

    /** Force an immediate feed advance (refresh button). */
    override fun refreshNow() {
        scope.launch { tick() }
    }

    override suspend fun board(origin: Airport, destination: Airport, range: TimeRange): RouteBoard =
        snapshot(origin, destination, range)

    /** Snapshot board for the given query; re-read on every [lastTick] to stay live. */
    @Synchronized
    private fun snapshot(origin: Airport, destination: Airport, range: TimeRange): RouteBoard {
        val key = "${origin.code}-${destination.code}"
        val state = pairs.getOrPut(key) { PairState(Route(origin, destination), random) }
        return state.board(range, clock())
    }

    @Synchronized
    private fun tick() {
        pairs.values.forEach { it.advance(random) }
        _lastTick.value = clock()
    }

    private fun tickingFlow(intervalSeconds: Int) = flow {
        val delayMs = intervalSeconds.coerceAtLeast(1) * 1000L
        while (true) {
            delay(delayMs)
            emit(Unit)
        }
    }

    // ---- Simulation state --------------------------------------------------

    private class PairState(val route: Route, seedRandom: Random) {
        private val base: Double = basePrice(route)
        private val baseline = DoubleArray(DAYS)
        private val daily = DoubleArray(DAYS)
        private val providers: List<ProviderSim>
        private val openByRange = HashMap<TimeRange, Double>()

        init {
            val seed = Random(route.id.hashCode())
            val weekPhase = seed.nextDouble() * 2 * PI
            val seasonPhase = seed.nextDouble() * 2 * PI
            for (day in 0 until DAYS) {
                baseline[day] = seasonalBaseline(day, weekPhase, seasonPhase)
                daily[day] = baseline[day] * (0.98 + seed.nextDouble() * 0.04)
            }
            providers = providerCatalog(route).map { p ->
                ProviderSim(p, 0.90 + seed.nextDouble() * 0.18)
            }
        }

        private fun seasonalBaseline(day: Int, weekPhase: Double, seasonPhase: Double): Double {
            val nearTermPremium = if (day < 14) 0.18 * (1 - day / 14.0) else 0.0
            val weekly = 0.04 * sin(day * 2 * PI / 7 + weekPhase)
            val seasonal = 0.10 * sin(day * 2 * PI / 60 + seasonPhase)
            val farOut = 0.05 * (day.toDouble() / DAYS)
            val factor = 1.0 + nearTermPremium + weekly + seasonal + farOut
            return max(base * 0.5, base * factor)
        }

        fun advance(random: Random) {
            for (day in 0 until DAYS) {
                val reversion = (baseline[day] - daily[day]) * 0.15
                val jitter = (random.nextDouble() - 0.5) * base * 0.012
                daily[day] = max(base * 0.4, daily[day] + reversion + jitter)
            }
            providers.forEach { it.advance(random) }
        }

        fun board(range: TimeRange, now: Long): RouteBoard {
            val days = min(range.days, DAYS - 1)
            val today = LocalDate.now()
            val (candles, xLabels) = buildCandles(range, days, now, today)

            var cheapest = Double.MAX_VALUE
            var cheapestDay = 0
            var highest = Double.MIN_VALUE
            for (day in 0 until days) {
                val p = daily[day]
                if (p < cheapest) { cheapest = p; cheapestDay = day }
                if (p > highest) highest = p
            }
            val open = openByRange.getOrPut(range) { cheapest }
            val changePct = if (open != 0.0) (cheapest - open) / open * 100.0 else 0.0

            return RouteBoard(
                route = route,
                range = range,
                candles = candles,
                xLabels = xLabels,
                price = cheapest,
                openPrice = open,
                high = highest,
                low = cheapest,
                changePct = changePct,
                cheapestDateLabel = today.plusDays(cheapestDay.toLong()).format(DAY_MONTH),
                quotes = providers.map { it.quote(daily, days) }.sortedBy { it.price },
                lastUpdated = now,
            )
        }

        private fun buildCandles(
            range: TimeRange,
            days: Int,
            now: Long,
            today: LocalDate,
        ): Pair<List<Candle>, List<String>> {
            val size = ceil(days.toDouble() / range.buckets).toInt().coerceAtLeast(1)
            val candles = ArrayList<Candle>()
            val labels = ArrayList<String>()
            var prevClose: Double? = null
            var start = 0
            while (start < days) {
                val end = min(days - 1, start + size - 1)
                var lo = Double.MAX_VALUE
                var hi = Double.MIN_VALUE
                for (day in start..end) {
                    lo = min(lo, daily[day]); hi = max(hi, daily[day])
                }
                val open = prevClose ?: daily[start]
                val close = daily[end]
                var high = max(hi, max(open, close))
                var low = min(lo, min(open, close))
                if (high == low) { high = close * 1.003; low = close * 0.997 }
                candles.add(Candle(now + start.toLong() * DAY_MS, open, high, low, close))
                labels.add(today.plusDays(start.toLong()).format(DAY_MONTH))
                prevClose = close
                start += size
            }
            return candles to labels
        }
    }

    private class ProviderSim(val provider: Provider, initialFactor: Double) {
        private var factor = initialFactor

        fun advance(random: Random) {
            factor = (factor + (random.nextDouble() - 0.5) * 0.01).coerceIn(0.88, 1.12)
        }

        fun quote(daily: DoubleArray, days: Int): ProviderQuote {
            var minPrice = Double.MAX_VALUE
            for (day in 0 until days) minPrice = min(minPrice, daily[day] * factor)
            // Sparkline = provider price across the future window (downsampled).
            val step = max(1, days / 24)
            val spark = ArrayList<Double>()
            var day = 0
            while (day < days) { spark.add(daily[day] * factor); day += step }
            val changePct = if (spark.size >= 2 && spark.first() != 0.0) {
                (spark.last() - spark.first()) / spark.first() * 100.0
            } else 0.0
            return ProviderQuote(provider, minPrice, changePct, spark)
        }
    }

}

// ---- File-private simulation constants & helpers (shared by the nested state) ----

private const val DAYS = 366
private const val DAY_MS = 86_400_000L
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

private val SOUTH_AMERICA = setOf("BR", "AR", "CL", "UY", "PY", "PE", "CO")
private val NORTH_AMERICA = setOf("US", "CA", "MX", "PA")
private val EUROPE = setOf("PT", "ES", "FR", "GB", "IT", "DE", "NL", "CH", "IE")

private fun basePrice(route: Route): Double {
    val a = route.origin.countryCode
    val b = route.destination.countryCode
    val seed = Random(route.id.hashCode())
    fun inRange(lo: Double, hi: Double) = lo + seed.nextDouble() * (hi - lo)
    return when {
        a == b -> inRange(350.0, 1300.0)                                    // doméstico
        a in SOUTH_AMERICA && b in SOUTH_AMERICA -> inRange(900.0, 2200.0)   // regional
        a in EUROPE || b in EUROPE -> inRange(3200.0, 5500.0)               // intercontinental EU
        a in NORTH_AMERICA || b in NORTH_AMERICA -> inRange(2400.0, 4600.0) // Américas longo
        else -> inRange(1500.0, 4200.0)
    }
}

private fun providerCatalog(route: Route): List<Provider> {
    val domestic = route.origin.countryCode == route.destination.countryCode &&
        route.origin.countryCode == "BR"
    val all = listOf(
        Provider("latam", "LATAM", ProviderKind.AIRLINE),
        Provider("gol", "GOL", ProviderKind.AIRLINE),
        Provider("azul", "Azul", ProviderKind.AIRLINE),
        Provider("tap", "TAP", ProviderKind.AIRLINE),
        Provider("decolar", "Decolar", ProviderKind.PLATFORM),
        Provider("kayak", "Kayak", ProviderKind.PLATFORM),
        Provider("googleflights", "Google Flights", ProviderKind.PLATFORM),
        Provider("maxmilhas", "MaxMilhas", ProviderKind.PLATFORM),
    )
    // Domestic BR legs drop the long-haul-only carrier.
    return if (domestic) all.filter { it.id != "tap" } else all
}
