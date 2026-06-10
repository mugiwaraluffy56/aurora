package com.aurora.cinema.render

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenGeometryTest {
    @Test
    fun wideVideoLetterboxesVertically() {
        val scale = ScreenGeometry.aspectFit(
            surfaceWidth = 1920,
            surfaceHeight = 1080,
            videoWidth = 3840,
            videoHeight = 1600,
        )

        assertEquals(1f, scale.x, TOLERANCE)
        assertEquals(0.7407f, scale.y, TOLERANCE)
    }

    @Test
    fun portraitVideoPillarboxesHorizontally() {
        val scale = ScreenGeometry.aspectFit(
            surfaceWidth = 1920,
            surfaceHeight = 1080,
            videoWidth = 1080,
            videoHeight = 1920,
        )

        assertEquals(0.3164f, scale.x, TOLERANCE)
        assertEquals(1f, scale.y, TOLERANCE)
    }

    @Test
    fun unknownDimensionsUseFullSurface() {
        assertEquals(
            ScreenScale(1f, 1f),
            ScreenGeometry.aspectFit(1920, 1080, 0, 0),
        )
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
