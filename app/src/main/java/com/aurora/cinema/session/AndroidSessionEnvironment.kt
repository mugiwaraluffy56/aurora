package com.aurora.cinema.session

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

object AndroidSessionEnvironment {
    fun read(
        context: Context,
        headsetMode: Boolean,
        comfortModeEnabled: Boolean,
    ): SessionEnvironment {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } else {
            100
        }
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val thermalStatus = readThermalStatus(context)
        return SessionEnvironment(
            thermalStatus = thermalStatus,
            batteryPercent = batteryPercent,
            charging = charging,
            headsetMode = headsetMode,
            comfortModeEnabled = comfortModeEnabled,
        )
    }

    private fun readThermalStatus(context: Context): ThermalStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalStatus.Nominal
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return ThermalStatus.Nominal
        return when (powerManager.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_SEVERE,
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.Hot
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.Warm
            else -> ThermalStatus.Nominal
        }
    }
}
