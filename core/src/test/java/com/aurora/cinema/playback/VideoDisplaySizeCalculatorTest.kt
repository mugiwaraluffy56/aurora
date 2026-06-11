package com.aurora.cinema.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoDisplaySizeCalculatorTest {
    @Test
    fun appliesPixelAspectRatio() {
        assertEquals(
            VideoDisplaySize(width = 1920, height = 1080),
            VideoDisplaySizeCalculator.calculate(
                width = 1440,
                height = 1080,
                pixelWidthHeightRatio = 4f / 3f,
            ),
        )
    }

    @Test
    fun keepsSquarePixelDimensions() {
        assertEquals(
            VideoDisplaySize(width = 1920, height = 1080),
            VideoDisplaySizeCalculator.calculate(
                width = 1920,
                height = 1080,
                pixelWidthHeightRatio = 1f,
            ),
        )
    }
}
