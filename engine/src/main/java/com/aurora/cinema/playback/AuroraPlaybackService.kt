package com.aurora.cinema.playback

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class AuroraPlaybackService : MediaSessionService() {
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return (application as MediaSessionOwner).mediaSessionController.mediaSession
    }
}
