package com.aurora.cinema.hardening

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionReadinessPolicyTest {
    @Test
    fun releaseIsReadyWhenHardeningChecksPass() {
        val result = ProductionReadinessPolicy.evaluate(
            ProductionReadinessInput(
                releaseMinified = true,
                exportedPlaybackService = false,
                databaseSchemaVersion = 4,
                latestSchemaFileVersion = 4,
                crashReportingHookInstalled = true,
                hasReleaseSigningGuidance = true,
            ),
        )

        assertTrue(result.ready)
        assertTrue(result.blockers.isEmpty())
    }

    @Test
    fun releaseIsBlockedByUnsafeRuntimeSettings() {
        val result = ProductionReadinessPolicy.evaluate(
            ProductionReadinessInput(
                releaseMinified = false,
                exportedPlaybackService = true,
                databaseSchemaVersion = 4,
                latestSchemaFileVersion = 3,
                crashReportingHookInstalled = false,
                hasReleaseSigningGuidance = false,
            ),
        )

        assertFalse(result.ready)
        assertTrue(result.blockers.size >= 4)
        assertTrue(result.warnings.isNotEmpty())
    }
}
