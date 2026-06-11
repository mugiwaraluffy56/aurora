package com.aurora.cinema

import android.content.Context
import android.util.Log

class CrashReporter private constructor(
    private val previousHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Log.e(TAG, "Unhandled Aurora crash on ${thread.name}", throwable)
        previousHandler?.uncaughtException(thread, throwable)
    }

    companion object {
        private const val TAG = "AuroraCrash"
        private var installed = false

        fun install(context: Context) {
            if (installed) return
            installed = true
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(CrashReporter(previous))
            Log.i(TAG, "Crash reporting hook installed for ${context.packageName}")
        }
    }
}
