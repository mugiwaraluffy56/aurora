package com.aurora.cinema.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FrameTelemetryTest {
    @Test
    fun reportsStableSixtyFpsWindow() {
        val telemetry = FrameTelemetry()
        var sample: FrameSample? = null
        var frameTime = 1_000_000_000L

        repeat(62) {
            sample = telemetry.record(frameTime) ?: sample
            frameTime += 16_666_667L
        }

        val result = assertNotNull(sample).let { requireNotNull(sample) }
        assertEquals(60f, result.framesPerSecond, 0.2f)
        assertEquals(16.67f, result.averageFrameTimeMs, 0.1f)
        assertEquals(0L, result.estimatedDroppedFrames)
    }
}
