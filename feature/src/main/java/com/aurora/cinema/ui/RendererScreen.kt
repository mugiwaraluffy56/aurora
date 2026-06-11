package com.aurora.cinema.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.cinema.app.AppContainer
import com.aurora.cinema.playback.PlaybackState
import com.aurora.cinema.render.*
import com.aurora.cinema.session.AndroidSessionEnvironment
import com.aurora.cinema.session.SessionEnvironment
import com.aurora.cinema.settings.AppSettings
import com.aurora.cinema.settings.AppSettingsRepository
import com.aurora.cinema.telemetry.PerformanceDiagnosticsPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun RendererScreen(
    settings: AppSettings,
    repository: AppSettingsRepository,
    appContainer: AppContainer,
    playbackState: PlaybackState,
    onBack: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val renderEngine = remember { RenderEngine() }
    val telemetry by renderEngine.telemetry.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val codecSummaries = remember { appContainer.codecCapabilityService.summarizeDeviceCodecs() }
    var stereoPreviewEnabled by rememberSaveable { mutableStateOf(false) }
    var sessionEnvironment by remember { mutableStateOf(SessionEnvironment(headsetMode = stereoPreviewEnabled, comfortModeEnabled = settings.comfortModeEnabled)) }
    var draftConfig by remember { mutableStateOf(settings.cinemaScreenConfig) }
    val diagnostics = remember(telemetry, playbackState, sessionEnvironment, codecSummaries) {
        PerformanceDiagnosticsPolicy.evaluate(renderTelemetry = telemetry, playbackState = playbackState, environment = sessionEnvironment, codecs = codecSummaries)
    }

    LaunchedEffect(settings.cinemaScreenConfig) { draftConfig = settings.cinemaScreenConfig }
    LaunchedEffect(draftConfig) { renderEngine.setCinemaScreenConfig(draftConfig) }
    LaunchedEffect(settings.stereoConfig, stereoPreviewEnabled) { renderEngine.setStereoConfig(settings.stereoConfig.copy(enabled = stereoPreviewEnabled)) }
    LaunchedEffect(settings.videoProjection) { renderEngine.setVideoProjection(settings.videoProjection) }
    LaunchedEffect(settings.theatreSceneConfig) { renderEngine.setTheatreSceneConfig(settings.theatreSceneConfig) }
    LaunchedEffect(settings.headsetProfile) { renderEngine.setHeadsetProfile(settings.headsetProfile) }
    LaunchedEffect(stereoPreviewEnabled, settings.comfortModeEnabled) {
        while (true) { sessionEnvironment = AndroidSessionEnvironment.read(context = context, headsetMode = stereoPreviewEnabled, comfortModeEnabled = settings.comfortModeEnabled); delay(30_000L) }
    }
    DisposableEffect(lifecycleOwner, renderEngine) {
        val observer = LifecycleEventObserver { _, event -> when (event) { Lifecycle.Event.ON_RESUME -> renderEngine.resume(); Lifecycle.Event.ON_PAUSE -> renderEngine.pause(); else -> Unit } }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) renderEngine.resume()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); renderEngine.pause() }
    }
    DisposableEffect(renderEngine) { onDispose { renderEngine.release() } }

    var previewMode by rememberSaveable { mutableStateOf(0) }
    val aspectModes = ScreenAspectRatio.entries
    val cropModes = ScreenCropMode.entries

    Box(modifier = Modifier.fillMaxSize().background(BodyBg)) {
        ScreenScroll(topPadding = 76) {
            GlassCard {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp).background(
                    Brush.radialGradient(colors = listOf(Color(0xFF1B2A4C), Color(0xFF0B101F)), radius = 360f))) {
                    AndroidView(modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.68f).aspectRatio(16f / 9f),
                        factory = { ctx -> AuroraRenderView(ctx, renderEngine = renderEngine) })
                    Surface(modifier = Modifier.align(Alignment.TopStart).padding(10.dp), color = TealColor.copy(alpha = 0.13f), shape = RoundedCornerShape(100.dp)) {
                        Text("Live preview", modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp), fontSize = 12.sp, color = TealColor, fontWeight = FontWeight.W600)
                    }
                }
                Segmented(options = listOf("Mono", "Stereo", "Mesh"), selectedIndex = previewMode,
                    onSelect = { idx -> previewMode = idx; stereoPreviewEnabled = (idx == 1); renderEngine.setDiagnosticMeshEnabled(idx == 2) },
                    modifier = Modifier.fillMaxWidth().padding(10.dp))
            }

            SectionHeader(title = "Screen geometry")
            GlassCard {
                ListRow("Aspect ratio", iconTint = AccentBlue, leadingIcon = Icons.Default.CropFree, value = "${draftConfig.aspectRatioMode.label} ›",
                    onClick = { val next = aspectModes[(aspectModes.indexOf(draftConfig.aspectRatioMode) + 1) % aspectModes.size]; draftConfig = draftConfig.copy(aspectRatioMode = next); scope.launch { repository.setCinemaScreenConfig(draftConfig) } })
                RowDivider()
                ListRow("Crop mode", iconTint = AccentBlue, leadingIcon = Icons.Default.Fullscreen, value = "${draftConfig.cropMode.label} ›",
                    onClick = { val next = cropModes[(cropModes.indexOf(draftConfig.cropMode) + 1) % cropModes.size]; draftConfig = draftConfig.copy(cropMode = next); scope.launch { repository.setCinemaScreenConfig(draftConfig) } })
                RowDivider()
                ToggleRow("Curved screen", "Gentle 20° cylindrical curve", iconTint = AccentBlue, leadingIcon = Icons.Default.RemoveRedEye,
                    checked = draftConfig.curvatureRadiusMeters > 0f,
                    onCheckedChange = { enabled -> draftConfig = draftConfig.copy(curvatureRadiusMeters = if (enabled) 16f else 0f); scope.launch { repository.setCinemaScreenConfig(draftConfig) } })
                RowDivider()
                SliderRow("Screen distance", "%.1f m".format(draftConfig.distanceMeters), draftConfig.distanceMeters, 1f..6f,
                    onValueChange = { draftConfig = draftConfig.copy(distanceMeters = it) }, onValueChangeFinished = { scope.launch { repository.setCinemaScreenConfig(draftConfig) } })
                RowDivider(startIndent = 16)
                SliderRow("Screen width", "%.1f m".format(draftConfig.widthMeters), draftConfig.widthMeters, 1f..8f,
                    onValueChange = { draftConfig = draftConfig.copy(widthMeters = it) }, onValueChangeFinished = { scope.launch { repository.setCinemaScreenConfig(draftConfig) } })
                RowDivider(startIndent = 16)
                SliderRow("Vertical offset", "%.1f m".format(draftConfig.verticalOffsetMeters), draftConfig.verticalOffsetMeters, -2f..2f,
                    onValueChange = { draftConfig = draftConfig.copy(verticalOffsetMeters = it) }, onValueChangeFinished = { scope.launch { repository.setCinemaScreenConfig(draftConfig) } })
                RowDivider(startIndent = 16)
                SliderRow("Tilt", "${draftConfig.tiltDegrees.toInt()}°", draftConfig.tiltDegrees, -30f..30f,
                    onValueChange = { draftConfig = draftConfig.copy(tiltDegrees = it) }, onValueChangeFinished = { scope.launch { repository.setCinemaScreenConfig(draftConfig) } })
            }

            SectionHeader(title = "Picture")
            GlassCard {
                SliderRow("Brightness", "${(draftConfig.brightness * 100).toInt()}%", draftConfig.brightness, 0.2f..1.6f,
                    onValueChange = { draftConfig = draftConfig.copy(brightness = it) }, onValueChangeFinished = { scope.launch { repository.setCinemaScreenConfig(draftConfig) } })
                RowDivider(startIndent = 16)
                SliderRow("Contrast", "${(draftConfig.contrast * 100).toInt()}%", draftConfig.contrast, 0.2f..1.6f,
                    onValueChange = { draftConfig = draftConfig.copy(contrast = it) }, onValueChangeFinished = { scope.launch { repository.setCinemaScreenConfig(draftConfig) } })
            }

            SectionHeader(title = "Performance")
            GlassCard {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
                    val rows = listOf(
                        listOf(Triple("FRAME RATE", "%.1f".format(telemetry.framesPerSecond), "fps"), Triple("FRAME TIME", "%.1f".format(telemetry.averageFrameTimeMs), "ms"), Triple("BACKLOG", diagnostics.estimatedVideoFrameBacklog.toString(), "frames")),
                        listOf(Triple("RENDERED", telemetry.renderedFrames.toString(), ""), Triple("EST. DROPS", telemetry.estimatedDroppedFrames.toString(), ""), Triple("MODE", diagnostics.mode.label, "")),
                    )
                    rows.forEachIndexed { idx, row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { (label, value, unit) ->
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, fontSize = 11.sp, color = Label2, letterSpacing = 0.2.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    if (label == "MODE") Pill(text = value, color = AmberColor)
                                    else Row(verticalAlignment = Alignment.Bottom) {
                                        Text(value, fontSize = 20.sp, fontWeight = FontWeight.W700,
                                            color = if (label == "EST. DROPS" && telemetry.estimatedDroppedFrames > 0) AmberColor else Label1)
                                        if (unit.isNotEmpty()) { Spacer(modifier = Modifier.width(4.dp)); Text(unit, fontSize = 12.sp, color = Label2, fontWeight = FontWeight.W500) }
                                    }
                                }
                            }
                        }
                        if (idx < rows.lastIndex) Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (diagnostics.warning != null) Banner("Degradation policy active", diagnostics.warning ?: "", Icons.Default.Warning, BannerTone.Warn)

            SectionHeader(title = "Renderer")
            GlassCard {
                NoIconRow("GL renderer", telemetry.glRenderer.ifBlank { "Waiting" }); RowDivider(startIndent = 16)
                NoIconRow("GL version", telemetry.glVersion.ifBlank { "Waiting" }); RowDivider(startIndent = 16)
                ListRow("Release profiler checklist", "${diagnostics.profilerChecklist.size} checks tracked", link = true, onClick = {})
            }
        }
        Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) { NavBar(onBack = onBack, title = "Cinema & Renderer") }
    }
}
