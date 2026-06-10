package com.aurora.cinema

import android.app.Application
import com.aurora.cinema.app.AppContainer
import com.aurora.cinema.app.DefaultAppContainer

class AuroraApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
    }
}
