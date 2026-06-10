package com.aurora.cinema.settings

import com.aurora.cinema.render.CinemaScreenConfig
import com.aurora.cinema.render.HeadsetProfile
import com.aurora.cinema.render.StereoConfig

data class AppSettings(
    val firstRunAcknowledged: Boolean = false,
    val comfortModeEnabled: Boolean = true,
    val defaultScreenDistanceMeters: Float = 8.0f,
    val cinemaScreenConfig: CinemaScreenConfig = CinemaScreenConfig(),
    val stereoConfig: StereoConfig = StereoConfig(),
    val headsetProfile: HeadsetProfile = HeadsetProfile(),
)
