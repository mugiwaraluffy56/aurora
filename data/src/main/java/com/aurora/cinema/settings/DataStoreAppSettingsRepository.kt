package com.aurora.cinema.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aurora.cinema.input.HardwareInputSettings
import com.aurora.cinema.input.InputAction
import com.aurora.cinema.input.InputBinding
import com.aurora.cinema.input.PhysicalInput
import com.aurora.cinema.playback.SubtitleSettings
import com.aurora.cinema.render.CinemaScreenConfig
import com.aurora.cinema.render.HeadsetProfile
import com.aurora.cinema.render.ScreenAspectRatio
import com.aurora.cinema.render.ScreenCropMode
import com.aurora.cinema.render.StereoConfig
import com.aurora.cinema.render.StereoRenderMode
import com.aurora.cinema.render.StereoVideoLayout
import com.aurora.cinema.render.VideoProjection
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings",
)

class DataStoreAppSettingsRepository(
    context: Context,
) : AppSettingsRepository {
    private val dataStore = context.appSettingsDataStore

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppSettings(
                firstRunAcknowledged = preferences[Keys.firstRunAcknowledged] ?: false,
                comfortModeEnabled = preferences[Keys.comfortModeEnabled] ?: true,
                defaultScreenDistanceMeters = preferences[Keys.defaultScreenDistanceMeters] ?: 8.0f,
                cinemaScreenConfig = CinemaScreenConfig(
                    aspectRatioMode = preferences[Keys.screenAspectRatio]
                        .enumValueOrDefault(ScreenAspectRatio.Source),
                    distanceMeters = preferences[Keys.screenDistance] ?: 8f,
                    widthMeters = preferences[Keys.screenWidth] ?: 18f,
                    verticalOffsetMeters = preferences[Keys.screenVerticalOffset] ?: 0f,
                    curvatureRadiusMeters = preferences[Keys.screenCurvatureRadius] ?: 0f,
                    tiltDegrees = preferences[Keys.screenTilt] ?: 0f,
                    brightness = preferences[Keys.screenBrightness] ?: 1f,
                    contrast = preferences[Keys.screenContrast] ?: 1f,
                    cropMode = preferences[Keys.screenCropMode]
                        .enumValueOrDefault(ScreenCropMode.Fit),
                ),
                stereoConfig = StereoConfig(
                    enabled = preferences[Keys.stereoEnabled] ?: false,
                    ipdMeters = preferences[Keys.stereoIpd] ?: 0.064f,
                    fieldOfViewDegrees = preferences[Keys.stereoFov] ?: 90f,
                    renderMode = preferences[Keys.stereoRenderMode]
                        .enumValueOrDefault(StereoRenderMode.Direct),
                    videoLayout = preferences[Keys.stereoVideoLayout]
                        .enumValueOrDefault(StereoVideoLayout.Mono),
                    swapEyes = preferences[Keys.stereoSwapEyes] ?: false,
                ),
                videoProjection = preferences[Keys.videoProjection]
                    .enumValueOrDefault(VideoProjection.Cinema),
                headsetProfile = HeadsetProfile(
                    id = preferences[Keys.headsetId] ?: "default",
                    name = preferences[Keys.headsetName] ?: "Default headset",
                    ipdMeters = preferences[Keys.headsetIpd] ?: 0.064f,
                    fovDegrees = preferences[Keys.headsetFov] ?: 90f,
                    screenToLensDistance = preferences[Keys.screenToLensDistance] ?: 0.04f,
                    interLensDistance = preferences[Keys.interLensDistance] ?: 0.064f,
                    verticalLensOffset = preferences[Keys.verticalLensOffset] ?: 0f,
                    distortionK1 = preferences[Keys.distortionK1] ?: 0.22f,
                    distortionK2 = preferences[Keys.distortionK2] ?: 0.24f,
                    distortionK3 = preferences[Keys.distortionK3] ?: 0f,
                    chromaticAberrationRed = preferences[Keys.chromaticAberrationRed] ?: 0f,
                    chromaticAberrationBlue = preferences[Keys.chromaticAberrationBlue] ?: 0f,
                ).clamped(),
                subtitleSettings = SubtitleSettings(
                    enabled = preferences[Keys.subtitlesEnabled] ?: true,
                    sizeScale = preferences[Keys.subtitleSizeScale] ?: 1f,
                    verticalOffset = preferences[Keys.subtitleVerticalOffset] ?: 0f,
                    depthMeters = preferences[Keys.subtitleDepth] ?: 6f,
                    audioDelayMs = (preferences[Keys.audioDelayMs] ?: 0f).toLong(),
                ).clamped(),
                hardwareInputSettings = HardwareInputSettings(
                    bindings = preferences[Keys.hardwareInputBindings].toBindings(),
                    consumeVolumeKeys = preferences[Keys.consumeVolumeKeys] ?: false,
                ),
            )
        }

    override suspend fun setFirstRunAcknowledged(acknowledged: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.firstRunAcknowledged] = acknowledged
        }
    }

    override suspend fun setComfortModeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.comfortModeEnabled] = enabled
        }
    }

    override suspend fun setDefaultScreenDistanceMeters(distanceMeters: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.defaultScreenDistanceMeters] = distanceMeters.coerceIn(3.0f, 20.0f)
        }
    }

    override suspend fun setCinemaScreenConfig(config: CinemaScreenConfig) {
        dataStore.edit { preferences ->
            preferences[Keys.screenAspectRatio] = config.aspectRatioMode.name
            preferences[Keys.screenDistance] = config.distanceMeters.coerceIn(3f, 20f)
            preferences[Keys.screenWidth] = config.widthMeters.coerceIn(6f, 30f)
            preferences[Keys.screenVerticalOffset] = config.verticalOffsetMeters.coerceIn(-4f, 4f)
            preferences[Keys.screenCurvatureRadius] = config.curvatureRadiusMeters.coerceIn(0f, 40f)
            preferences[Keys.screenTilt] = config.tiltDegrees.coerceIn(-15f, 15f)
            preferences[Keys.screenBrightness] = config.brightness.coerceIn(0.5f, 1.5f)
            preferences[Keys.screenContrast] = config.contrast.coerceIn(0.5f, 1.5f)
            preferences[Keys.screenCropMode] = config.cropMode.name
        }
    }

    override suspend fun setStereoConfig(config: StereoConfig) {
        dataStore.edit { preferences ->
            preferences[Keys.stereoEnabled] = config.enabled
            preferences[Keys.stereoIpd] = config.ipdMeters.coerceIn(0.04f, 0.09f)
            preferences[Keys.stereoFov] = config.fieldOfViewDegrees.coerceIn(60f, 110f)
            preferences[Keys.stereoRenderMode] = config.renderMode.name
            preferences[Keys.stereoVideoLayout] = config.videoLayout.name
            preferences[Keys.stereoSwapEyes] = config.swapEyes
        }
    }

    override suspend fun setVideoProjection(projection: VideoProjection) {
        dataStore.edit { preferences ->
            preferences[Keys.videoProjection] = projection.name
        }
    }

    override suspend fun setHeadsetProfile(profile: HeadsetProfile) {
        val clamped = profile.clamped()
        dataStore.edit { preferences ->
            preferences[Keys.headsetId] = clamped.id
            preferences[Keys.headsetName] = clamped.name
            preferences[Keys.headsetIpd] = clamped.ipdMeters
            preferences[Keys.headsetFov] = clamped.fovDegrees
            preferences[Keys.screenToLensDistance] = clamped.screenToLensDistance
            preferences[Keys.interLensDistance] = clamped.interLensDistance
            preferences[Keys.verticalLensOffset] = clamped.verticalLensOffset
            preferences[Keys.distortionK1] = clamped.distortionK1
            preferences[Keys.distortionK2] = clamped.distortionK2
            preferences[Keys.distortionK3] = clamped.distortionK3
            preferences[Keys.chromaticAberrationRed] = clamped.chromaticAberrationRed
            preferences[Keys.chromaticAberrationBlue] = clamped.chromaticAberrationBlue
        }
    }

    override suspend fun setSubtitleSettings(settings: SubtitleSettings) {
        val clamped = settings.clamped()
        dataStore.edit { preferences ->
            preferences[Keys.subtitlesEnabled] = clamped.enabled
            preferences[Keys.subtitleSizeScale] = clamped.sizeScale
            preferences[Keys.subtitleVerticalOffset] = clamped.verticalOffset
            preferences[Keys.subtitleDepth] = clamped.depthMeters
            preferences[Keys.audioDelayMs] = clamped.audioDelayMs.toFloat()
        }
    }

    override suspend fun setHardwareInputSettings(settings: HardwareInputSettings) {
        dataStore.edit { preferences ->
            preferences[Keys.hardwareInputBindings] = settings.bindings.serialize()
            preferences[Keys.consumeVolumeKeys] = settings.consumeVolumeKeys
        }
    }

    private object Keys {
        val firstRunAcknowledged = booleanPreferencesKey("first_run_acknowledged")
        val comfortModeEnabled = booleanPreferencesKey("comfort_mode_enabled")
        val defaultScreenDistanceMeters = floatPreferencesKey("default_screen_distance_meters")
        val screenAspectRatio = stringPreferencesKey("screen_aspect_ratio")
        val screenDistance = floatPreferencesKey("screen_distance")
        val screenWidth = floatPreferencesKey("screen_width")
        val screenVerticalOffset = floatPreferencesKey("screen_vertical_offset")
        val screenCurvatureRadius = floatPreferencesKey("screen_curvature_radius")
        val screenTilt = floatPreferencesKey("screen_tilt")
        val screenBrightness = floatPreferencesKey("screen_brightness")
        val screenContrast = floatPreferencesKey("screen_contrast")
        val screenCropMode = stringPreferencesKey("screen_crop_mode")
        val stereoEnabled = booleanPreferencesKey("stereo_enabled")
        val stereoIpd = floatPreferencesKey("stereo_ipd")
        val stereoFov = floatPreferencesKey("stereo_fov")
        val stereoRenderMode = stringPreferencesKey("stereo_render_mode")
        val stereoVideoLayout = stringPreferencesKey("stereo_video_layout")
        val stereoSwapEyes = booleanPreferencesKey("stereo_swap_eyes")
        val videoProjection = stringPreferencesKey("video_projection")
        val headsetId = stringPreferencesKey("headset_id")
        val headsetName = stringPreferencesKey("headset_name")
        val headsetIpd = floatPreferencesKey("headset_ipd")
        val headsetFov = floatPreferencesKey("headset_fov")
        val screenToLensDistance = floatPreferencesKey("screen_to_lens_distance")
        val interLensDistance = floatPreferencesKey("inter_lens_distance")
        val verticalLensOffset = floatPreferencesKey("vertical_lens_offset")
        val distortionK1 = floatPreferencesKey("distortion_k1")
        val distortionK2 = floatPreferencesKey("distortion_k2")
        val distortionK3 = floatPreferencesKey("distortion_k3")
        val chromaticAberrationRed = floatPreferencesKey("chromatic_aberration_red")
        val chromaticAberrationBlue = floatPreferencesKey("chromatic_aberration_blue")
        val subtitlesEnabled = booleanPreferencesKey("subtitles_enabled")
        val subtitleSizeScale = floatPreferencesKey("subtitle_size_scale")
        val subtitleVerticalOffset = floatPreferencesKey("subtitle_vertical_offset")
        val subtitleDepth = floatPreferencesKey("subtitle_depth")
        val audioDelayMs = floatPreferencesKey("audio_delay_ms")
        val hardwareInputBindings = stringPreferencesKey("hardware_input_bindings")
        val consumeVolumeKeys = booleanPreferencesKey("consume_volume_keys")
    }
}

private inline fun <reified T : Enum<T>> String?.enumValueOrDefault(default: T): T {
    return enumValues<T>().firstOrNull { it.name == this } ?: default
}

private fun String?.toBindings(): List<InputBinding> {
    if (isNullOrBlank()) return HardwareInputSettings.defaultBindings
    val parsed = split(";").mapNotNull { encoded ->
        val parts = encoded.split("=")
        if (parts.size != 2) return@mapNotNull null
        val input = parts[0].enumValueOrNull<PhysicalInput>()
        val action = parts[1].enumValueOrNull<InputAction>()
        if (input != null && action != null) InputBinding(input, action) else null
    }
    return parsed.ifEmpty { HardwareInputSettings.defaultBindings }
}

private fun List<InputBinding>.serialize(): String {
    return joinToString(separator = ";") { binding ->
        "${binding.input.name}=${binding.action.name}"
    }
}

private inline fun <reified T : Enum<T>> String?.enumValueOrNull(): T? {
    return enumValues<T>().firstOrNull { it.name == this }
}
