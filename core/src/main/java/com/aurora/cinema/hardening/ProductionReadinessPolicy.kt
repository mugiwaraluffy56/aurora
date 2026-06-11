package com.aurora.cinema.hardening

data class ProductionReadinessInput(
    val releaseMinified: Boolean,
    val exportedPlaybackService: Boolean,
    val databaseSchemaVersion: Int,
    val latestSchemaFileVersion: Int,
    val crashReportingHookInstalled: Boolean,
    val hasReleaseSigningGuidance: Boolean,
)

data class ProductionReadinessResult(
    val ready: Boolean,
    val blockers: List<String>,
    val warnings: List<String>,
)

object ProductionReadinessPolicy {
    fun evaluate(input: ProductionReadinessInput): ProductionReadinessResult {
        val blockers = buildList {
            if (!input.releaseMinified) add("Release builds must run through R8.")
            if (input.exportedPlaybackService) add("Playback service should not be exported without a permission boundary.")
            if (input.databaseSchemaVersion != input.latestSchemaFileVersion) {
                add("Room database version and exported schema version are out of sync.")
            }
            if (!input.crashReportingHookInstalled) add("Crash reporting hook is not installed.")
        }
        val warnings = buildList {
            if (!input.hasReleaseSigningGuidance) add("Release signing guidance is missing.")
        }
        return ProductionReadinessResult(
            ready = blockers.isEmpty(),
            blockers = blockers,
            warnings = warnings,
        )
    }
}
