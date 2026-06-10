package com.aurora.cinema.render

data class StereoConfig(
    val enabled: Boolean = false,
    val ipdMeters: Float = 0.064f,
    val fieldOfViewDegrees: Float = 90f,
    val nearPlane: Float = 0.1f,
    val farPlane: Float = 100f,
    val renderMode: StereoRenderMode = StereoRenderMode.Direct,
)

enum class StereoRenderMode(val label: String) {
    Direct("Direct"),
    Offscreen("Offscreen"),
}

data class StereoEyeMatrices(
    val left: FloatArray,
    val right: FloatArray,
)

object StereoCameraRig {
    fun eyeMatrices(
        config: StereoConfig,
        eyeAspectRatio: Float,
        recenterTransform: FloatArray = MatrixMath.identity(),
    ): StereoEyeMatrices {
        require(eyeAspectRatio > 0f)
        require(config.ipdMeters in 0.04f..0.09f)
        val projection = MatrixMath.perspective(
            verticalFieldOfViewDegrees = config.fieldOfViewDegrees,
            aspectRatio = eyeAspectRatio,
            nearPlane = config.nearPlane,
            farPlane = config.farPlane,
        )
        val halfIpd = config.ipdMeters / 2f
        val leftView = MatrixMath.multiply(
            MatrixMath.translation(halfIpd, 0f, 0f),
            recenterTransform,
        )
        val rightView = MatrixMath.multiply(
            MatrixMath.translation(-halfIpd, 0f, 0f),
            recenterTransform,
        )
        return StereoEyeMatrices(
            left = MatrixMath.multiply(projection, leftView),
            right = MatrixMath.multiply(projection, rightView),
        )
    }
}
