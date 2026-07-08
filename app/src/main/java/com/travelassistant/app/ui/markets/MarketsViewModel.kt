package com.travelassistant.app.ui.markets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.travelassistant.app.TravelApp
import com.travelassistant.app.data.MarketRepository
import com.travelassistant.app.data.SettingsRepository
import com.travelassistant.app.data.model.RouteMarket
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MarketsViewModel(
    private val repo: MarketRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val markets: StateFlow<List<RouteMarket>> = repo.markets
    val lastTick: StateFlow<Long> = repo.lastTick
    val intervalSeconds: StateFlow<Int> = settings.refreshIntervalSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_INTERVAL)

    fun refresh() = repo.refreshNow()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TravelApp
                MarketsViewModel(app.container.marketRepository, app.container.settingsRepository)
            }
        }
    }
}
