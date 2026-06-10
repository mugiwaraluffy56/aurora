package com.aurora.cinema.tracking

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadPoseMathTest {
    @Test
    fun rotationVectorWithoutScalarComputesUnitQuaternion() {
        val quaternion = HeadPoseMath.quaternionFromRotationVector(floatArrayOf(0f, 0f, 0f))

        assertEquals(0f, quaternion.x, TOLERANCE)
        assertEquals(0f, quaternion.y, TOLERANCE)
        assertEquals(0f, quaternion.z, TOLERANCE)
        assertEquals(1f, quaternion.w, TOLERANCE)
    }

    @Test
    fun recenteredPoseProducesIdentityView() {
        val current = HeadPoseMath.normalize(Quaternion(0.1f, 0.2f, 0.05f, 0.97f))

        assertArrayEquals(
            com.aurora.cinema.render.MatrixMath.identity(),
            HeadPoseMath.viewMatrix(current, current),
            TOLERANCE,
        )
    }

    @Test
    fun differentPoseAfterRecenterProducesCameraRotation() {
        val recenter = HeadPoseMath.identityQuaternion()
        val current = HeadPoseMath.normalize(Quaternion(0f, 0.2f, 0f, 0.98f))
        val matrix = HeadPoseMath.viewMatrix(current, recenter)

        assertTrue(kotlin.math.abs(matrix[8]) > 0.1f)
    }

    @Test
    fun smootherMovesPartWayTowardNewPose() {
        val smoother = HeadPoseSmoother(amount = 0.25f)
        val first = HeadPoseMath.identityQuaternion()
        val second = HeadPoseMath.normalize(Quaternion(0f, 0.5f, 0f, 0.866f))

        assertEquals(first, smoother.smooth(first))
        val smoothed = smoother.smooth(second)

        assertTrue(smoothed.y > 0f)
        assertTrue(smoothed.y < second.y)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
