package com.aurora.cinema.app

import android.content.Context
import com.aurora.cinema.settings.AppSettingsRepository
import com.aurora.cinema.settings.DataStoreAppSettingsRepository

interface AppContainer {
    val applicationContext: Context
    val settingsRepository: AppSettingsRepository
}

class DefaultAppContainer(
    override val applicationContext: Context,
) : AppContainer {
    override val settingsRepository: AppSettingsRepository =
        DataStoreAppSettingsRepository(applicationContext)
}
