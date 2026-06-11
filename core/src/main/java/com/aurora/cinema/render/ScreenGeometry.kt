package com.aurora.cinema.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

    fun resolveAspectRatio(
        mode: ScreenAspectRatio,
        videoWidth: Int,
        videoHeight: Int,
    ): Float {
        val sourceRatio = if (videoWidth > 0 && videoHeight > 0) {
            videoWidth.toFloat() / videoHeight
        } else {
            DEFAULT_ASPECT_RATIO
        }
        return mode.ratio ?: sourceRatio
    }

    fun contentMapping(
        cropMode: ScreenCropMode,
        screenAspectRatio: Float,
        videoWidth: Int,
        videoHeight: Int,
    ): ContentMapping {
        if (videoWidth <= 0 || videoHeight <= 0 || screenAspectRatio <= 0f) {
            return ContentMapping()
        }
        val sourceAspect = videoWidth.toFloat() / videoHeight
        return when (cropMode) {
            ScreenCropMode.Fill -> ContentMapping()
            ScreenCropMode.Fit -> if (sourceAspect > screenAspectRatio) {
                ContentMapping(activeScaleY = screenAspectRatio / sourceAspect)
            } else {
                ContentMapping(activeScaleX = sourceAspect / screenAspectRatio)
            }
            ScreenCropMode.Crop -> if (sourceAspect > screenAspectRatio) {
                ContentMapping(sampleScaleX = screenAspectRatio / sourceAspect)
            } else {
                ContentMapping(sampleScaleY = sourceAspect / screenAspectRatio)
            }
        }
    }

    fun buildMesh(
        config: CinemaScreenConfig,
        videoWidth: Int,
        videoHeight: Int,
        curvedSegments: Int = DEFAULT_CURVED_SEGMENTS,
    ): FloatArray {
        require(config.widthMeters > 0f)
        require(config.distanceMeters > 0f)
        require(curvedSegments >= 1)

        val aspectRatio = resolveAspectRatio(config.aspectRatioMode, videoWidth, videoHeight)
        val height = config.widthMeters / aspectRatio
        val curved = config.curvatureRadiusMeters > 0f
        val segments = if (curved) curvedSegments else 1
        val vertices = FloatArray(segments * VERTICES_PER_SEGMENT * COMPONENTS_PER_VERTEX)
        val tiltRadians = Math.toRadians(config.tiltDegrees.toDouble()).toFloat()
        val tiltCos = cos(tiltRadians)
        val tiltSin = sin(tiltRadians)
        val radius = config.curvatureRadiusMeters.coerceAtLeast(config.widthMeters / PI.toFloat())
        val totalArc = if (curved) config.widthMeters / radius else 0f
        var offset = 0

        fun writeVertex(horizontal: Float, vertical: Float, u: Float, v: Float) {
            val angle = (horizontal - 0.5f) * totalArc
            val x = if (curved) sin(angle) * radius else (horizontal - 0.5f) * config.widthMeters
            val localZ = if (curved) (cos(angle) - 1f) * radius else 0f
            val localY = (vertical - 0.5f) * height
            val y = config.verticalOffsetMeters + localY * tiltCos - localZ * tiltSin
            val z = -config.distanceMeters + localY * tiltSin + localZ * tiltCos
            vertices[offset++] = x
            vertices[offset++] = y
            vertices[offset++] = z
            vertices[offset++] = u
            vertices[offset++] = v
        }

        for (segment in 0 until segments) {
            val left = segment.toFloat() / segments
            val right = (segment + 1f) / segments
            writeVertex(left, 0f, left, 0f)
            writeVertex(right, 0f, right, 0f)
            writeVertex(right, 1f, right, 1f)
            writeVertex(left, 0f, left, 0f)
            writeVertex(right, 1f, right, 1f)
            writeVertex(left, 1f, left, 1f)
        }
        return vertices
    }

    private const val DEFAULT_ASPECT_RATIO = 16f / 9f
    private const val DEFAULT_CURVED_SEGMENTS = 48
    private const val VERTICES_PER_SEGMENT = 6
    private const val COMPONENTS_PER_VERTEX = 5
}
