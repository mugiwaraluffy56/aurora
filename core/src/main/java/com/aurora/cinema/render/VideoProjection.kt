package com.aurora.cinema.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class VideoProjection(
    val label: String,
) {
    Cinema("Cinema screen"),
    Equirectangular180("180 equirectangular"),
    Equirectangular360("360 equirectangular"),
}

object EquirectangularSphere {
    fun buildMesh(
        projection: VideoProjection,
        radiusMeters: Float = DEFAULT_RADIUS_METERS,
        horizontalSegments: Int = DEFAULT_HORIZONTAL_SEGMENTS,
        verticalSegments: Int = DEFAULT_VERTICAL_SEGMENTS,
    ): FloatArray {
        require(projection != VideoProjection.Cinema)
        require(radiusMeters > 0f)
        require(horizontalSegments >= 4)
        require(verticalSegments >= 2)

        val horizontalRadians = when (projection) {
            VideoProjection.Equirectangular180 -> PI.toFloat()
            VideoProjection.Equirectangular360 -> (PI * 2.0).toFloat()
            VideoProjection.Cinema -> error("Cinema projection does not use a sphere")
        }
        val startYaw = -horizontalRadians / 2f
        val vertices = FloatArray(horizontalSegments * verticalSegments * 6 * COMPONENTS_PER_VERTEX)
        var offset = 0

        fun writeVertex(h: Float, v: Float) {
            val yaw = startYaw + h * horizontalRadians
            val pitch = (0.5f - v) * PI.toFloat()
            val cosPitch = cos(pitch)
            vertices[offset++] = sin(yaw) * cosPitch * radiusMeters
            vertices[offset++] = sin(pitch) * radiusMeters
            vertices[offset++] = -cos(yaw) * cosPitch * radiusMeters
            vertices[offset++] = h
            vertices[offset++] = v
        }

        for (y in 0 until verticalSegments) {
            val top = y.toFloat() / verticalSegments
            val bottom = (y + 1f) / verticalSegments
            for (x in 0 until horizontalSegments) {
                val left = x.toFloat() / horizontalSegments
                val right = (x + 1f) / horizontalSegments
                writeVertex(left, top)
                writeVertex(left, bottom)
                writeVertex(right, bottom)
                writeVertex(left, top)
                writeVertex(right, bottom)
                writeVertex(right, top)
            }
        }
        return vertices
    }

    private const val DEFAULT_RADIUS_METERS = 16f
    private const val DEFAULT_HORIZONTAL_SEGMENTS = 96
    private const val DEFAULT_VERTICAL_SEGMENTS = 48
    private const val COMPONENTS_PER_VERTEX = 5
}
