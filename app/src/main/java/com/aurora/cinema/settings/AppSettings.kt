package com.aurora.cinema.settings

data class AppSettings(
    val firstRunAcknowledged: Boolean = false,
    val comfortModeEnabled: Boolean = true,
    val defaultScreenDistanceMeters: Float = 8.0f,
)
