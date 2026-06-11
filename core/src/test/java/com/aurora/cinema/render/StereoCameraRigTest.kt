package com.aurora.cinema.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StereoCameraRigTest {
    @Test
    fun eyesUseEqualAndOppositeHorizontalOffsets() {
        val eyes = StereoCameraRig.eyeMatrices(
            config = StereoConfig(ipdMeters = 0.064f),
            eyeAspectRatio = 1f,
        )

        assertNotEquals(eyes.left[12], eyes.right[12])
        assertEquals(-eyes.left[12], eyes.right[12], TOLERANCE)
    }

    @Test
    fun largerIpdIncreasesStereoSeparation() {
        val narrow = StereoCameraRig.eyeMatrices(
            config = StereoConfig(ipdMeters = 0.05f),
            eyeAspectRatio = 1f,
        )
        val wide = StereoCameraRig.eyeMatrices(
            config = StereoConfig(ipdMeters = 0.075f),
            eyeAspectRatio = 1f,
        )

        val narrowSeparation = kotlin.math.abs(narrow.left[12] - narrow.right[12])
        val wideSeparation = kotlin.math.abs(wide.left[12] - wide.right[12])
        assertTrue(wideSeparation > narrowSeparation)
    }

    @Test
    fun translationMatrixStoresOffsetsInFinalColumn() {
        val matrix = MatrixMath.translation(1f, 2f, 3f)

        assertEquals(1f, matrix[12], TOLERANCE)
        assertEquals(2f, matrix[13], TOLERANCE)
        assertEquals(3f, matrix[14], TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
