package com.aurora.cinema.core.nativebridge

object NativeCore {
    init {
        System.loadLibrary("aurora_native")
    }

    external fun engineName(): String
}
