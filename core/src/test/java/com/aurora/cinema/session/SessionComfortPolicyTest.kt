package com.aurora.cinema.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionComfortPolicyTest {
    @Test
    fun headsetModeShowsSeatedWarning() {
        val state = SessionComfortPolicy.evaluate(SessionEnvironment(headsetMode = true))

        assertTrue(state.seatedWarningVisible)
        assertEquals("Use seated VR only and keep an easy path to exit.", state.warning)
    }

    @Test
    fun hotDeviceGetsStrongBrightnessLimitAndFrequentSaves() {
        val state = SessionComfortPolicy.evaluate(
            SessionEnvironment(thermalStatus = ThermalStatus.Hot, headsetMode = true),
        )

        assertEquals(0.62f, state.brightnessLimit, 0.0001f)
        assertEquals(2_000L, state.saveProgressIntervalMs)
    }

    @Test
    fun lowBatterySavesMoreOften() {
        val state = SessionComfortPolicy.evaluate(
            SessionEnvironment(batteryPercent = 10, charging = false),
        )

        assertEquals(2_000L, state.saveProgressIntervalMs)
        assertEquals("Battery is low. Progress is being saved more often.", state.warning)
    }
}
