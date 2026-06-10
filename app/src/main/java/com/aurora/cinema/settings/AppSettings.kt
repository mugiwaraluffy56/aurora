package com.aurora.cinema.settings

import com.aurora.cinema.render.CinemaScreenConfig

data class AppSettings(
    val firstRunAcknowledged: Boolean = false,
    val comfortModeEnabled: Boolean = true,
    val defaultScreenDistanceMeters: Float = 8.0f,
    val cinemaScreenConfig: CinemaScreenConfig = CinemaScreenConfig(),
)
