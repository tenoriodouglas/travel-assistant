package com.travelassistant.app

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.travelassistant.app.data.AmadeusPriceRepository
import com.travelassistant.app.data.PriceRepository
import com.travelassistant.app.data.SettingsRepository
import com.travelassistant.app.data.TravelpayoutsPriceRepository
import com.travelassistant.app.data.remote.AmadeusService
import com.travelassistant.app.data.remote.TravelpayoutsService

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Lightweight manual DI container — no framework needed for an app this size. */
class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context.dataStore)

    private val hasTravelpayouts = BuildConfig.TRAVELPAYOUTS_TOKEN.isNotBlank()
    private val hasAmadeus =
        BuildConfig.AMADEUS_CLIENT_ID.isNotBlank() && BuildConfig.AMADEUS_CLIENT_SECRET.isNotBlank()

    /** True when a real-data provider was configured at build time. */
    val realDataEnabled: Boolean = hasTravelpayouts || hasAmadeus

    /**
     * Real-data provider — Travelpayouts (free) preferred, then Amadeus. Null when none is
     * configured: the app shows real prices only, never a simulation.
     */
    val priceRepository: PriceRepository? = when {
        hasTravelpayouts ->
            TravelpayoutsPriceRepository(TravelpayoutsService(BuildConfig.TRAVELPAYOUTS_TOKEN))
        hasAmadeus ->
            AmadeusPriceRepository(
                AmadeusService(
                    clientId = BuildConfig.AMADEUS_CLIENT_ID,
                    clientSecret = BuildConfig.AMADEUS_CLIENT_SECRET,
                    environment = BuildConfig.AMADEUS_ENV,
                ),
            )
        else -> null
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
