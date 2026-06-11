package com.aurora.cinema

import android.app.Application
import com.aurora.cinema.app.AppContainer
import com.aurora.cinema.app.DefaultAppContainer
import com.aurora.cinema.playback.MediaSessionController
import com.aurora.cinema.playback.MediaSessionOwner

class AuroraApplication : Application(), MediaSessionOwner {
    lateinit var appContainer: AppContainer
        private set

    override val mediaSessionController: MediaSessionController
        get() = appContainer.mediaSessionController

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        appContainer = DefaultAppContainer(this)
    }
}
