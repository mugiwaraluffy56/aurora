package com.aurora.cinema.ui

import com.aurora.cinema.render.MatrixMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VrDwellSelectorTest {
    @Test
    fun dwellFiresOnlyAfterThreshold() {
        val selector = VrDwellSelector(dwellMillis = 1_000L, cooldownMillis = 500L)

        assertEquals(VrGazeTarget.None, selector.update(VrGazeTarget.PlayPause, 0L).fired)
        assertEquals(VrGazeTarget.None, selector.update(VrGazeTarget.PlayPause, 999L).fired)
        assertEquals(VrGazeTarget.PlayPause, selector.update(VrGazeTarget.PlayPause, 1_000L).fired)
    }

    @Test
    fun changingTargetResetsProgress() {
        val selector = VrDwellSelector(dwellMillis = 1_000L, cooldownMillis = 500L)

        selector.update(VrGazeTarget.Back, 0L)
        val changed = selector.update(VrGazeTarget.Forward, 800L)

        assertEquals(VrGazeTarget.Forward, changed.target)
        assertEquals(0f, changed.progress, 0.0001f)
    }

    @Test
    fun mapperChoosesCenterPlaybackByDefault() {
        assertEquals(VrGazeTarget.PlayPause, VrGazeMapper.targetFromViewMatrix(MatrixMath.identity()))
    }

    @Test
    fun mapperUsesHorizontalGazeForSeek() {
        val matrix = MatrixMath.identity().also { it[8] = 0.6f }

        assertEquals(VrGazeTarget.Forward, VrGazeMapper.targetFromViewMatrix(matrix))
        assertTrue(VrGazeMapper.targetFromViewMatrix(FloatArray(4)) == VrGazeTarget.None)
    }
}
