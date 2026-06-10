package com.aurora.cinema.render

import kotlin.math.tan

object MatrixMath {
    fun identity(): FloatArray {
        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    fun perspective(
        verticalFieldOfViewDegrees: Float,
        aspectRatio: Float,
        nearPlane: Float,
        farPlane: Float,
    ): FloatArray {
        require(verticalFieldOfViewDegrees in 1f..179f)
        require(aspectRatio > 0f)
        require(nearPlane > 0f)
        require(farPlane > nearPlane)

        val focalLength = 1f / tan(Math.toRadians(verticalFieldOfViewDegrees / 2.0)).toFloat()
        val depth = nearPlane - farPlane
        return floatArrayOf(
            focalLength / aspectRatio, 0f, 0f, 0f,
            0f, focalLength, 0f, 0f,
            0f, 0f, (farPlane + nearPlane) / depth, -1f,
            0f, 0f, (2f * farPlane * nearPlane) / depth, 0f,
        )
    }

    fun multiply(left: FloatArray, right: FloatArray): FloatArray {
        require(left.size == MATRIX_SIZE && right.size == MATRIX_SIZE)
        return FloatArray(MATRIX_SIZE) { index ->
            val column = index / ROW_SIZE
            val row = index % ROW_SIZE
            var value = 0f
            for (element in 0 until ROW_SIZE) {
                value += left[element * ROW_SIZE + row] * right[column * ROW_SIZE + element]
            }
            value
        }
    }

    private const val ROW_SIZE = 4
    private const val MATRIX_SIZE = 16
}
