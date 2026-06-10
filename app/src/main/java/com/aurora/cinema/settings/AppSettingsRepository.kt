package com.aurora.cinema.settings

import kotlinx.coroutines.flow.Flow
import com.aurora.cinema.render.CinemaScreenConfig
import com.aurora.cinema.render.HeadsetProfile
import com.aurora.cinema.render.StereoConfig

interface AppSettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setFirstRunAcknowledged(acknowledged: Boolean)

    suspend fun setComfortModeEnabled(enabled: Boolean)

    suspend fun setDefaultScreenDistanceMeters(distanceMeters: Float)

    suspend fun setCinemaScreenConfig(config: CinemaScreenConfig)

    suspend fun setStereoConfig(config: StereoConfig)

    suspend fun setHeadsetProfile(profile: HeadsetProfile)
}
