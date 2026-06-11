package com.aurora.cinema.ui

import android.content.pm.ActivityInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.cinema.app.AppContainer
import com.aurora.cinema.input.InputAction
import com.aurora.cinema.library.VideoItem
import com.aurora.cinema.playback.PlaybackState
import com.aurora.cinema.playback.SubtitleSettings
import com.aurora.cinema.render.*
import com.aurora.cinema.session.*
import com.aurora.cinema.settings.AppSettings
import com.aurora.cinema.tracking.AndroidHeadTracker
import com.aurora.cinema.tracking.HeadPose
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun PlayerScreen(
    selectedVideo: VideoItem?,
    playbackState: PlaybackState,
    appContainer: AppContainer,
    screenConfig: CinemaScreenConfig,
    stereoConfig: StereoConfig,
    videoProjection: VideoProjection,
    subtitleSettings: SubtitleSettings,
    comfortModeEnabled: Boolean,
    vrMode: Boolean,
    onVrModeChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val renderEngine = remember { RenderEngine() }
    val videoSurface by renderEngine.videoSurface.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var comfortState by remember { mutableStateOf(SessionComfortPolicy.evaluate(SessionEnvironment(headsetMode = vrMode, comfortModeEnabled = comfortModeEnabled))) }
    var headPose by remember { mutableStateOf(HeadPose()) }
    val headTracker = remember(context, renderEngine) {
        AndroidHeadTracker(context.applicationContext) { pose ->
            headPose = pose
            renderEngine.setHeadPose(pose.viewMatrix)
            renderEngine.setAngularVelocity(pose.angularVelocityRadsPerSec)
        }
    }

    LaunchedEffect(videoSurface, appContainer.playerController) { appContainer.playerController.setVideoSurface(videoSurface) }
    LaunchedEffect(appContainer.hardwareInputController, headTracker.available) {
        appContainer.hardwareInputController.actions.collect { action ->
            if (action == InputAction.Recenter) { if (headTracker.available) headTracker.recenter() else renderEngine.recenter() }
        }
    }
    LaunchedEffect(playbackState.videoWidth, playbackState.videoHeight) { renderEngine.setVideoSize(playbackState.videoWidth, playbackState.videoHeight) }
    LaunchedEffect(videoProjection) { renderEngine.setVideoProjection(videoProjection) }
    LaunchedEffect(appContainer.settingsRepository, appContainer) {
        appContainer.settingsRepository.settings.collect { settings -> renderEngine.setTheatreSceneConfig(settings.theatreSceneConfig) }
    }
    LaunchedEffect(vrMode, comfortModeEnabled) {
        while (vrMode) {
            comfortState = SessionComfortPolicy.evaluate(AndroidSessionEnvironment.read(context = context, headsetMode = true, comfortModeEnabled = comfortModeEnabled))
            delay(30_000L)
        }
        comfortState = SessionComfortPolicy.evaluate(SessionEnvironment(headsetMode = false, comfortModeEnabled = comfortModeEnabled))
    }
    LaunchedEffect(vrMode, comfortState.saveProgressIntervalMs) {
        while (vrMode) { delay(comfortState.saveProgressIntervalMs); appContainer.playerController.saveProgress() }
    }
    LaunchedEffect(screenConfig, comfortState.brightnessLimit) {
        renderEngine.setCinemaScreenConfig(screenConfig.copy(brightness = screenConfig.brightness.coerceAtMost(comfortState.brightnessLimit)))
    }
    LaunchedEffect(stereoConfig, vrMode) { renderEngine.setStereoConfig(stereoConfig.copy(enabled = vrMode)) }
    LaunchedEffect(subtitleSettings) { appContainer.playerController.setSubtitleSettings(subtitleSettings) }
    LaunchedEffect(vrMode) {
        if (vrMode) headTracker.start()
        else { headTracker.stop(); renderEngine.setHeadPose(MatrixMath.identity()) }
    }

    // Intercept system back: in VR → exit VR (not the app), in 2D → go back to details
    BackHandler(enabled = vrMode) { onVrModeChanged(false) }

    VrSystemUiEffect(active = vrMode)

    DisposableEffect(lifecycleOwner, renderEngine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { renderEngine.resume(); if (vrMode) headTracker.start() }
                Lifecycle.Event.ON_PAUSE -> { appContainer.playerController.saveProgress(); headTracker.stop(); renderEngine.pause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) renderEngine.resume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            appContainer.playerController.saveProgress(); headTracker.stop()
            appContainer.playerController.setVideoSurface(null); renderEngine.release(); onVrModeChanged(false)
        }
    }

    val activeVideo = playbackState.selectedVideo ?: selectedVideo
    var vrControlsLocked by rememberSaveable { mutableStateOf(false) }
    var vrScreenConfig by remember { mutableStateOf(screenConfig) }
    LaunchedEffect(screenConfig) { vrScreenConfig = screenConfig }
    fun executeGazeTarget(target: VrGazeTarget) {
        when (target) {
            VrGazeTarget.Back -> appContainer.playerController.seekTo((playbackState.positionMs - 10_000L).coerceAtLeast(0L))
            VrGazeTarget.PlayPause -> {
                if (activeVideo != null && playbackState.selectedVideo == null) appContainer.playerController.select(activeVideo)
                if (playbackState.isPlaying) appContainer.playerController.pause() else appContainer.playerController.play()
            }
            VrGazeTarget.Forward -> appContainer.playerController.seekTo(playbackState.positionMs + 10_000L)
            VrGazeTarget.Timeline -> { val mid = playbackState.durationMs / 2L; if (mid > 0L) appContainer.playerController.seekTo(mid) }
            VrGazeTarget.Recenter -> if (headTracker.available) headTracker.recenter() else renderEngine.recenter()
            VrGazeTarget.ScreenSmaller -> { vrScreenConfig = vrScreenConfig.copy(widthMeters = (vrScreenConfig.widthMeters - 1f).coerceAtLeast(6f)); scope.launch { appContainer.settingsRepository.setCinemaScreenConfig(vrScreenConfig) } }
            VrGazeTarget.ScreenLarger -> { vrScreenConfig = vrScreenConfig.copy(widthMeters = (vrScreenConfig.widthMeters + 1f).coerceAtMost(30f)); scope.launch { appContainer.settingsRepository.setCinemaScreenConfig(vrScreenConfig) } }
            VrGazeTarget.LockControls -> vrControlsLocked = !vrControlsLocked
            VrGazeTarget.Exit -> onVrModeChanged(false)
            VrGazeTarget.None -> Unit
        }
    }

    var errorDismissed by remember { mutableStateOf(false) }

    // Single AuroraRenderView instance kept alive across 2D↔VR transitions.
    // Two separate AndroidView factories would tear down the GL surface on each switch,
    // invalidating ExoPlayer's output surface → MediaCodecVideoRenderer crash.
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = if (vrMode) Modifier.fillMaxSize()
                       else Modifier.fillMaxWidth().height(286.dp).padding(top = 98.dp),
            factory = { ctx -> AuroraRenderView(ctx, renderEngine = renderEngine) },
        )

        if (vrMode) {
            VrPlaybackOverlay(
                viewMatrix = headPose.viewMatrix, tracking = headPose.tracking, sensorName = headPose.sensorName,
                isPlaying = playbackState.isPlaying, progressLabel = "${playbackState.positionMs.timeLabel()} / ${playbackState.durationMs.timeLabel()}",
                subtitleText = playbackState.subtitleText, subtitleSettings = subtitleSettings,
                controlsLocked = vrControlsLocked, onControlsLockedChange = { vrControlsLocked = it }, onTarget = ::executeGazeTarget,
                screenConfig = vrScreenConfig,
                positionMs = playbackState.positionMs,
                durationMs = playbackState.durationMs.coerceAtLeast(1L),
                onSeek = { appContainer.playerController.seekTo(it) },
                onScreenWidthChange = { w ->
                    vrScreenConfig = vrScreenConfig.copy(widthMeters = w)
                    scope.launch { appContainer.settingsRepository.setCinemaScreenConfig(vrScreenConfig) }
                },
                onScreenDistanceChange = { d ->
                    vrScreenConfig = vrScreenConfig.copy(distanceMeters = d)
                    scope.launch { appContainer.settingsRepository.setCinemaScreenConfig(vrScreenConfig) }
                },
            )
            return@Box
        }

        // 2D player controls — render view already fills the top portion via Modifier
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(286.dp)) // space reserved for render view above
            Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 132.dp)) {
                playbackState.error?.let { err ->
                    if (!errorDismissed) Banner("Playback error", err.message, Icons.Default.Warning, BannerTone.Error, modifier = Modifier.padding(bottom = 12.dp))
                }
                Surface(color = GlassFill, shape = CardShape, border = BorderStroke(1.dp, GlassBorder), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        val pos = playbackState.positionMs; val dur = playbackState.durationMs.coerceAtLeast(1L)
                        Slider(value = pos.toFloat().coerceAtMost(dur.toFloat()), onValueChange = { appContainer.playerController.seekTo(it.toLong()) },
                            valueRange = 0f..dur.toFloat(), colors = SliderDefaults.colors(thumbColor = Color.White,
                                activeTrackColor = Color.White.copy(alpha = 0.92f), inactiveTrackColor = Color.White.copy(alpha = 0.16f)))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(pos.timeLabel(), fontSize = 12.sp, color = Label2); Spacer(Modifier.weight(1f))
                            Text("-${(dur - pos).coerceAtLeast(0L).timeLabel()}", fontSize = 12.sp, color = Label2)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    TBtn(Icons.Default.Stop, 44, glass = true) { appContainer.playerController.stop(); onBack() }
                    Spacer(Modifier.width(12.dp))
                    TBtn(Icons.Default.Replay10, 52, glass = true) { appContainer.playerController.seekTo((playbackState.positionMs - 10_000L).coerceAtLeast(0L)) }
                    Spacer(Modifier.width(12.dp))
                    TBtn(if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 66, main = true) {
                        if (activeVideo != null && playbackState.selectedVideo == null) appContainer.playerController.select(activeVideo)
                        if (playbackState.isPlaying) appContainer.playerController.pause() else appContainer.playerController.play()
                    }
                    Spacer(Modifier.width(12.dp))
                    TBtn(Icons.Default.Forward10, 52, glass = true) { appContainer.playerController.seekTo(playbackState.positionMs + 10_000L) }
                    Spacer(Modifier.width(12.dp))
                    TBtn(Icons.Default.Warning, 44, glass = true) { errorDismissed = !errorDismissed }
                }
                Spacer(Modifier.height(14.dp))
                GlassButton(text = "Enter VR", icon = Icons.Default.ViewInAr, onClick = { onVrModeChanged(true) })
                SectionHeader(title = "Subtitles & audio")
                GlassCard {
                    ToggleRow("Subtitles", playbackState.timedTextTracks.firstOrNull()?.label ?: "No embedded tracks",
                        iconTint = TealColor, leadingIcon = Icons.Default.ClosedCaption, checked = subtitleSettings.enabled,
                        onCheckedChange = { enabled -> scope.launch { appContainer.settingsRepository.setSubtitleSettings(subtitleSettings.copy(enabled = enabled)) } })
                    RowDivider()
                    var audioDelay by remember(subtitleSettings) { mutableStateOf(subtitleSettings.audioDelayMs.toFloat()) }
                    SliderRow("Audio delay", "${if (audioDelay.toInt() > 0) "+" else ""}${audioDelay.toInt()} ms",
                        audioDelay, -500f..500f, onValueChange = { audioDelay = it },
                        onValueChangeFinished = { scope.launch { appContainer.settingsRepository.setSubtitleSettings(subtitleSettings.copy(audioDelayMs = audioDelay.toLong())) } })
                }
                Spacer(Modifier.height(9.dp))
                Text("Position is saved automatically. Closing the app resumes here next time.",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), fontSize = 13.sp, color = Label3, lineHeight = 19.sp)
            }
        } // Column(fillMaxSize) wrapping Spacer + scrollable Column
        Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            NavBar(onBack = onBack, title = activeVideo?.displayName ?: "Player",
                actions = { NavButton(icon = Icons.Default.ClosedCaption, onClick = {}) })
        }
    }
}

@Composable
internal fun TBtn(icon: ImageVector, size: Int, main: Boolean = false, glass: Boolean = false, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(size.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        color = when { main -> Color.White.copy(alpha = 0.94f); glass -> GlassFill; else -> Color.Transparent },
        contentColor = if (main) BgColor else Label1, shape = CircleShape,
        border = if (glass) BorderStroke(1.dp, GlassBorder) else null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (main) BgColor else Label1,
                modifier = Modifier.size(if (main) 24.dp else if (size <= 44) 15.dp else 22.dp))
        }
    }
}
