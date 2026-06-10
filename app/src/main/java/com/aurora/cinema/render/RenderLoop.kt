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
    private var compositeShader: ShaderProgram? = null
    private var mesh: Mesh? = null
    private var compositeMesh: Mesh? = null
    private var leftEyeTarget: EyeFramebuffer? = null
    private var rightEyeTarget: EyeFramebuffer? = null
    private var videoSampler: VideoFrameSampler? = null
    private var diagnosticOverlay: RenderOverlay? = null
    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var resumed = false
    private var diagnosticMeshEnabled = false
    private var videoWidth = 0
    private var videoHeight = 0
    private var screenConfig = CinemaScreenConfig()
    private var stereoConfig = StereoConfig()
    private var screenAspectRatio = 16f / 9f
    private var projectionMatrix = MatrixMath.identity()
    private var leftEyeMatrix = MatrixMath.identity()
    private var rightEyeMatrix = MatrixMath.identity()
    private var recenterTransform = MatrixMath.identity()
    private var contentMapping = ContentMapping()
    private var videoTextureUniform = -1
    private var textureTransformUniform = -1
    private var projectionUniform = -1
    private var activeScaleUniform = -1
    private var sampleScaleUniform = -1
    private var brightnessUniform = -1
    private var contrastUniform = -1
    private var compositeTextureUniform = -1
    private var renderedFrames = 0L
    private var droppedFrames = 0L

    fun setSurface(surface: Surface, width: Int, height: Int) {
        releaseGl()
        this.surface = surface
        this.width = width
        this.height = height
        updateCameraMatrices()
        startIfReady()
    }

    fun resize(width: Int, height: Int) {
        this.width = width
        this.height = height
        updateCameraMatrices()
        releaseEyeTargets()
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
        rebuildScreenGeometry()
        videoSampler?.setDefaultBufferSize(width, height)
    }

    fun setCinemaScreenConfig(config: CinemaScreenConfig) {
        if (screenConfig == config) return
        screenConfig = config
        rebuildScreenGeometry()
    }

    fun setStereoConfig(config: StereoConfig) {
        if (stereoConfig == config) return
        val targetsChanged = stereoConfig.renderMode != config.renderMode || !config.enabled
        stereoConfig = config
        updateCameraMatrices()
        if (targetsChanged) releaseEyeTargets()
    }

    fun recenter() {
        recenterTransform = MatrixMath.identity()
        updateCameraMatrices()
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
                projectionUniform = GLES30.glGetUniformLocation(shaderId, "uProjection")
                activeScaleUniform = GLES30.glGetUniformLocation(shaderId, "uActiveScale")
                sampleScaleUniform = GLES30.glGetUniformLocation(shaderId, "uSampleScale")
                brightnessUniform = GLES30.glGetUniformLocation(shaderId, "uBrightness")
                contrastUniform = GLES30.glGetUniformLocation(shaderId, "uContrast")
                compositeShader = ShaderProgram(COMPOSITE_VERTEX_SHADER, COMPOSITE_FRAGMENT_SHADER)
                compositeTextureUniform = GLES30.glGetUniformLocation(
                    checkNotNull(compositeShader).id,
                    "uTexture",
                )
                compositeMesh = Mesh.screenQuad()
                updateCameraMatrices()
                rebuildScreenGeometry()
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
        val sampler = videoSampler
        sampler?.updateTextureIfNeeded()
        if (stereoConfig.enabled) {
            renderStereo(shader, sampler)
        } else {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, width, height)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            renderScene(shader, sampler, projectionMatrix)
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
            compositeMesh?.release()
            compositeShader?.release()
            videoShader?.release()
        }
        releaseEyeTargets()
        videoSampler = null
        diagnosticOverlay = null
        mesh = null
        compositeMesh = null
        videoShader = null
        compositeShader = null
        videoTextureUniform = -1
        textureTransformUniform = -1
        projectionUniform = -1
        activeScaleUniform = -1
        sampleScaleUniform = -1
        brightnessUniform = -1
        contrastUniform = -1
        compositeTextureUniform = -1
        egl.release()
    }

    private fun updateCameraMatrices() {
        if (width <= 0 || height <= 0) return
        projectionMatrix = MatrixMath.perspective(
            verticalFieldOfViewDegrees = stereoConfig.fieldOfViewDegrees,
            aspectRatio = width.toFloat() / height,
            nearPlane = stereoConfig.nearPlane,
            farPlane = stereoConfig.farPlane,
        )
        val eyes = StereoCameraRig.eyeMatrices(
            config = stereoConfig,
            eyeAspectRatio = (width / 2f) / height,
            recenterTransform = recenterTransform,
        )
        leftEyeMatrix = eyes.left
        rightEyeMatrix = eyes.right
    }

    private fun rebuildScreenGeometry() {
        screenAspectRatio = ScreenGeometry.resolveAspectRatio(
            screenConfig.aspectRatioMode,
            videoWidth,
            videoHeight,
        )
        contentMapping = ScreenGeometry.contentMapping(
            screenConfig.cropMode,
            screenAspectRatio,
            videoWidth,
            videoHeight,
        )
        if (videoShader != null) {
            mesh?.release()
            mesh = Mesh.cinemaScreen(screenConfig, videoWidth, videoHeight)
        }
    }

    private fun renderStereo(shader: ShaderProgram, sampler: VideoFrameSampler?) {
        val eyeWidth = (width / 2).coerceAtLeast(1)
        if (stereoConfig.renderMode == StereoRenderMode.Offscreen) {
            ensureEyeTargets(eyeWidth, height)
            checkNotNull(leftEyeTarget).bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
            renderScene(shader, sampler, leftEyeMatrix)
            checkNotNull(rightEyeTarget).bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
            renderScene(shader, sampler, rightEyeMatrix)
            compositeEyes(eyeWidth)
        } else {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, eyeWidth, height)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            renderScene(shader, sampler, leftEyeMatrix)
            GLES30.glViewport(eyeWidth, 0, width - eyeWidth, height)
            renderScene(shader, sampler, rightEyeMatrix)
        }
    }

    private fun renderScene(
        shader: ShaderProgram,
        sampler: VideoFrameSampler?,
        viewProjection: FloatArray,
    ) {
        if (sampler != null && sampler.presentedFrames > 0) {
            shader.use()
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, sampler.textureId)
            GLES30.glUniform1i(videoTextureUniform, 0)
            GLES30.glUniformMatrix4fv(textureTransformUniform, 1, false, sampler.transformMatrix(), 0)
            GLES30.glUniformMatrix4fv(projectionUniform, 1, false, viewProjection, 0)
            GLES30.glUniform2f(activeScaleUniform, contentMapping.activeScaleX, contentMapping.activeScaleY)
            GLES30.glUniform2f(sampleScaleUniform, contentMapping.sampleScaleX, contentMapping.sampleScaleY)
            GLES30.glUniform1f(brightnessUniform, screenConfig.brightness)
            GLES30.glUniform1f(contrastUniform, screenConfig.contrast)
            mesh?.draw()
            GLES30.glBindTexture(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        }
        if (diagnosticMeshEnabled) {
            diagnosticOverlay?.draw()
        }
    }

    private fun ensureEyeTargets(eyeWidth: Int, eyeHeight: Int) {
        if (leftEyeTarget?.width == eyeWidth && leftEyeTarget?.height == eyeHeight) return
        releaseEyeTargets()
        leftEyeTarget = EyeFramebuffer(eyeWidth, eyeHeight)
        rightEyeTarget = EyeFramebuffer(eyeWidth, eyeHeight)
    }

    private fun compositeEyes(eyeWidth: Int) {
        val shader = compositeShader ?: return
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        shader.use()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glUniform1i(compositeTextureUniform, 0)
        GLES30.glViewport(0, 0, eyeWidth, height)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, leftEyeTarget?.textureId ?: 0)
        compositeMesh?.draw()
        GLES30.glViewport(eyeWidth, 0, width - eyeWidth, height)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, rightEyeTarget?.textureId ?: 0)
        compositeMesh?.draw()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    private fun releaseEyeTargets() {
        leftEyeTarget?.release()
        rightEyeTarget?.release()
        leftEyeTarget = null
        rightEyeTarget = null
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
            uniform mat4 uProjection;
            uniform mat4 uTextureTransform;
            out vec2 vTextureCoordinate;
            void main() {
                gl_Position = uProjection * vec4(aPosition, 1.0);
                vTextureCoordinate = (uTextureTransform * vec4(aTextureCoordinate, 0.0, 1.0)).xy;
            }
        """.trimIndent()

        val VIDEO_FRAGMENT_SHADER = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            uniform samplerExternalOES uVideoTexture;
            uniform vec2 uActiveScale;
            uniform vec2 uSampleScale;
            uniform float uBrightness;
            uniform float uContrast;
            in vec2 vTextureCoordinate;
            out vec4 fragmentColor;
            void main() {
                vec2 centered = vTextureCoordinate - vec2(0.5);
                vec2 activePosition = abs(centered) * 2.0;
                if (activePosition.x > uActiveScale.x || activePosition.y > uActiveScale.y) {
                    fragmentColor = vec4(0.0, 0.0, 0.0, 1.0);
                    return;
                }
                vec2 normalized = centered / uActiveScale;
                vec2 sampleCoordinate = normalized * uSampleScale + vec2(0.5);
                vec4 color = texture(uVideoTexture, sampleCoordinate);
                color.rgb = (color.rgb - vec3(0.5)) * uContrast + vec3(0.5);
                color.rgb *= uBrightness;
                fragmentColor = vec4(clamp(color.rgb, 0.0, 1.0), color.a);
            }
        """.trimIndent()

        val COMPOSITE_VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec2 aTextureCoordinate;
            out vec2 vTextureCoordinate;
            void main() {
                gl_Position = vec4(aPosition, 1.0);
                vTextureCoordinate = aTextureCoordinate;
            }
        """.trimIndent()

        val COMPOSITE_FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            uniform sampler2D uTexture;
            in vec2 vTextureCoordinate;
            out vec4 fragmentColor;
            void main() {
                fragmentColor = texture(uTexture, vTextureCoordinate);
            }
        """.trimIndent()
    }
}
