package com.aurora.cinema.render

import com.aurora.cinema.render.nativebridge.NativeMath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MatrixMathTest {
    @Test
    fun identityDoesNotChangeMatrix() {
        val matrix = FloatArray(16) { index -> index.toFloat() }

        assertArrayEquals(matrix, MatrixMath.multiply(MatrixMath.identity(), matrix), TOLERANCE)
        assertArrayEquals(matrix, MatrixMath.multiply(matrix, MatrixMath.identity()), TOLERANCE)
    }

    @Test
    fun perspectiveUsesExpectedProjectionTerms() {
        val matrix = MatrixMath.perspective(
            verticalFieldOfViewDegrees = 90f,
            aspectRatio = 2f,
            nearPlane = 0.1f,
            farPlane = 100f,
        )

        assertEquals(0.5f, matrix[0], TOLERANCE)
        assertEquals(1f, matrix[5], TOLERANCE)
        assertEquals(-1f, matrix[11], TOLERANCE)
        assertEquals(0f, matrix[15], TOLERANCE)
    }

    @Test
    fun nativeFacadeMatchesKotlinOrFallsBackOnJvm() {
        val left = FloatArray(16) { index -> (index + 1).toFloat() }
        val right = FloatArray(16) { index -> (16 - index).toFloat() }

        assertArrayEquals(
            MatrixMath.multiply(left, right),
            NativeMath.multiply(left, right),
            TOLERANCE,
        )
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
