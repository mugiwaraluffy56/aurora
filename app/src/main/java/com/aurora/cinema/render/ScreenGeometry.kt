package com.aurora.cinema.render

data class ScreenScale(
    val x: Float,
    val y: Float,
)

object ScreenGeometry {
    fun aspectFit(
        surfaceWidth: Int,
        surfaceHeight: Int,
        videoWidth: Int,
        videoHeight: Int,
    ): ScreenScale {
        if (surfaceWidth <= 0 || surfaceHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            return ScreenScale(1f, 1f)
        }
        val surfaceAspect = surfaceWidth.toFloat() / surfaceHeight
        val videoAspect = videoWidth.toFloat() / videoHeight
        return if (videoAspect > surfaceAspect) {
            ScreenScale(x = 1f, y = surfaceAspect / videoAspect)
        } else {
            ScreenScale(x = videoAspect / surfaceAspect, y = 1f)
        }
    }
}
