package com.aurora.cinema.render.nativebridge

import com.aurora.cinema.render.MatrixMath

object NativeMath {
    init {
        runCatching { System.loadLibrary("aurora_native") }
    }

    fun multiply(left: FloatArray, right: FloatArray): FloatArray {
        require(left.size == MATRIX_SIZE && right.size == MATRIX_SIZE)
        return runCatching { multiplyMatrices(left, right) }
            .getOrElse { MatrixMath.multiply(left, right) }
    }

    private external fun multiplyMatrices(left: FloatArray, right: FloatArray): FloatArray

    private const val MATRIX_SIZE = 16
}
