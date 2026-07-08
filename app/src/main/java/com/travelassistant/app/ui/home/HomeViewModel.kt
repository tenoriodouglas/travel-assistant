package com.travelassistant.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.travelassistant.app.TravelApp
import com.travelassistant.app.data.Airports
import com.travelassistant.app.data.MarketRepository
import com.travelassistant.app.data.SettingsRepository
import com.travelassistant.app.data.model.Airport
import com.travelassistant.app.data.model.RouteBoard
import com.travelassistant.app.data.model.TimeRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    private val repo: MarketRepository,
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

    /** Live board for the current query, recomputed on every feed tick. Null until a valid pair. */
    val board: StateFlow<RouteBoard?> =
        combine(_origin, _destination, _range, repo.lastTick) { origin, destination, range, _ ->
            if (origin != null && destination != null && origin.code != destination.code) {
                repo.board(origin, destination, range)
            } else {
                null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    fun refresh() = repo.refreshNow()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TravelApp
                HomeViewModel(app.container.marketRepository, app.container.settingsRepository)
            }
        }
    }
}
