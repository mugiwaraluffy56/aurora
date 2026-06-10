package com.aurora.cinema.render

data class CinemaScreenConfig(
    val aspectRatioMode: ScreenAspectRatio = ScreenAspectRatio.Source,
    val distanceMeters: Float = 8f,
    val widthMeters: Float = 18f,
    val verticalOffsetMeters: Float = 0f,
    val curvatureRadiusMeters: Float = 0f,
    val tiltDegrees: Float = 0f,
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val cropMode: ScreenCropMode = ScreenCropMode.Fit,
)

enum class ScreenAspectRatio(val label: String, val ratio: Float?) {
    Source("Source", null),
    Imax143("1.43:1", 1.43f),
    Imax190("1.90:1", 1.90f),
    Widescreen169("16:9", 16f / 9f),
    Cinema239("2.39:1", 2.39f),
}

enum class ScreenCropMode(val label: String) {
    Fit("Fit"),
    Fill("Fill"),
    Crop("Crop"),
}

data class ContentMapping(
    val activeScaleX: Float = 1f,
    val activeScaleY: Float = 1f,
    val sampleScaleX: Float = 1f,
    val sampleScaleY: Float = 1f,
)
