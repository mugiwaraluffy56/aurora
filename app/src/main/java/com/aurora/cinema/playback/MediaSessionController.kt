package com.aurora.cinema.playback

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession

class MediaSessionController(
    context: Context,
    player: ExoPlayer,
) {
    val mediaSession: MediaSession = MediaSession.Builder(context, player).build()

    fun release() {
        mediaSession.release()
    }
}
