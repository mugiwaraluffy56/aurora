package com.aurora.cinema.render

internal interface RenderOverlay {
    fun draw()
    fun release()
}

internal class DiagnosticOverlay : RenderOverlay {
    private val shader = ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    private val mesh = Mesh.screenQuad()

    override fun draw() {
        shader.use()
        mesh.draw()
    }

    override fun release() {
        mesh.release()
        shader.release()
    }

    private companion object {
        val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec2 aTextureCoordinate;
            void main() {
                gl_Position = vec4(aPosition * vec3(0.92, 0.92, 1.0), 1.0);
            }
        """.trimIndent()

        val FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            out vec4 fragmentColor;
            void main() {
                fragmentColor = vec4(0.0, 0.45, 0.65, 0.22);
            }
        """.trimIndent()
    }
}
