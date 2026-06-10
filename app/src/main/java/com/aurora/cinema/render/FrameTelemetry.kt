package com.aurora.cinema.render

internal class FrameTelemetry(
    private val expectedFrameNanos: Long = 16_666_667L,
) {
    private var windowStartNanos = 0L
    private var previousFrameNanos = 0L
    private var frameCount = 0L
    private var frameTimeTotalNanos = 0L
    private var droppedFrames = 0L

    fun record(frameTimeNanos: Long): FrameSample? {
        if (windowStartNanos == 0L) {
            windowStartNanos = frameTimeNanos
            previousFrameNanos = frameTimeNanos
            return null
        }

        val deltaNanos = (frameTimeNanos - previousFrameNanos).coerceAtLeast(0L)
        previousFrameNanos = frameTimeNanos
        frameCount += 1
        frameTimeTotalNanos += deltaNanos
        droppedFrames += ((deltaNanos / expectedFrameNanos) - 1L).coerceAtLeast(0L)

        val elapsedNanos = frameTimeNanos - windowStartNanos
        if (elapsedNanos < SAMPLE_WINDOW_NANOS) return null

        val sample = FrameSample(
            framesPerSecond = frameCount * NANOS_PER_SECOND / elapsedNanos.toFloat(),
            averageFrameTimeMs = frameTimeTotalNanos / frameCount.toFloat() / NANOS_PER_MILLISECOND,
            renderedFrames = frameCount,
            estimatedDroppedFrames = droppedFrames,
        )
        windowStartNanos = frameTimeNanos
        frameCount = 0L
        frameTimeTotalNanos = 0L
        droppedFrames = 0L
        return sample
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val NANOS_PER_MILLISECOND = 1_000_000f
        const val SAMPLE_WINDOW_NANOS = 1_000_000_000L
    }
}

internal data class FrameSample(
    val framesPerSecond: Float,
    val averageFrameTimeMs: Float,
    val renderedFrames: Long,
    val estimatedDroppedFrames: Long,
)
