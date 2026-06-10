package com.aurora.cinema.render

import android.view.Choreographer

internal class FrameClock(
    private val onFrame: (Long) -> Unit,
) : Choreographer.FrameCallback {
    private var choreographer: Choreographer? = null
    private var running = false

    fun start() {
        if (running) return
        running = true
        val frameScheduler = choreographer ?: Choreographer.getInstance().also {
            choreographer = it
        }
        frameScheduler.postFrameCallback(this)
    }

    fun stop() {
        running = false
        choreographer?.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        onFrame(frameTimeNanos)
        if (running) {
            choreographer?.postFrameCallback(this)
        }
    }
}
