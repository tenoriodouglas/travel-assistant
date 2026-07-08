package com.travelassistant.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persists user preferences such as the live-feed refresh interval. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private val refreshKey = intPreferencesKey("refresh_interval_seconds")

    /** How often the live feed advances, in seconds. Defaults to 30s. */
    val refreshIntervalSeconds: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[refreshKey] ?: DEFAULT_INTERVAL).coerceIn(MIN_INTERVAL, MAX_INTERVAL)
    }

    suspend fun setRefreshInterval(seconds: Int) {
        val clamped = seconds.coerceIn(MIN_INTERVAL, MAX_INTERVAL)
        dataStore.edit { it[refreshKey] = clamped }
    }

    companion object {
        const val DEFAULT_INTERVAL = 30
        const val MIN_INTERVAL = 5
        const val MAX_INTERVAL = 300
        val PRESETS = listOf(10, 15, 30, 60, 120)
    }
}
