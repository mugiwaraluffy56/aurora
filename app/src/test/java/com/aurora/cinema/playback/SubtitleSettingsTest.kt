package com.aurora.cinema.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleSettingsTest {
    @Test
    fun clampsReadableVrRanges() {
        val settings = SubtitleSettings(
            sizeScale = 3f,
            verticalOffset = 1f,
            depthMeters = 20f,
            audioDelayMs = 5_000L,
        ).clamped()

        assertEquals(1.8f, settings.sizeScale, 0.0001f)
        assertEquals(0.35f, settings.verticalOffset, 0.0001f)
        assertEquals(12f, settings.depthMeters, 0.0001f)
        assertEquals(1_000L, settings.audioDelayMs)
    }
}
