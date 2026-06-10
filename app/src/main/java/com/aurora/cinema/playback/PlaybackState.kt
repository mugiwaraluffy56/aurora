package com.aurora.cinema.playback

import com.aurora.cinema.library.VideoItem

data class PlaybackState(
    val selectedVideo: VideoItem? = null,
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val error: PlaybackError? = null,
    val timedTextTracks: List<TimedTextTrack> = emptyList(),
)
