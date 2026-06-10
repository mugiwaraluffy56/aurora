package com.aurora.cinema.app

import android.content.Context
import androidx.room.Room
import com.aurora.cinema.library.AndroidVideoMetadataReader
import com.aurora.cinema.library.OfflineLibraryRepository
import com.aurora.cinema.library.RoomOfflineLibraryRepository
import com.aurora.cinema.library.db.AuroraDatabase
import com.aurora.cinema.settings.AppSettingsRepository
import com.aurora.cinema.settings.DataStoreAppSettingsRepository

interface AppContainer {
    val applicationContext: Context
    val settingsRepository: AppSettingsRepository
    val libraryRepository: OfflineLibraryRepository
}

class DefaultAppContainer(
    override val applicationContext: Context,
) : AppContainer {
    private val database: AuroraDatabase = Room.databaseBuilder(
        applicationContext,
        AuroraDatabase::class.java,
        "aurora.db",
    ).build()

    override val settingsRepository: AppSettingsRepository =
        DataStoreAppSettingsRepository(applicationContext)

    override val libraryRepository: OfflineLibraryRepository =
        RoomOfflineLibraryRepository(
            context = applicationContext,
            videoDao = database.videoDao(),
            metadataReader = AndroidVideoMetadataReader(applicationContext.contentResolver),
        )
}
