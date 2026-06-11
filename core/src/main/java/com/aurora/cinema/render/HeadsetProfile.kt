package com.aurora.cinema.render

data class HeadsetProfile(
    val id: String = "default",
    val name: String = "Default headset",
    val ipdMeters: Float = 0.064f,
    val fovDegrees: Float = 90f,
    val screenToLensDistance: Float = 0.04f,
    val interLensDistance: Float = 0.064f,
    val verticalLensOffset: Float = 0f,
    val distortionK1: Float = 0.22f,
    val distortionK2: Float = 0.24f,
    val distortionK3: Float = 0f,
    val chromaticAberrationRed: Float = 0f,
    val chromaticAberrationBlue: Float = 0f,
) {
    fun clamped(): HeadsetProfile = copy(
        ipdMeters = ipdMeters.coerceIn(0.04f, 0.09f),
        fovDegrees = fovDegrees.coerceIn(60f, 110f),
        screenToLensDistance = screenToLensDistance.coerceIn(0.02f, 0.08f),
        interLensDistance = interLensDistance.coerceIn(0.04f, 0.09f),
        verticalLensOffset = verticalLensOffset.coerceIn(-0.2f, 0.2f),
        distortionK1 = distortionK1.coerceIn(-1f, 1f),
        distortionK2 = distortionK2.coerceIn(-1f, 1f),
        distortionK3 = distortionK3.coerceIn(-1f, 1f),
        chromaticAberrationRed = chromaticAberrationRed.coerceIn(-0.02f, 0.02f),
        chromaticAberrationBlue = chromaticAberrationBlue.coerceIn(-0.02f, 0.02f),
    )
}

data class DistortionSample(
    val radius: Float,
    val distortedRadius: Float,
)

object LensDistortion {
    fun distortRadius(radius: Float, profile: HeadsetProfile): Float {
        val r = radius.coerceAtLeast(0f)
        val r2 = r * r
        val scale = 1f +
            profile.distortionK1 * r2 +
            profile.distortionK2 * r2 * r2 +
            profile.distortionK3 * r2 * r2 * r2
        return r * scale
    }

    fun lookupTable(profile: HeadsetProfile, samples: Int = 32): List<DistortionSample> {
        require(samples >= 2)
        return List(samples) { index ->
            val radius = index.toFloat() / (samples - 1).toFloat()
            DistortionSample(radius, distortRadius(radius, profile))
        }
    }
}
