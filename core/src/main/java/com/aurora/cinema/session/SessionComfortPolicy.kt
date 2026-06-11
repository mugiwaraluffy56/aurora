package com.aurora.cinema.session

data class SessionEnvironment(
    val thermalStatus: ThermalStatus = ThermalStatus.Nominal,
    val batteryPercent: Int = 100,
    val charging: Boolean = true,
    val headsetMode: Boolean = false,
    val comfortModeEnabled: Boolean = true,
)

enum class ThermalStatus {
    Nominal,
    Warm,
    Hot,
}

data class SessionComfortState(
    val seatedWarningVisible: Boolean,
    val brightnessLimit: Float,
    val saveProgressIntervalMs: Long,
    val warning: String?,
)

object SessionComfortPolicy {
    fun evaluate(environment: SessionEnvironment): SessionComfortState {
        val lowBattery = environment.batteryPercent <= 15 && !environment.charging
        val brightnessLimit = when {
            environment.thermalStatus == ThermalStatus.Hot -> 0.62f
            environment.thermalStatus == ThermalStatus.Warm -> 0.78f
            environment.comfortModeEnabled -> 0.86f
            else -> 1f
        }
        val warning = when {
            environment.thermalStatus == ThermalStatus.Hot -> "Device is hot. Brightness and effects are reduced."
            environment.thermalStatus == ThermalStatus.Warm -> "Device is warming up. Comfort mode is limiting brightness."
            lowBattery -> "Battery is low. Progress is being saved more often."
            environment.headsetMode -> "Use seated VR only and keep an easy path to exit."
            else -> null
        }
        return SessionComfortState(
            seatedWarningVisible = environment.headsetMode,
            brightnessLimit = brightnessLimit,
            saveProgressIntervalMs = if (lowBattery || environment.thermalStatus != ThermalStatus.Nominal) {
                2_000L
            } else {
                5_000L
            },
            warning = warning,
        )
    }
}
