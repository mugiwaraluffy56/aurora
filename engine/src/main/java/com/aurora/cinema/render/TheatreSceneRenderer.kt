package com.aurora.cinema.render

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class TheatreSceneRenderer {
    private val shader = ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    private val projectionUniform = GLES30.glGetUniformLocation(shader.id, "uProjection")
    private val vertexArray = IntArray(1)
    private val vertexBuffer = IntArray(1)
    private val vertices = TheatreSceneGeometry.build()
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
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, COMPONENTS_PER_VERTEX * Float.SIZE_BYTES, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, COMPONENTS_PER_VERTEX * Float.SIZE_BYTES, 3 * Float.SIZE_BYTES)
        GLES30.glBindVertexArray(0)
    }

    fun draw(viewProjection: FloatArray) {
        shader.use()
        GLES30.glUniformMatrix4fv(projectionUniform, 1, false, viewProjection, 0)
        GLES30.glBindVertexArray(vertexArray[0])
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        GLES30.glDeleteBuffers(1, vertexBuffer, 0)
        GLES30.glDeleteVertexArrays(1, vertexArray, 0)
        shader.release()
    }

    private companion object {
        private const val COMPONENTS_PER_VERTEX = 6

        val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec3 aColor;
            uniform mat4 uProjection;
            out vec3 vColor;
            void main() {
                gl_Position = uProjection * vec4(aPosition, 1.0);
                vColor = aColor;
            }
        """.trimIndent()

        val FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            in vec3 vColor;
            out vec4 fragmentColor;
            void main() {
                fragmentColor = vec4(vColor, 1.0);
            }
        """.trimIndent()
    }
}
