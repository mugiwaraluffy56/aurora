package com.aurora.cinema.render

enum class TheatreSceneMode(
    val label: String,
) {
    Void("Black void"),
    Theatre("Theatre"),
}

data class TheatreSceneConfig(
    val mode: TheatreSceneMode = TheatreSceneMode.Void,
    val seatRow: Int = 3,
    val seatColumn: Int = 4,
)

object TheatreSceneGeometry {
    fun build(): FloatArray {
        val vertices = mutableListOf<Float>()

        fun quad(
            ax: Float,
            ay: Float,
            az: Float,
            bx: Float,
            by: Float,
            bz: Float,
            cx: Float,
            cy: Float,
            cz: Float,
            dx: Float,
            dy: Float,
            dz: Float,
            r: Float,
            g: Float,
            b: Float,
        ) {
            fun vertex(x: Float, y: Float, z: Float) {
                vertices += x
                vertices += y
                vertices += z
                vertices += r
                vertices += g
                vertices += b
            }
            vertex(ax, ay, az)
            vertex(bx, by, bz)
            vertex(cx, cy, cz)
            vertex(ax, ay, az)
            vertex(cx, cy, cz)
            vertex(dx, dy, dz)
        }

        quad(-22f, -5.2f, -4f, 22f, -5.2f, -4f, 26f, -5.2f, -32f, -26f, -5.2f, -32f, 0.055f, 0.058f, 0.068f)
        quad(-24f, -5.2f, -31f, 24f, -5.2f, -31f, 24f, 8f, -31f, -24f, 8f, -31f, 0.032f, 0.035f, 0.045f)
        quad(-24f, -5.2f, -4f, -24f, -5.2f, -31f, -24f, 8f, -31f, -24f, 8f, -4f, 0.04f, 0.042f, 0.052f)
        quad(24f, -5.2f, -31f, 24f, -5.2f, -4f, 24f, 8f, -4f, 24f, 8f, -31f, 0.04f, 0.042f, 0.052f)
        quad(-24f, 8f, -4f, 24f, 8f, -4f, 24f, 8f, -31f, -24f, 8f, -31f, 0.026f, 0.028f, 0.036f)

        for (row in 0 until SEAT_ROWS) {
            val z = -6.5f - row * 2.1f
            val y = -4.75f + row * 0.22f
            for (column in 0 until SEATS_PER_ROW) {
                val x = (column - (SEATS_PER_ROW - 1) / 2f) * 1.55f
                seat(vertices, x, y, z)
            }
        }

        return vertices.toFloatArray()
    }

    private fun seat(vertices: MutableList<Float>, centerX: Float, baseY: Float, centerZ: Float) {
        fun addBox(
            minX: Float,
            minY: Float,
            minZ: Float,
            maxX: Float,
            maxY: Float,
            maxZ: Float,
            color: FloatArray,
        ) {
            val (r, g, b) = color
            fun quad(a: FloatArray, b0: FloatArray, c: FloatArray, d: FloatArray) {
                listOf(a, b0, c, a, c, d).forEach { p ->
                    vertices += p[0]
                    vertices += p[1]
                    vertices += p[2]
                    vertices += r
                    vertices += g
                    vertices += b
                }
            }
            val p000 = floatArrayOf(minX, minY, minZ)
            val p001 = floatArrayOf(minX, minY, maxZ)
            val p010 = floatArrayOf(minX, maxY, minZ)
            val p011 = floatArrayOf(minX, maxY, maxZ)
            val p100 = floatArrayOf(maxX, minY, minZ)
            val p101 = floatArrayOf(maxX, minY, maxZ)
            val p110 = floatArrayOf(maxX, maxY, minZ)
            val p111 = floatArrayOf(maxX, maxY, maxZ)
            quad(p001, p101, p111, p011)
            quad(p100, p000, p010, p110)
            quad(p000, p001, p011, p010)
            quad(p101, p100, p110, p111)
            quad(p010, p011, p111, p110)
            quad(p000, p100, p101, p001)
        }

        val cushion = floatArrayOf(0.16f, 0.028f, 0.038f)
        addBox(centerX - 0.56f, baseY, centerZ - 0.44f, centerX + 0.56f, baseY + 0.28f, centerZ + 0.42f, cushion)
        addBox(centerX - 0.58f, baseY + 0.22f, centerZ + 0.28f, centerX + 0.58f, baseY + 1.1f, centerZ + 0.52f, cushion)
    }

    private const val SEAT_ROWS = 4
    private const val SEATS_PER_ROW = 9
}
