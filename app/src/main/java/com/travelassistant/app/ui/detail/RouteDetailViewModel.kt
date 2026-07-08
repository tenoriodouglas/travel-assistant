package com.travelassistant.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.travelassistant.app.TravelApp
import com.travelassistant.app.data.MarketRepository
import com.travelassistant.app.data.SettingsRepository
import com.travelassistant.app.data.model.RouteDetail
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

const val ROUTE_ID_ARG = "routeId"

class RouteDetailViewModel(
    private val repo: MarketRepository,
    settings: SettingsRepository,
    private val routeId: String,
) : ViewModel() {

    val detail: StateFlow<RouteDetail?> = repo.lastTick
        .map { repo.detailSnapshot(routeId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = repo.detailSnapshot(routeId),
        )

    val intervalSeconds: StateFlow<Int> = settings.refreshIntervalSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_INTERVAL)

    fun refresh() = repo.refreshNow()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TravelApp
                val handle: SavedStateHandle = createSavedStateHandle()
                val routeId: String = handle[ROUTE_ID_ARG] ?: ""
                RouteDetailViewModel(
                    app.container.marketRepository,
                    app.container.settingsRepository,
                    routeId,
                )
            }
        }
    }
}
