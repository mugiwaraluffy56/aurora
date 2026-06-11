package com.aurora.cinema.render

data class HeadsetProfile(
    val id: String = "jiodive",
    val name: String = "JioDive",
    // JioDive fixed IPD ~62mm, Fresnel lenses, ~96° FOV
    // Paired with Samsung Galaxy S24 FE (2340×1080, 6.7")
    val ipdMeters: Float = 0.062f,
    val fovDegrees: Float = 96f,
    val screenToLensDistance: Float = 0.038f,
    val interLensDistance: Float = 0.062f,
    val verticalLensOffset: Float = 0f,
    // JioDive Fresnel lens distortion — stronger than basic Cardboard
    val distortionK1: Float = 0.33f,
    val distortionK2: Float = 0.28f,
    val distortionK3: Float = 0.02f,
    // Slight chromatic aberration from Fresnel lenses
    val chromaticAberrationRed: Float = 0.006f,
    val chromaticAberrationBlue: Float = -0.006f,
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
