package com.aurora.cinema.playback

data class SubtitleSettings(
    val enabled: Boolean = true,
    val sizeScale: Float = 1f,
    val verticalOffset: Float = 0f,
    val depthMeters: Float = 6f,
    val audioDelayMs: Long = 0L,
) {
    fun clamped(): SubtitleSettings = copy(
        sizeScale = sizeScale.coerceIn(0.6f, 1.8f),
        verticalOffset = verticalOffset.coerceIn(-0.35f, 0.35f),
        depthMeters = depthMeters.coerceIn(3f, 12f),
        audioDelayMs = audioDelayMs.coerceIn(-1_000L, 1_000L),
    )
}
