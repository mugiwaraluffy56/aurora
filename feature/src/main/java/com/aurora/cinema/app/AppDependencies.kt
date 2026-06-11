package com.aurora.cinema.app

import android.content.Context
import com.aurora.cinema.library.OfflineLibraryRepository
import com.aurora.cinema.media.CodecCapabilityService
import com.aurora.cinema.media.DeviceDisplayInfo
import com.aurora.cinema.playback.MediaSessionController
import com.aurora.cinema.playback.PlayerController
import com.aurora.cinema.settings.AppSettingsRepository

interface AppContainer {
    val applicationContext: Context
    val settingsRepository: AppSettingsRepository
    val libraryRepository: OfflineLibraryRepository
    val playerController: PlayerController
    val mediaSessionController: MediaSessionController
    val codecCapabilityService: CodecCapabilityService
    val deviceDisplayInfo: DeviceDisplayInfo
}
