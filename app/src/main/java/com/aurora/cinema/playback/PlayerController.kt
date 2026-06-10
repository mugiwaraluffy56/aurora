package com.aurora.cinema.playback

import com.aurora.cinema.library.VideoItem
import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

interface PlayerController {
    val state: StateFlow<PlaybackState>
    val mediaPlayer: Player?

    fun select(video: VideoItem)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun stop()

    fun saveProgress()
}
