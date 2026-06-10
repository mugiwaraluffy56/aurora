package com.aurora.cinema.library.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_progress")
data class PlaybackProgressEntity(
    @PrimaryKey
    val videoId: Long,
    val positionMs: Long,
    val completed: Boolean,
    val updatedAt: Long,
)
