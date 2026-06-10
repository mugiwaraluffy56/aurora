package com.aurora.cinema.telemetry

import com.aurora.cinema.media.CodecSummary
import com.aurora.cinema.playback.PlaybackState
import com.aurora.cinema.render.RenderTelemetry
import com.aurora.cinema.session.SessionEnvironment
import com.aurora.cinema.session.ThermalStatus

enum class PerformanceMode(val label: String) {
    Normal("Normal"),
    Warm("Warm"),
    Hot("Hot"),
}

data class PerformanceDiagnostics(
    val mode: PerformanceMode,
    val renderHealth: String,
    val decoderHealth: String,
    val estimatedVideoFrameBacklog: Long,
    val overlayEffectsAllowed: Boolean,
    val brightnessLimit: Float,
    val warning: String?,
    val profilerChecklist: List<String>,
)

object PerformanceDiagnosticsPolicy {
    fun evaluate(
        renderTelemetry: RenderTelemetry,
        playbackState: PlaybackState,
        environment: SessionEnvironment,
        codecs: List<CodecSummary>,
    ): PerformanceDiagnostics {
        val frameBacklog = (
            renderTelemetry.videoFramesAvailable - renderTelemetry.videoFramesPresented
            ).coerceAtLeast(0L)
        val slowRender = renderTelemetry.averageFrameTimeMs > 20f && renderTelemetry.renderedFrames > 0
        val droppedRenderFrames = renderTelemetry.estimatedDroppedFrames > 0
        val mode = when {
            environment.thermalStatus == ThermalStatus.Hot -> PerformanceMode.Hot
            environment.thermalStatus == ThermalStatus.Warm || slowRender || frameBacklog > 12L -> PerformanceMode.Warm
            else -> PerformanceMode.Normal
        }
        val renderHealth = when {
            renderTelemetry.state.name == "Failed" -> "Renderer failed: ${renderTelemetry.lastError.orEmpty()}"
            slowRender -> "Render frame time is above 20 ms."
            droppedRenderFrames -> "Render loop is estimating dropped frames."
            renderTelemetry.framesPerSecond > 0f -> "%.1f fps at %.2f ms".format(
                renderTelemetry.framesPerSecond,
                renderTelemetry.averageFrameTimeMs,
            )
            else -> "Waiting for frame samples"
        }
        val decoderHealth = decoderHealth(playbackState, codecs)
        return PerformanceDiagnostics(
            mode = mode,
            renderHealth = renderHealth,
            decoderHealth = decoderHealth,
            estimatedVideoFrameBacklog = frameBacklog,
            overlayEffectsAllowed = mode == PerformanceMode.Normal,
            brightnessLimit = when (mode) {
                PerformanceMode.Normal -> 1f
                PerformanceMode.Warm -> 0.78f
                PerformanceMode.Hot -> 0.62f
            },
            warning = when (mode) {
                PerformanceMode.Normal -> null
                PerformanceMode.Warm -> "Performance is constrained. Keep the scene simple and target 60 fps."
                PerformanceMode.Hot -> "Thermal pressure is high. Reduce brightness and disable optional scene work."
            },
            profilerChecklist = releaseProfilerChecklist,
        )
    }

    private fun decoderHealth(
        playbackState: PlaybackState,
        codecs: List<CodecSummary>,
    ): String {
        val resolution = if (playbackState.videoWidth > 0 && playbackState.videoHeight > 0) {
            "${playbackState.videoWidth}x${playbackState.videoHeight}"
        } else {
            "No active video"
        }
        val supportedFamilies = codecs.count { it.supported }
        return "$resolution, $supportedFamilies/${codecs.size} target decoder families available"
    }

    val releaseProfilerChecklist = listOf(
        "Run a release build, not only debug.",
        "Capture Perfetto during sustained VR playback.",
        "Check render, decode, thermal, memory, and battery tracks.",
        "Record at least one high-bitrate 4K playback sample when a device is available.",
    )
}
