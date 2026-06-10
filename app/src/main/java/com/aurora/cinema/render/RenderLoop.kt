package com.aurora.cinema.render

import android.opengl.GLES30
import android.os.Handler
import android.view.Surface

internal class RenderLoop(
    private val callbackHandler: Handler,
    private val onTelemetry: (RenderTelemetry) -> Unit,
    private val onVideoSurface: (Surface?) -> Unit,
) {
    private val egl = EglRenderSurface()
    private val frameTelemetry = FrameTelemetry()
    private val frameClock = FrameClock(::renderFrame)
    private var videoShader: ShaderProgram? = null
    private var mesh: Mesh? = null
    private var videoSampler: VideoFrameSampler? = null
    private var diagnosticOverlay: RenderOverlay? = null
    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var resumed = false
    private var diagnosticMeshEnabled = false
    private var videoWidth = 0
    private var videoHeight = 0
    private var screenScaleX = 1f
    private var screenScaleY = 1f
    private var videoTextureUniform = -1
    private var textureTransformUniform = -1
    private var screenScaleUniform = -1
    private var renderedFrames = 0L
    private var droppedFrames = 0L

    fun setSurface(surface: Surface, width: Int, height: Int) {
        releaseGl()
        this.surface = surface
        this.width = width
        this.height = height
        updateScreenScale()
        startIfReady()
    }

    fun resize(width: Int, height: Int) {
        this.width = width
        this.height = height
        updateScreenScale()
        if (videoShader != null) {
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

    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        updateScreenScale()
        videoSampler?.setDefaultBufferSize(width, height)
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
        if (videoShader == null) {
            runCatching {
                egl.create(currentSurface)
                GLES30.glViewport(0, 0, width, height)
                GLES30.glDisable(GLES30.GL_DEPTH_TEST)
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                videoShader = ShaderProgram(VERTEX_SHADER, VIDEO_FRAGMENT_SHADER)
                val shaderId = checkNotNull(videoShader).id
                videoTextureUniform = GLES30.glGetUniformLocation(shaderId, "uVideoTexture")
                textureTransformUniform = GLES30.glGetUniformLocation(shaderId, "uTextureTransform")
                screenScaleUniform = GLES30.glGetUniformLocation(shaderId, "uScreenScale")
                mesh = Mesh.screenQuad()
                diagnosticOverlay = DiagnosticOverlay()
                videoSampler = VideoFrameSampler(callbackHandler).also { sampler ->
                    sampler.setDefaultBufferSize(videoWidth, videoHeight)
                    onVideoSurface(sampler.surface)
                }
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
        val shader = videoShader ?: return
        if (!resumed) return
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        val sampler = videoSampler
        val updatedVideoFrame = sampler?.updateTextureIfNeeded() ?: false
        if (sampler != null && (sampler.presentedFrames > 0 || updatedVideoFrame)) {
            shader.use()
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, sampler.textureId)
            GLES30.glUniform1i(videoTextureUniform, 0)
            GLES30.glUniformMatrix4fv(
                textureTransformUniform,
                1,
                false,
                sampler.transformMatrix(),
                0,
            )
            GLES30.glUniform2f(
                screenScaleUniform,
                screenScaleX,
                screenScaleY,
            )
            mesh?.draw()
            GLES30.glBindTexture(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        }
        if (diagnosticMeshEnabled) {
            diagnosticOverlay?.draw()
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
        if (videoShader != null) {
            onVideoSurface(null)
            videoSampler?.release()
            diagnosticOverlay?.release()
            mesh?.release()
            videoShader?.release()
        }
        videoSampler = null
        diagnosticOverlay = null
        mesh = null
        videoShader = null
        videoTextureUniform = -1
        textureTransformUniform = -1
        screenScaleUniform = -1
        egl.release()
    }

    private fun updateScreenScale() {
        val scale = ScreenGeometry.aspectFit(width, height, videoWidth, videoHeight)
        screenScaleX = scale.x
        screenScaleY = scale.y
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
            glVendor = if (videoShader != null) GLES30.glGetString(GLES30.GL_VENDOR).orEmpty() else "",
            glRenderer = if (videoShader != null) GLES30.glGetString(GLES30.GL_RENDERER).orEmpty() else "",
            glVersion = if (videoShader != null) GLES30.glGetString(GLES30.GL_VERSION).orEmpty() else "",
            videoFramesAvailable = videoSampler?.availableFrameCount() ?: 0L,
            videoFramesPresented = videoSampler?.presentedFrames ?: 0L,
            videoSurfaceAttached = videoSampler != null,
            lastError = lastError,
        )
    }

    private companion object {
        val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec2 aTextureCoordinate;
            uniform vec2 uScreenScale;
            uniform mat4 uTextureTransform;
            out vec2 vTextureCoordinate;
            void main() {
                gl_Position = vec4(aPosition.xy * uScreenScale, aPosition.z, 1.0);
                vTextureCoordinate = (uTextureTransform * vec4(aTextureCoordinate, 0.0, 1.0)).xy;
            }
        """.trimIndent()

        val VIDEO_FRAGMENT_SHADER = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            uniform samplerExternalOES uVideoTexture;
            in vec2 vTextureCoordinate;
            out vec4 fragmentColor;
            void main() {
                fragmentColor = texture(uVideoTexture, vTextureCoordinate);
            }
        """.trimIndent()
    }
}
