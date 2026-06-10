package com.aurora.cinema.library

import com.aurora.cinema.media.MediaProbeResult

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
    val probeResult: MediaProbeResult,
)
