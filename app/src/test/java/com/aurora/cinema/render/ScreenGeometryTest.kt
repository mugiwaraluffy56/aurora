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

    @Test
    fun aspectPresetsOverrideSourceRatio() {
        assertEquals(
            1.43f,
            ScreenGeometry.resolveAspectRatio(ScreenAspectRatio.Imax143, 3840, 2160),
            TOLERANCE,
        )
        assertEquals(
            16f / 9f,
            ScreenGeometry.resolveAspectRatio(ScreenAspectRatio.Source, 3840, 2160),
            TOLERANCE,
        )
    }

    @Test
    fun fitMappingLetterboxesWideContent() {
        val mapping = ScreenGeometry.contentMapping(
            cropMode = ScreenCropMode.Fit,
            screenAspectRatio = 16f / 9f,
            videoWidth = 2390,
            videoHeight = 1000,
        )

        assertEquals(1f, mapping.activeScaleX, TOLERANCE)
        assertEquals((16f / 9f) / 2.39f, mapping.activeScaleY, TOLERANCE)
    }

    @Test
    fun curvedMeshHasStableSizeAndCurvedDepth() {
        val vertices = ScreenGeometry.buildMesh(
            config = CinemaScreenConfig(
                widthMeters = 18f,
                distanceMeters = 8f,
                curvatureRadiusMeters = 16f,
            ),
            videoWidth = 3840,
            videoHeight = 2160,
            curvedSegments = 8,
        )

        assertEquals(8 * 6 * 5, vertices.size)
        val leftEdgeZ = vertices[2]
        val centerSegmentZ = vertices[(4 * 6 * 5) + 2]
        assert(leftEdgeZ < centerSegmentZ)
    }

    @Test
    fun flatMeshUsesRequestedPhysicalDimensions() {
        val vertices = ScreenGeometry.buildMesh(
            config = CinemaScreenConfig(
                aspectRatioMode = ScreenAspectRatio.Imax190,
                widthMeters = 19f,
                distanceMeters = 10f,
            ),
            videoWidth = 3840,
            videoHeight = 2160,
        )

        assertEquals(-9.5f, vertices[0], TOLERANCE)
        assertEquals(-5f, vertices[1], TOLERANCE)
        assertEquals(-10f, vertices[2], TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
