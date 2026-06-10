package com.aurora.cinema.settings

import kotlinx.coroutines.flow.Flow

interface AppSettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setFirstRunAcknowledged(acknowledged: Boolean)

    suspend fun setComfortModeEnabled(enabled: Boolean)

    suspend fun setDefaultScreenDistanceMeters(distanceMeters: Float)
}
