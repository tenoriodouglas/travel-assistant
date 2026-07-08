package com.travelassistant.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.travelassistant.app.TravelApp
import com.travelassistant.app.data.Airports
import com.travelassistant.app.data.PriceRepository
import com.travelassistant.app.data.model.Airport
import com.travelassistant.app.data.model.RouteBoard
import com.travelassistant.app.data.model.TimeRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

sealed interface BoardUiState {
    /** No valid origin/destination selected yet. */
    data object Idle : BoardUiState

    /** No real-data provider configured — the app only shows real prices. */
    data object NeedsSetup : BoardUiState

    /** Fetching from the provider. */
    data object Loading : BoardUiState

    data class Ready(val board: RouteBoard) : BoardUiState

    /** The provider failed or doesn't cover this route. Never falls back to fake data. */
    data class Error(val message: String) : BoardUiState
}

private data class Query(val origin: Airport, val destination: Airport, val range: TimeRange)

/**
 * Real-data-only home. A real provider (Travelpayouts/Amadeus) is required; without one the
 * screen asks for setup instead of ever showing simulated prices. Failures surface as errors.
 */
class HomeViewModel(
    private val primary: PriceRepository?,
    val realDataEnabled: Boolean,
) : ViewModel() {

    private val _origin = MutableStateFlow(Airports.byCode("GRU"))
    private val _destination = MutableStateFlow(Airports.byCode("LIS"))
    private val _range = MutableStateFlow(TimeRange.M1)

    val origin: StateFlow<Airport?> = _origin
    val destination: StateFlow<Airport?> = _destination
    val range: StateFlow<TimeRange> = _range

    private val queryFlow = combine(_origin, _destination, _range) { origin, destination, range ->
        if (origin != null && destination != null && origin.code != destination.code) {
            Query(origin, destination, range)
        } else {
            null
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val boardState: StateFlow<BoardUiState> =
        if (primary == null) {
            MutableStateFlow<BoardUiState>(BoardUiState.NeedsSetup)
        } else {
            combine(queryFlow, primary.lastTick) { query, _ -> query }
                .flatMapLatest { query -> boardFlow(primary, query) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BoardUiState.Loading)
        }

    private fun boardFlow(repo: PriceRepository, query: Query?) = flow {
        if (query == null) {
            emit(BoardUiState.Idle)
            return@flow
        }
        emit(BoardUiState.Loading)
        try {
            emit(BoardUiState.Ready(repo.board(query.origin, query.destination, query.range)))
        } catch (e: Exception) {
            emit(BoardUiState.Error(e.message ?: "Não foi possível buscar os preços."))
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

    fun refresh() { primary?.refreshNow() }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TravelApp
                val c = app.container
                HomeViewModel(primary = c.priceRepository, realDataEnabled = c.realDataEnabled)
            }
        }
    }
}
