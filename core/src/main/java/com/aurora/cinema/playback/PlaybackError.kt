package com.aurora.cinema.playback

data class PlaybackError(
    val message: String,
    val causeName: String? = null,
)
