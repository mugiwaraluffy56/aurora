package com.aurora.cinema.render

import android.opengl.GLES30

internal class ShaderProgram(
    vertexSource: String,
    fragmentSource: String,
) {
    val id: Int

    init {
        val vertexShader = compile(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, vertexShader)
        GLES30.glAttachShader(id, fragmentShader)
        GLES30.glLinkProgram(id)
        val status = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        check(status[0] == GLES30.GL_TRUE) {
            "Program link failed: ${GLES30.glGetProgramInfoLog(id)}"
        }
    }

    fun use() {
        GLES30.glUseProgram(id)
    }

    fun release() {
        GLES30.glDeleteProgram(id)
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES30.GL_TRUE) {
            val message = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            "Shader compilation failed: $message"
        }
        return shader
    }
}
