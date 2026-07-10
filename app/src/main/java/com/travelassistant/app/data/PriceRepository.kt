package com.travelassistant.app.data

import com.travelassistant.app.data.model.Airport
import com.travelassistant.app.data.model.Granularity
import com.travelassistant.app.data.model.RouteBoard
import com.travelassistant.app.data.model.TimeRange
import kotlinx.coroutines.flow.StateFlow

/** Source of price boards from a real flight-price API. */
interface PriceRepository {
    /** Bumps whenever fresh data is requested; the UI recomputes the board on each change. */
    val lastTick: StateFlow<Long>

    /** Build the board for the query. Hits the network and throws on failure. */
    suspend fun board(
        origin: Airport,
        destination: Airport,
        range: TimeRange,
        granularity: Granularity,
    ): RouteBoard

    /** Request fresh data now (re-fetch). */
    fun refreshNow()
}
