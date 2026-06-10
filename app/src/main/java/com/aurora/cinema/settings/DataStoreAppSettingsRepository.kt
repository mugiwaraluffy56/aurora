package com.aurora.cinema.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings",
)

class DataStoreAppSettingsRepository(
    context: Context,
) : AppSettingsRepository {
    private val dataStore = context.appSettingsDataStore

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppSettings(
                firstRunAcknowledged = preferences[Keys.firstRunAcknowledged] ?: false,
                comfortModeEnabled = preferences[Keys.comfortModeEnabled] ?: true,
                defaultScreenDistanceMeters = preferences[Keys.defaultScreenDistanceMeters] ?: 8.0f,
            )
        }

    override suspend fun setFirstRunAcknowledged(acknowledged: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.firstRunAcknowledged] = acknowledged
        }
    }

    override suspend fun setComfortModeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.comfortModeEnabled] = enabled
        }
    }

    override suspend fun setDefaultScreenDistanceMeters(distanceMeters: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.defaultScreenDistanceMeters] = distanceMeters.coerceIn(3.0f, 20.0f)
        }
    }

    private object Keys {
        val firstRunAcknowledged = booleanPreferencesKey("first_run_acknowledged")
        val comfortModeEnabled = booleanPreferencesKey("comfort_mode_enabled")
        val defaultScreenDistanceMeters = floatPreferencesKey("default_screen_distance_meters")
    }
}
