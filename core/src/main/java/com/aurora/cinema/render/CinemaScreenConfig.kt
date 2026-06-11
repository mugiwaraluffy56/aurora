package com.aurora.cinema.render

data class CinemaScreenConfig(
    // Source = use video's actual aspect ratio (handles 16:9 / 21:9 / IMAX automatically)
    val aspectRatioMode: ScreenAspectRatio = ScreenAspectRatio.Source,
    // JioDive + S24 FE: 20m wide at 5m = ~106° horizontal fill
    // 21:9 → screen is 20×8.6m  (fills horizontal FOV perfectly)
    // 16:9 → screen is 20×11.2m (slightly taller, still immersive)
    // IMAX 1.43:1 → screen is 20×14m (towering, true IMAX feel)
    val distanceMeters: Float = 5f,
    val widthMeters: Float = 20f,
    val verticalOffsetMeters: Float = -0.2f,
    val curvatureRadiusMeters: Float = 16f,
    val tiltDegrees: Float = 0f,
    val brightness: Float = 1f,
    val contrast: Float = 1.05f,
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
