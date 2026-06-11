package com.aurora.cinema.app

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aurora.cinema.library.AndroidVideoMetadataReader
import com.aurora.cinema.library.OfflineLibraryRepository
import com.aurora.cinema.library.RoomOfflineLibraryRepository
import com.aurora.cinema.library.db.AuroraDatabase
import com.aurora.cinema.media.CodecCapabilityService
import com.aurora.cinema.media.DeviceDisplayInfo
import com.aurora.cinema.media.MediaProbe
import com.aurora.cinema.playback.MediaSessionController
import com.aurora.cinema.playback.PlaybackEngine
import com.aurora.cinema.playback.PlaybackProgressStore
import com.aurora.cinema.settings.AppSettingsRepository
import com.aurora.cinema.settings.DataStoreAppSettingsRepository
import com.aurora.cinema.playback.PlayerController

class DefaultAppContainer(
    override val applicationContext: Context,
) : AppContainer {
    private val database: AuroraDatabase = Room.databaseBuilder(
        applicationContext,
        AuroraDatabase::class.java,
        "aurora.db",
    )
        .addMigrations(MIGRATION_1_2)
        .addMigrations(MIGRATION_2_3)
        .build()

    private val player: ExoPlayer = ExoPlayer.Builder(applicationContext).build().apply {
        playWhenReady = false
    }

    override val settingsRepository: AppSettingsRepository =
        DataStoreAppSettingsRepository(applicationContext)

    override val codecCapabilityService: CodecCapabilityService = CodecCapabilityService()

    override val deviceDisplayInfo: DeviceDisplayInfo = DeviceDisplayInfo(applicationContext)

    override val libraryRepository: OfflineLibraryRepository =
        RoomOfflineLibraryRepository(
            context = applicationContext,
            videoDao = database.videoDao(),
            metadataReader = AndroidVideoMetadataReader(
                contentResolver = applicationContext.contentResolver,
                mediaProbe = MediaProbe(
                    contentResolver = applicationContext.contentResolver,
                    codecCapabilityService = codecCapabilityService,
                ),
            ),
        )

    override val playerController: PlayerController =
        PlaybackEngine(
            context = applicationContext,
            player = player,
            progressStore = PlaybackProgressStore(database.videoDao()),
        )

    override val mediaSessionController: MediaSessionController =
        MediaSessionController(
            context = applicationContext,
            player = player,
        )

    private companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playback_progress` (
                        `videoId` INTEGER NOT NULL,
                        `positionMs` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`videoId`)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `videos` ADD COLUMN `probeContainerMimeType` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `videos` ADD COLUMN `probeVideoMimeType` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `videos` ADD COLUMN `codecFamily` TEXT NOT NULL DEFAULT 'Unknown'")
                db.execSQL("ALTER TABLE `videos` ADD COLUMN `profileLevel` TEXT NOT NULL DEFAULT 'Unknown'")
                db.execSQL("ALTER TABLE `videos` ADD COLUMN `frameRate` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `videos` ADD COLUMN `bitrate` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `videos` ADD COLUMN `bitDepth` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `videos` ADD COLUMN `hdrFormat` TEXT NOT NULL DEFAULT 'Unknown'")
                db.execSQL("ALTER TABLE `videos` ADD COLUMN `decoderName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `videos` ADD COLUMN `codecSupportStatus` TEXT NOT NULL DEFAULT 'Unknown'")
                db.execSQL("ALTER TABLE `videos` ADD COLUMN `codecWarnings` TEXT NOT NULL DEFAULT 'Codec support has not been probed yet.'")
            }
        }
    }
}
