package com.aurora.cinema.render

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class Mesh(
    vertices: FloatArray,
) {
    private val vertexArray = IntArray(1)
    private val vertexBuffer = IntArray(1)
    private val vertexCount = vertices.size / COMPONENTS_PER_VERTEX

    init {
        val data = ByteBuffer.allocateDirect(vertices.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
            .apply { position(0) }

        GLES30.glGenVertexArrays(1, vertexArray, 0)
        GLES30.glGenBuffers(1, vertexBuffer, 0)
        GLES30.glBindVertexArray(vertexArray[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBuffer[0])
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertices.size * Float.SIZE_BYTES,
            data,
            GLES30.GL_STATIC_DRAW,
        )
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(
            0,
            POSITION_COMPONENTS,
            GLES30.GL_FLOAT,
            false,
            COMPONENTS_PER_VERTEX * Float.SIZE_BYTES,
            0,
        )
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(
            1,
            TEXTURE_COMPONENTS,
            GLES30.GL_FLOAT,
            false,
            COMPONENTS_PER_VERTEX * Float.SIZE_BYTES,
            POSITION_COMPONENTS * Float.SIZE_BYTES,
        )
        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glBindVertexArray(vertexArray[0])
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        GLES30.glDeleteBuffers(1, vertexBuffer, 0)
        GLES30.glDeleteVertexArrays(1, vertexArray, 0)
    }

    companion object {
        private const val POSITION_COMPONENTS = 3
        private const val TEXTURE_COMPONENTS = 2
        private const val COMPONENTS_PER_VERTEX = POSITION_COMPONENTS + TEXTURE_COMPONENTS

        fun screenQuad(): Mesh {
            return Mesh(
                floatArrayOf(
                    -1f, -1f, 0f, 0f, 0f,
                    1f, -1f, 0f, 1f, 0f,
                    1f, 1f, 0f, 1f, 1f,
                    -1f, -1f, 0f, 0f, 0f,
                    1f, 1f, 0f, 1f, 1f,
                    -1f, 1f, 0f, 0f, 1f,
                ),
            )
        }

        fun cinemaScreen(
            config: CinemaScreenConfig,
            videoWidth: Int,
            videoHeight: Int,
        ): Mesh {
            return Mesh(ScreenGeometry.buildMesh(config, videoWidth, videoHeight))
        }

        fun equirectangularSphere(projection: VideoProjection): Mesh {
            return Mesh(EquirectangularSphere.buildMesh(projection))
        }
    }
}
