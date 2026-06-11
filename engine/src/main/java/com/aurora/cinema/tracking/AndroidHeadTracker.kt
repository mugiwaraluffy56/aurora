package com.aurora.cinema.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class AndroidHeadTracker(
    context: Context,
    private val onPose: (HeadPose) -> Unit,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val smoother = HeadPoseSmoother()
    private var recenterQuaternion = HeadPoseMath.identityQuaternion()
    private var latestQuaternion: Quaternion? = null
    private var running = false
    private var pendingRecenter = false
    private var prevQuaternion: Quaternion? = null
    private var prevTimestampNs: Long = 0L

    val available: Boolean
        get() = sensor != null

    val sensorName: String
        get() = sensor?.name ?: "Rotation vector unavailable"

    fun start() {
        val currentSensor = sensor ?: run {
            onPose(HeadPose(sensorName = sensorName))
            return
        }
        if (running) return
        running = true
        pendingRecenter = true   // recenter on first reading, not on a timer
        smoother.reset()
        sensorManager.registerListener(
            this,
            currentSensor,
            SensorManager.SENSOR_DELAY_FASTEST,
        )
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager.unregisterListener(this)
        smoother.reset()
        onPose(HeadPose(sensorName = sensorName))
    }

    fun recenter() {
        latestQuaternion?.let { quaternion ->
            recenterQuaternion = quaternion
            smoother.reset()
            onPose(
                HeadPose(
                    viewMatrix = HeadPoseMath.viewMatrix(quaternion, recenterQuaternion),
                    tracking = running,
                    sensorName = sensorName,
                ),
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val quaternion = HeadPoseMath.quaternionFromRotationVector(event.values)
        latestQuaternion = quaternion
        if (pendingRecenter) {
            pendingRecenter = false
            recenterQuaternion = quaternion
            smoother.reset()
        }

        // Angular velocity: angle between consecutive quaternions / delta time
        val ts = event.timestamp
        val angularVel = if (prevQuaternion != null && prevTimestampNs > 0L) {
            val dtSec = (ts - prevTimestampNs) / 1_000_000_000f
            if (dtSec > 0f) {
                val prev = prevQuaternion!!
                val dot = (quaternion.x * prev.x + quaternion.y * prev.y +
                           quaternion.z * prev.z + quaternion.w * prev.w)
                    .coerceIn(-1f, 1f)
                val angleRad = 2f * kotlin.math.acos(kotlin.math.abs(dot))
                angleRad / dtSec
            } else 0f
        } else 0f
        prevQuaternion = quaternion
        prevTimestampNs = ts

        val smoothed = smoother.smooth(quaternion)
        onPose(
            HeadPose(
                viewMatrix = HeadPoseMath.viewMatrix(smoothed, recenterQuaternion),
                tracking = running,
                sensorName = sensorName,
                angularVelocityRadsPerSec = angularVel,
            ),
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
