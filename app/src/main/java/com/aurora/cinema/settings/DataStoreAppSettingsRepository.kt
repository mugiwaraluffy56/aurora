package com.aurora.cinema.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aurora.cinema.render.CinemaScreenConfig
import com.aurora.cinema.render.ScreenAspectRatio
import com.aurora.cinema.render.ScreenCropMode
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
                cinemaScreenConfig = CinemaScreenConfig(
                    aspectRatioMode = preferences[Keys.screenAspectRatio]
                        .enumValueOrDefault(ScreenAspectRatio.Source),
                    distanceMeters = preferences[Keys.screenDistance] ?: 8f,
                    widthMeters = preferences[Keys.screenWidth] ?: 18f,
                    verticalOffsetMeters = preferences[Keys.screenVerticalOffset] ?: 0f,
                    curvatureRadiusMeters = preferences[Keys.screenCurvatureRadius] ?: 0f,
                    tiltDegrees = preferences[Keys.screenTilt] ?: 0f,
                    brightness = preferences[Keys.screenBrightness] ?: 1f,
                    contrast = preferences[Keys.screenContrast] ?: 1f,
                    cropMode = preferences[Keys.screenCropMode]
                        .enumValueOrDefault(ScreenCropMode.Fit),
                ),
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

    override suspend fun setCinemaScreenConfig(config: CinemaScreenConfig) {
        dataStore.edit { preferences ->
            preferences[Keys.screenAspectRatio] = config.aspectRatioMode.name
            preferences[Keys.screenDistance] = config.distanceMeters.coerceIn(3f, 20f)
            preferences[Keys.screenWidth] = config.widthMeters.coerceIn(6f, 30f)
            preferences[Keys.screenVerticalOffset] = config.verticalOffsetMeters.coerceIn(-4f, 4f)
            preferences[Keys.screenCurvatureRadius] = config.curvatureRadiusMeters.coerceIn(0f, 40f)
            preferences[Keys.screenTilt] = config.tiltDegrees.coerceIn(-15f, 15f)
            preferences[Keys.screenBrightness] = config.brightness.coerceIn(0.5f, 1.5f)
            preferences[Keys.screenContrast] = config.contrast.coerceIn(0.5f, 1.5f)
            preferences[Keys.screenCropMode] = config.cropMode.name
        }
    }

    private object Keys {
        val firstRunAcknowledged = booleanPreferencesKey("first_run_acknowledged")
        val comfortModeEnabled = booleanPreferencesKey("comfort_mode_enabled")
        val defaultScreenDistanceMeters = floatPreferencesKey("default_screen_distance_meters")
        val screenAspectRatio = stringPreferencesKey("screen_aspect_ratio")
        val screenDistance = floatPreferencesKey("screen_distance")
        val screenWidth = floatPreferencesKey("screen_width")
        val screenVerticalOffset = floatPreferencesKey("screen_vertical_offset")
        val screenCurvatureRadius = floatPreferencesKey("screen_curvature_radius")
        val screenTilt = floatPreferencesKey("screen_tilt")
        val screenBrightness = floatPreferencesKey("screen_brightness")
        val screenContrast = floatPreferencesKey("screen_contrast")
        val screenCropMode = stringPreferencesKey("screen_crop_mode")
    }
}

private inline fun <reified T : Enum<T>> String?.enumValueOrDefault(default: T): T {
    return enumValues<T>().firstOrNull { it.name == this } ?: default
}
