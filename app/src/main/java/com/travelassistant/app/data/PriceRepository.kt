package com.travelassistant.app.data

import com.travelassistant.app.data.model.Airport
import com.travelassistant.app.data.model.RouteBoard
import com.travelassistant.app.data.model.TimeRange
import kotlinx.coroutines.flow.StateFlow

/** Source of price boards — either the simulated live feed or a real flight-price API. */
interface PriceRepository {
    /** True when the source pushes continuous updates (simulation); false for on-demand APIs. */
    val isLive: Boolean

    /** Bumps whenever new data is available; the UI recomputes the board on each change. */
    val lastTick: StateFlow<Long>

    /** Build the board for the query. May hit the network and throw on failure. */
    suspend fun board(origin: Airport, destination: Airport, range: TimeRange): RouteBoard

    /** Request fresh data now (feed tick for simulation, re-fetch for a real API). */
    fun refreshNow()
}
