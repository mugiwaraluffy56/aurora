package com.aurora.cinema.playback

import com.aurora.cinema.library.VideoItem

data class PlaybackState(
    val selectedVideo: VideoItem? = null,
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val error: PlaybackError? = null,
    val timedTextTracks: List<TimedTextTrack> = emptyList(),
)
