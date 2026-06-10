package com.aurora.cinema.render

import android.opengl.GLES30
import android.view.Surface

internal class RenderLoop(
    private val onTelemetry: (RenderTelemetry) -> Unit,
) {
    private val egl = EglRenderSurface()
    private val frameTelemetry = FrameTelemetry()
    private val frameClock = FrameClock(::renderFrame)
    private var shaderProgram: ShaderProgram? = null
    private var mesh: Mesh? = null
    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var resumed = false
    private var diagnosticMeshEnabled = false
    private var renderedFrames = 0L
    private var droppedFrames = 0L

    fun setSurface(surface: Surface, width: Int, height: Int) {
        releaseGl()
        this.surface = surface
        this.width = width
        this.height = height
        startIfReady()
    }

    fun resize(width: Int, height: Int) {
        this.width = width
        this.height = height
        if (shaderProgram != null) {
            GLES30.glViewport(0, 0, width, height)
        }
    }

    fun clearSurface() {
        frameClock.stop()
        releaseGl()
        surface = null
        publish(RenderState.WaitingForSurface)
    }

    fun resume() {
        resumed = true
        startIfReady()
    }

    fun pause() {
        resumed = false
        frameClock.stop()
        publish(RenderState.Paused)
    }

    fun setDiagnosticMeshEnabled(enabled: Boolean) {
        diagnosticMeshEnabled = enabled
    }

    fun release() {
        resumed = false
        frameClock.stop()
        releaseGl()
        surface = null
        publish(RenderState.Released)
    }

    private fun startIfReady() {
        val currentSurface = surface
        if (!resumed || currentSurface == null || !currentSurface.isValid) {
            publish(if (resumed) RenderState.WaitingForSurface else RenderState.Paused)
            return
        }
        if (shaderProgram == null) {
            runCatching {
                egl.create(currentSurface)
                GLES30.glViewport(0, 0, width, height)
                GLES30.glDisable(GLES30.GL_DEPTH_TEST)
                shaderProgram = ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER)
                mesh = Mesh.diagnosticScreen()
            }.onFailure { error ->
                releaseGl()
                onTelemetry(
                    RenderTelemetry(
                        state = RenderState.Failed,
                        surfaceWidth = width,
                        surfaceHeight = height,
                        lastError = error.message ?: error.javaClass.simpleName,
                    ),
                )
                return
            }
        }
        publish(RenderState.Running)
        frameClock.start()
    }

    private fun renderFrame(frameTimeNanos: Long) {
        if (!resumed || shaderProgram == null) return
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (diagnosticMeshEnabled) {
            shaderProgram?.use()
            mesh?.draw()
        }
        if (!egl.swapBuffers()) {
            frameClock.stop()
            publish(RenderState.Failed, "EGL buffer swap failed")
            return
        }

        renderedFrames += 1
        frameTelemetry.record(frameTimeNanos)?.let { sample ->
            droppedFrames += sample.estimatedDroppedFrames
            onTelemetry(
                currentTelemetry(
                    state = RenderState.Running,
                    framesPerSecond = sample.framesPerSecond,
                    averageFrameTimeMs = sample.averageFrameTimeMs,
                ),
            )
        }
    }

    private fun releaseGl() {
        if (shaderProgram != null) {
            mesh?.release()
            shaderProgram?.release()
        }
        mesh = null
        shaderProgram = null
        egl.release()
    }

    private fun publish(state: RenderState, error: String? = null) {
        onTelemetry(currentTelemetry(state = state, lastError = error))
    }

    private fun currentTelemetry(
        state: RenderState,
        framesPerSecond: Float = 0f,
        averageFrameTimeMs: Float = 0f,
        lastError: String? = null,
    ): RenderTelemetry {
        return RenderTelemetry(
            state = state,
            framesPerSecond = framesPerSecond,
            averageFrameTimeMs = averageFrameTimeMs,
            renderedFrames = renderedFrames,
            estimatedDroppedFrames = droppedFrames,
            surfaceWidth = width,
            surfaceHeight = height,
            glVendor = if (shaderProgram != null) GLES30.glGetString(GLES30.GL_VENDOR).orEmpty() else "",
            glRenderer = if (shaderProgram != null) GLES30.glGetString(GLES30.GL_RENDERER).orEmpty() else "",
            glVersion = if (shaderProgram != null) GLES30.glGetString(GLES30.GL_VERSION).orEmpty() else "",
            lastError = lastError,
        )
    }

    private companion object {
        val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 1.0);
            }
        """.trimIndent()

        val FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            out vec4 fragmentColor;
            void main() {
                fragmentColor = vec4(0.12, 0.36, 0.48, 1.0);
            }
        """.trimIndent()
    }
}
