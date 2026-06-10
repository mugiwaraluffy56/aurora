package com.aurora.cinema.ui

import kotlin.math.abs

enum class VrGazeTarget {
    None,
    Back,
    PlayPause,
    Forward,
    Timeline,
    Recenter,
    ScreenSmaller,
    ScreenLarger,
    LockControls,
    Exit,
}

data class DwellSelection(
    val target: VrGazeTarget = VrGazeTarget.None,
    val progress: Float = 0f,
    val fired: VrGazeTarget = VrGazeTarget.None,
)

class VrDwellSelector(
    private val dwellMillis: Long = 900L,
    private val cooldownMillis: Long = 650L,
) {
    private var currentTarget = VrGazeTarget.None
    private var targetStartedAt = 0L
    private var cooldownUntil = 0L

    fun update(target: VrGazeTarget, nowMillis: Long): DwellSelection {
        if (target == VrGazeTarget.None || nowMillis < cooldownUntil) {
            if (target == VrGazeTarget.None) currentTarget = VrGazeTarget.None
            return DwellSelection(target = target)
        }
        if (target != currentTarget) {
            currentTarget = target
            targetStartedAt = nowMillis
            return DwellSelection(target = target)
        }

        val elapsed = nowMillis - targetStartedAt
        val progress = (elapsed.toFloat() / dwellMillis.toFloat()).coerceIn(0f, 1f)
        if (elapsed >= dwellMillis) {
            cooldownUntil = nowMillis + cooldownMillis
            currentTarget = VrGazeTarget.None
            return DwellSelection(target = target, progress = 1f, fired = target)
        }
        return DwellSelection(target = target, progress = progress)
    }
}

object VrGazeMapper {
    fun targetFromViewMatrix(viewMatrix: FloatArray): VrGazeTarget {
        if (viewMatrix.size != 16) return VrGazeTarget.None
        val yaw = viewMatrix[8].coerceIn(-1f, 1f)
        val pitch = viewMatrix[9].coerceIn(-1f, 1f)
        return when {
            pitch > 0.46f && yaw < -0.35f -> VrGazeTarget.Recenter
            pitch > 0.46f && abs(yaw) <= 0.35f -> VrGazeTarget.LockControls
            pitch > 0.46f -> VrGazeTarget.Exit
            pitch < -0.48f && yaw < -0.2f -> VrGazeTarget.ScreenSmaller
            pitch < -0.48f && yaw > 0.2f -> VrGazeTarget.ScreenLarger
            pitch < -0.48f -> VrGazeTarget.Timeline
            yaw < -0.42f -> VrGazeTarget.Back
            yaw > 0.42f -> VrGazeTarget.Forward
            else -> VrGazeTarget.PlayPause
        }
    }
}
