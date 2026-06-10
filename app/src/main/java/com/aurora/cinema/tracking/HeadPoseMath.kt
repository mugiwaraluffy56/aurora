package com.aurora.cinema.tracking

import com.aurora.cinema.render.MatrixMath
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class Quaternion(
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float,
)

object HeadPoseMath {
    fun quaternionFromRotationVector(values: FloatArray): Quaternion {
        require(values.size >= 3)
        val x = values[0]
        val y = values[1]
        val z = values[2]
        val w = if (values.size >= 4) {
            values[3]
        } else {
            sqrt(max(0f, 1f - x * x - y * y - z * z))
        }
        return normalize(Quaternion(x, y, z, w))
    }

    fun normalize(quaternion: Quaternion): Quaternion {
        val length = sqrt(
            quaternion.x * quaternion.x +
                quaternion.y * quaternion.y +
                quaternion.z * quaternion.z +
                quaternion.w * quaternion.w,
        )
        require(length > 0f)
        return Quaternion(
            x = quaternion.x / length,
            y = quaternion.y / length,
            z = quaternion.z / length,
            w = quaternion.w / length,
        )
    }

    fun slerp(from: Quaternion, to: Quaternion, amount: Float): Quaternion {
        val t = amount.coerceIn(0f, 1f)
        var target = to
        var dot = from.x * to.x + from.y * to.y + from.z * to.z + from.w * to.w
        if (dot < 0f) {
            target = Quaternion(-to.x, -to.y, -to.z, -to.w)
            dot = -dot
        }
        if (dot > 0.9995f) {
            return normalize(
                Quaternion(
                    x = from.x + t * (target.x - from.x),
                    y = from.y + t * (target.y - from.y),
                    z = from.z + t * (target.z - from.z),
                    w = from.w + t * (target.w - from.w),
                ),
            )
        }

        val theta = acos(dot)
        val sinTheta = sin(theta)
        val fromScale = sin((1f - t) * theta) / sinTheta
        val toScale = sin(t * theta) / sinTheta
        return Quaternion(
            x = from.x * fromScale + target.x * toScale,
            y = from.y * fromScale + target.y * toScale,
            z = from.z * fromScale + target.z * toScale,
            w = from.w * fromScale + target.w * toScale,
        )
    }

    fun viewMatrix(current: Quaternion, recenter: Quaternion): FloatArray {
        val relative = multiply(conjugate(current), recenter)
        return rotationMatrix(relative)
    }

    fun identityQuaternion(): Quaternion = Quaternion(0f, 0f, 0f, 1f)

    private fun multiply(left: Quaternion, right: Quaternion): Quaternion {
        return normalize(
            Quaternion(
                x = left.w * right.x + left.x * right.w + left.y * right.z - left.z * right.y,
                y = left.w * right.y - left.x * right.z + left.y * right.w + left.z * right.x,
                z = left.w * right.z + left.x * right.y - left.y * right.x + left.z * right.w,
                w = left.w * right.w - left.x * right.x - left.y * right.y - left.z * right.z,
            ),
        )
    }

    private fun conjugate(quaternion: Quaternion): Quaternion {
        return Quaternion(-quaternion.x, -quaternion.y, -quaternion.z, quaternion.w)
    }

    private fun rotationMatrix(quaternion: Quaternion): FloatArray {
        val x = quaternion.x
        val y = quaternion.y
        val z = quaternion.z
        val w = quaternion.w
        val xx = x * x
        val yy = y * y
        val zz = z * z
        val xy = x * y
        val xz = x * z
        val yz = y * z
        val wx = w * x
        val wy = w * y
        val wz = w * z
        return floatArrayOf(
            1f - 2f * (yy + zz), 2f * (xy + wz), 2f * (xz - wy), 0f,
            2f * (xy - wz), 1f - 2f * (xx + zz), 2f * (yz + wx), 0f,
            2f * (xz + wy), 2f * (yz - wx), 1f - 2f * (xx + yy), 0f,
            0f, 0f, 0f, 1f,
        )
    }
}

class HeadPoseSmoother(
    private val amount: Float = 0.18f,
) {
    private var current: Quaternion? = null

    fun reset() {
        current = null
    }

    fun smooth(input: Quaternion): Quaternion {
        val previous = current
        val next = if (previous == null) {
            input
        } else {
            HeadPoseMath.slerp(previous, input, amount)
        }
        current = next
        return next
    }
}

data class HeadPose(
    val viewMatrix: FloatArray = MatrixMath.identity(),
    val tracking: Boolean = false,
    val sensorName: String = "Unavailable",
)
