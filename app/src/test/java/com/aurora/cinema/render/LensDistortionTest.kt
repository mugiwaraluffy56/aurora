package com.aurora.cinema.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LensDistortionTest {
    @Test
    fun zeroCoefficientsLeaveRadiusUnchanged() {
        val profile = HeadsetProfile(distortionK1 = 0f, distortionK2 = 0f, distortionK3 = 0f)

        assertEquals(0.75f, LensDistortion.distortRadius(0.75f, profile), TOLERANCE)
    }

    @Test
    fun positiveBarrelCoefficientsIncreaseOuterRadius() {
        val profile = HeadsetProfile(distortionK1 = 0.2f, distortionK2 = 0.1f)

        assertTrue(LensDistortion.distortRadius(0.9f, profile) > 0.9f)
    }

    @Test
    fun lookupTableIncludesStableEndpoints() {
        val table = LensDistortion.lookupTable(HeadsetProfile(), samples = 8)

        assertEquals(0f, table.first().radius, TOLERANCE)
        assertEquals(1f, table.last().radius, TOLERANCE)
        assertEquals(8, table.size)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
