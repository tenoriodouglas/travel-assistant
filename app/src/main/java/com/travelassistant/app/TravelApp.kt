package com.travelassistant.app

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.travelassistant.app.data.AmadeusPriceRepository
import com.travelassistant.app.data.MarketRepository
import com.travelassistant.app.data.PriceRepository
import com.travelassistant.app.data.SettingsRepository
import com.travelassistant.app.data.remote.AmadeusService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Lightweight manual DI container — no framework needed for an app this size. */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository = SettingsRepository(context.dataStore)

    /** Always available; also the fallback when a real API call fails. */
    val simulatedRepository = MarketRepository(settingsRepository, appScope).apply { start() }

    /** True when Amadeus credentials were provided at build time. */
    val realDataEnabled: Boolean =
        BuildConfig.AMADEUS_CLIENT_ID.isNotBlank() && BuildConfig.AMADEUS_CLIENT_SECRET.isNotBlank()

    /** Primary source: real Amadeus data if configured, otherwise the simulation. */
    val priceRepository: PriceRepository =
        if (realDataEnabled) {
            AmadeusPriceRepository(
                AmadeusService(
                    clientId = BuildConfig.AMADEUS_CLIENT_ID,
                    clientSecret = BuildConfig.AMADEUS_CLIENT_SECRET,
                    environment = BuildConfig.AMADEUS_ENV,
                ),
            )
        } else {
            simulatedRepository
        }
}

class TravelApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
