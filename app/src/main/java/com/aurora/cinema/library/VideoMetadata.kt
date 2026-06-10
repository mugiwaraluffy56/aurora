package com.aurora.cinema.library

data class VideoMetadata(
    val uri: String,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val sourceType: String,
    val persistedPermission: Boolean,
    val accessState: VideoAccessState,
)
