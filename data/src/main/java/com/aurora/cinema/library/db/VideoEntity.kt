package com.aurora.cinema.library.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "videos",
    indices = [Index(value = ["uri"], unique = true)],
)
data class VideoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val uri: String,
    val displayName: String,
    val displayTitleOverride: String,
    val thumbnailPath: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val dateAdded: Long,
    val lastSeenAt: Long,
    val sourceType: String,
    val persistedPermission: Boolean,
    val lastAccessCheckAt: Long,
    val accessState: String,
    val probeContainerMimeType: String,
    val probeVideoMimeType: String,
    val codecFamily: String,
    val profileLevel: String,
    val frameRate: Float,
    val bitrate: Long,
    val bitDepth: Int,
    val hdrFormat: String,
    val decoderName: String,
    val codecSupportStatus: String,
    val codecWarnings: String,
)
