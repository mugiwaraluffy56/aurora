package com.aurora.cinema.settings

import com.aurora.cinema.playback.SubtitleSettings
import com.aurora.cinema.input.HardwareInputSettings
import com.aurora.cinema.render.CinemaScreenConfig
import com.aurora.cinema.render.HeadsetProfile
import com.aurora.cinema.render.StereoConfig
import com.aurora.cinema.render.VideoProjection

data class AppSettings(
    val firstRunAcknowledged: Boolean = false,
    val comfortModeEnabled: Boolean = true,
    val defaultScreenDistanceMeters: Float = 8.0f,
    val cinemaScreenConfig: CinemaScreenConfig = CinemaScreenConfig(),
    val stereoConfig: StereoConfig = StereoConfig(),
    val videoProjection: VideoProjection = VideoProjection.Cinema,
    val headsetProfile: HeadsetProfile = HeadsetProfile(),
    val subtitleSettings: SubtitleSettings = SubtitleSettings(),
    val hardwareInputSettings: HardwareInputSettings = HardwareInputSettings(),
)
