package com.aurora.cinema.library

import com.aurora.cinema.media.MediaProbeResult

data class VideoItem(
    val id: Long,
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
    val accessState: VideoAccessState,
    val probeResult: MediaProbeResult,
    val playbackPositionMs: Long,
    val playbackCompleted: Boolean,
    val playbackUpdatedAt: Long,
)

enum class VideoAccessState {
    Available,
    Missing,
    Unknown,
}
