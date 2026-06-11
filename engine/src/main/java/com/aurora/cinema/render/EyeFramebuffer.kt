package com.aurora.cinema.render

import android.opengl.GLES30

internal class EyeFramebuffer(
    val width: Int,
    val height: Int,
) {
    private val framebuffer = IntArray(1)
    private val colorTexture = IntArray(1)
    private val depthBuffer = IntArray(1)

    val textureId: Int
        get() = colorTexture[0]

    init {
        GLES30.glGenFramebuffers(1, framebuffer, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer[0])

        GLES30.glGenTextures(1, colorTexture, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexture[0])
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA8,
            width,
            height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null,
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            colorTexture[0],
            0,
        )

        GLES30.glGenRenderbuffers(1, depthBuffer, 0)
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthBuffer[0])
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT16, width, height)
        GLES30.glFramebufferRenderbuffer(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_DEPTH_ATTACHMENT,
            GLES30.GL_RENDERBUFFER,
            depthBuffer[0],
        )
        check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) {
            "Unable to create stereo eye framebuffer"
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer[0])
        GLES30.glViewport(0, 0, width, height)
    }

    fun release() {
        GLES30.glDeleteRenderbuffers(1, depthBuffer, 0)
        GLES30.glDeleteTextures(1, colorTexture, 0)
        GLES30.glDeleteFramebuffers(1, framebuffer, 0)
    }
}
