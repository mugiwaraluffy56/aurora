package com.aurora.cinema.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EquirectangularSphereTest {
    @Test
    fun sphereMeshHasExpectedTriangleData() {
        val vertices = EquirectangularSphere.buildMesh(
            projection = VideoProjection.Equirectangular360,
            horizontalSegments = 8,
            verticalSegments = 4,
        )

        assertEquals(8 * 4 * 6 * 5, vertices.size)
    }

    @Test
    fun halfSphereKeepsUvRangeStable() {
        val vertices = EquirectangularSphere.buildMesh(
            projection = VideoProjection.Equirectangular180,
            horizontalSegments = 4,
            verticalSegments = 2,
        )

        val uValues = vertices.toList().chunked(5).map { it[3] }
        val vValues = vertices.toList().chunked(5).map { it[4] }
        assertEquals(0f, uValues.minOrNull() ?: -1f, TOLERANCE)
        assertEquals(1f, uValues.maxOrNull() ?: -1f, TOLERANCE)
        assertEquals(0f, vValues.minOrNull() ?: -1f, TOLERANCE)
        assertEquals(1f, vValues.maxOrNull() ?: -1f, TOLERANCE)
    }

    @Test
    fun sphereFacesForwardAtCenter() {
        val vertices = EquirectangularSphere.buildMesh(
            projection = VideoProjection.Equirectangular180,
            horizontalSegments = 4,
            verticalSegments = 2,
        )

        val zValues = vertices.toList().chunked(5).map { it[2] }
        assertTrue(zValues.minOrNull() ?: 0f < -15f)
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
