package com.aurora.cinema.telemetry

import com.aurora.cinema.media.CodecSummary
import com.aurora.cinema.playback.PlaybackState
import com.aurora.cinema.render.RenderState
import com.aurora.cinema.render.RenderTelemetry
import com.aurora.cinema.session.SessionEnvironment
import com.aurora.cinema.session.ThermalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceDiagnosticsPolicyTest {
    @Test
    fun nominalTelemetryKeepsFullQuality() {
        val diagnostics = PerformanceDiagnosticsPolicy.evaluate(
            renderTelemetry = RenderTelemetry(
                state = RenderState.Running,
                framesPerSecond = 60f,
                averageFrameTimeMs = 16.6f,
                renderedFrames = 120,
            ),
            playbackState = PlaybackState(videoWidth = 3840, videoHeight = 2160),
            environment = SessionEnvironment(),
            codecs = codecs,
        )

        assertEquals(PerformanceMode.Normal, diagnostics.mode)
        assertTrue(diagnostics.overlayEffectsAllowed)
        assertEquals(1f, diagnostics.brightnessLimit, 0.0001f)
    }

    @Test
    fun thermalWarmUsesWarmDegradation() {
        val diagnostics = PerformanceDiagnosticsPolicy.evaluate(
            renderTelemetry = RenderTelemetry(),
            playbackState = PlaybackState(),
            environment = SessionEnvironment(thermalStatus = ThermalStatus.Warm),
            codecs = codecs,
        )

        assertEquals(PerformanceMode.Warm, diagnostics.mode)
        assertFalse(diagnostics.overlayEffectsAllowed)
    }

    @Test
    fun highVideoBacklogTriggersWarmMode() {
        val diagnostics = PerformanceDiagnosticsPolicy.evaluate(
            renderTelemetry = RenderTelemetry(
                videoFramesAvailable = 30,
                videoFramesPresented = 10,
            ),
            playbackState = PlaybackState(),
            environment = SessionEnvironment(),
            codecs = codecs,
        )

        assertEquals(PerformanceMode.Warm, diagnostics.mode)
        assertEquals(20L, diagnostics.estimatedVideoFrameBacklog)
    }

    private val codecs = listOf(
        CodecSummary("H.264 / AVC", "video/avc", "decoder.avc", true),
        CodecSummary("H.265 / HEVC", "video/hevc", "", false),
    )
}
