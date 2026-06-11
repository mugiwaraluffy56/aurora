package com.aurora.cinema.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TheatreSceneGeometryTest {
    @Test
    fun theatreGeometryContainsRoomAndSeats() {
        val vertices = TheatreSceneGeometry.build()

        assertEquals(0, vertices.size % 6)
        assertTrue(vertices.size > 5 * 6 * 6)
    }

    @Test
    fun theatreGeometryStaysBehindViewer() {
        val zValues = TheatreSceneGeometry.build().toList().chunked(6).map { it[2] }

        assertTrue(zValues.minOrNull() ?: 0f < -30f)
        assertTrue(zValues.maxOrNull() ?: -100f <= -4f)
    }
}
