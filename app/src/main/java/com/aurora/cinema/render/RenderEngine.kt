package com.aurora.cinema.render

import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RenderEngine {
    private val thread = HandlerThread("AuroraRenderThread").apply { start() }
    private val handler = Handler(thread.looper)
    private val mutableTelemetry = MutableStateFlow(
        RenderTelemetry(state = RenderState.WaitingForSurface),
    )
    private val renderLoop = RenderLoop { telemetry ->
        mutableTelemetry.value = telemetry
    }
    private var released = false

    val telemetry: StateFlow<RenderTelemetry> = mutableTelemetry.asStateFlow()

    fun attachSurface(surface: Surface, width: Int, height: Int) {
        post { setSurface(surface, width, height) }
    }

    fun resize(width: Int, height: Int) {
        post { resize(width, height) }
    }

    fun detachSurface() {
        post { clearSurface() }
    }

    fun resume() {
        post { resume() }
    }

    fun pause() {
        post { pause() }
    }

    fun setDiagnosticMeshEnabled(enabled: Boolean) {
        post { setDiagnosticMeshEnabled(enabled) }
    }

    fun release() {
        if (released) return
        released = true
        handler.post {
            renderLoop.release()
            thread.quitSafely()
        }
    }

    private fun post(block: RenderLoop.() -> Unit) {
        if (!released) {
            handler.post { renderLoop.block() }
        }
    }
}
