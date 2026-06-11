package com.aurora.cinema.playback

import kotlin.math.roundToInt

data class VideoDisplaySize(
    val width: Int,
    val height: Int,
)

object VideoDisplaySizeCalculator {
    fun calculate(
        width: Int,
        height: Int,
        pixelWidthHeightRatio: Float,
    ): VideoDisplaySize {
        if (width <= 0 || height <= 0) return VideoDisplaySize(0, 0)
        val ratio = pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
        val adjustedWidth = (width * ratio).roundToInt().coerceAtLeast(1)
        return VideoDisplaySize(width = adjustedWidth, height = height)
    }
}
