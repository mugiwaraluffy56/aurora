package com.aurora.cinema.playback

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aurora.cinema.AuroraApplication

class AuroraPlaybackService : MediaSessionService() {
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return (application as AuroraApplication).appContainer.mediaSessionController.mediaSession
    }
}
