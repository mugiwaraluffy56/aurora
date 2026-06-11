package com.aurora.cinema.media

import android.content.Context
import android.os.Build

class DeviceDisplayInfo(
    private val context: Context,
) {
    fun read(): DisplayDiagnostics {
        val displayMetrics = context.resources.displayMetrics
        val refreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display.refreshRate
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)
                ?.defaultDisplay
                ?.refreshRate
                ?: 0f
        }

        return DisplayDiagnostics(
            widthPixels = displayMetrics.widthPixels,
            heightPixels = displayMetrics.heightPixels,
            densityDpi = displayMetrics.densityDpi,
            refreshRate = refreshRate,
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        )
    }
}

data class DisplayDiagnostics(
    val widthPixels: Int,
    val heightPixels: Int,
    val densityDpi: Int,
    val refreshRate: Float,
    val androidVersion: String,
    val deviceName: String,
)
