package com.aurora.cinema.library.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        VideoEntity::class,
        PlaybackProgressEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AuroraDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
}
