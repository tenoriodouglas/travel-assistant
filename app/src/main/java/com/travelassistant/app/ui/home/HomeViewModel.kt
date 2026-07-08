package com.travelassistant.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.travelassistant.app.TravelApp
import com.travelassistant.app.data.Airports
import com.travelassistant.app.data.PriceRepository
import com.travelassistant.app.data.SettingsRepository
import com.travelassistant.app.data.model.Airport
import com.travelassistant.app.data.model.TimeRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

enum class DataSource { REAL, SIMULATED }

sealed interface BoardUiState {
    /** No valid origin/destination selected yet. */
    data object Idle : BoardUiState

    /** Fetching from the network (real API only). */
    data object Loading : BoardUiState

    data class Ready(
        val board: RouteBoard,
        val live: Boolean,
        val source: DataSource,
        val notice: String? = null,
    ) : BoardUiState
}

private data class Query(val origin: Airport, val destination: Airport, val range: TimeRange)

class HomeViewModel(
    private val primary: PriceRepository,
    private val simulated: PriceRepository,
    val realDataEnabled: Boolean,
    settings: SettingsRepository,
) : ViewModel() {

    private val _origin = MutableStateFlow(Airports.byCode("GRU"))
    private val _destination = MutableStateFlow(Airports.byCode("LIS"))
    private val _range = MutableStateFlow(TimeRange.M1)

    val origin: StateFlow<Airport?> = _origin
    val destination: StateFlow<Airport?> = _destination
    val range: StateFlow<TimeRange> = _range

    val intervalSeconds: StateFlow<Int> = settings.refreshIntervalSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_INTERVAL)

    private val queryFlow = combine(_origin, _destination, _range) { origin, destination, range ->
        if (origin != null && destination != null && origin.code != destination.code) {
            Query(origin, destination, range)
        } else {
            null
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val boardState: StateFlow<BoardUiState> =
        combine(queryFlow, primary.lastTick) { query, _ -> query }
            .flatMapLatest { query -> boardFlow(query) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BoardUiState.Idle)

    private fun boardFlow(query: Query?) = flow {
        if (query == null) {
            emit(BoardUiState.Idle)
            return@flow
        }
        if (primary.isLive) {
            // Simulation is instant and continuously live.
            val board = primary.board(query.origin, query.destination, query.range)
            emit(BoardUiState.Ready(board, live = true, source = DataSource.SIMULATED))
            return@flow
        }
        emit(BoardUiState.Loading)
        try {
            val board = primary.board(query.origin, query.destination, query.range)
            emit(BoardUiState.Ready(board, live = false, source = DataSource.REAL))
        } catch (e: Exception) {
            // Real API failed — fall back to the simulation so the screen still works.
            val fallback = simulated.board(query.origin, query.destination, query.range)
            emit(
                BoardUiState.Ready(
                    board = fallback,
                    live = false,
                    source = DataSource.SIMULATED,
                    notice = "API indisponível — mostrando simulação. (${e.message})",
                ),
            )
        }
    }

    fun setOrigin(airport: Airport) { _origin.value = airport }
    fun setDestination(airport: Airport) { _destination.value = airport }
    fun setRange(range: TimeRange) { _range.value = range }

    fun swap() {
        val o = _origin.value
        _origin.value = _destination.value
        _destination.value = o
    }

    fun pickPopular(originCode: String, destinationCode: String) {
        Airports.byCode(originCode)?.let { _origin.value = it }
        Airports.byCode(destinationCode)?.let { _destination.value = it }
    }

    fun refresh() = primary.refreshNow()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TravelApp
                val c = app.container
                HomeViewModel(
                    primary = c.priceRepository,
                    simulated = c.simulatedRepository,
                    realDataEnabled = c.realDataEnabled,
                    settings = c.settingsRepository,
                )
            }
        }
    }
}
