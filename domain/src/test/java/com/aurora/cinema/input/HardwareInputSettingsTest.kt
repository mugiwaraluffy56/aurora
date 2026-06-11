package com.aurora.cinema.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HardwareInputSettingsTest {
    @Test
    fun defaultBindingsMapPlaybackControls() {
        val settings = HardwareInputSettings()

        assertEquals(InputAction.PlayPause, settings.actionFor(PhysicalInput.Center))
        assertEquals(InputAction.SeekBack, settings.actionFor(PhysicalInput.DpadLeft))
        assertEquals(InputAction.SeekForward, settings.actionFor(PhysicalInput.DpadRight))
        assertEquals(InputAction.Recenter, settings.actionFor(PhysicalInput.ButtonX))
        assertEquals(InputAction.Stop, settings.actionFor(PhysicalInput.MediaStop))
    }

    @Test
    fun volumeShortcutsAreVisibleButNotConsumedByDefault() {
        val settings = HardwareInputSettings()

        assertEquals(InputAction.VolumeUp, settings.actionFor(PhysicalInput.VolumeUp))
        assertEquals(InputAction.VolumeDown, settings.actionFor(PhysicalInput.VolumeDown))
        assertFalse(settings.consumeVolumeKeys)
    }
}
