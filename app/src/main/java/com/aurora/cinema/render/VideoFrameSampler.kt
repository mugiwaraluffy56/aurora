package com.aurora.cinema.render

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.Matrix
import android.os.Handler
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class VideoFrameSampler(
    callbackHandler: Handler,
) {
    private val textureIds = IntArray(1)
    private val framePending = AtomicBoolean(false)
    private val availableFrames = AtomicLong(0L)
    private val transformMatrix = FloatArray(16)

    val textureId: Int
    val surfaceTexture: SurfaceTexture
    val surface: Surface
    var presentedFrames: Long = 0L
        private set

    init {
        Matrix.setIdentityM(transformMatrix, 0)
        GLES30.glGenTextures(1, textureIds, 0)
        textureId = textureIds[0]
        check(textureId != 0) { "Unable to allocate external video texture" }
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE,
        )
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener(
                {
                    availableFrames.incrementAndGet()
                    framePending.set(true)
                },
                callbackHandler,
            )
        }
        surface = Surface(surfaceTexture)
    }

    fun updateTextureIfNeeded(): Boolean {
        if (!framePending.compareAndSet(true, false)) return false
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(transformMatrix)
        presentedFrames += 1
        return true
    }

    fun transformMatrix(): FloatArray = transformMatrix

    fun availableFrameCount(): Long = availableFrames.get()

    fun setDefaultBufferSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            surfaceTexture.setDefaultBufferSize(width, height)
        }
    }

    fun release() {
        surface.release()
        surfaceTexture.release()
        GLES30.glDeleteTextures(1, textureIds, 0)
    }
}
