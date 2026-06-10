package com.aurora.cinema.render

data class RenderTelemetry(
    val state: RenderState = RenderState.Idle,
    val framesPerSecond: Float = 0f,
    val averageFrameTimeMs: Float = 0f,
    val renderedFrames: Long = 0L,
    val estimatedDroppedFrames: Long = 0L,
    val surfaceWidth: Int = 0,
    val surfaceHeight: Int = 0,
    val glVendor: String = "",
    val glRenderer: String = "",
    val glVersion: String = "",
    val videoFramesAvailable: Long = 0L,
    val videoFramesPresented: Long = 0L,
    val videoSurfaceAttached: Boolean = false,
    val lastError: String? = null,
)

enum class RenderState {
    Idle,
    WaitingForSurface,
    Running,
    Paused,
    Failed,
    Released,
}
