package com.aurora.cinema.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aurora.cinema.app.AppContainer
import com.aurora.cinema.core.nativebridge.NativeCore
import com.aurora.cinema.library.ImportResult
import com.aurora.cinema.library.LibrarySort
import com.aurora.cinema.library.OfflineLibraryRepository
import com.aurora.cinema.library.VideoAccessState
import com.aurora.cinema.library.VideoItem
import com.aurora.cinema.media.CodecSupportStatus
import com.aurora.cinema.playback.PlaybackState
import com.aurora.cinema.render.AuroraRenderView
import com.aurora.cinema.render.RenderEngine
import com.aurora.cinema.render.RenderState
import com.aurora.cinema.settings.AppSettings
import com.aurora.cinema.settings.AppSettingsRepository
import com.aurora.cinema.ui.theme.AuroraTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun AuroraApp(appContainer: AppContainer) {
    val settings by appContainer.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    val scope = rememberCoroutineScope()

    if (!settings.firstRunAcknowledged) {
        SafetyAcknowledgementScreen(
            onContinue = {
                scope.launch {
                    appContainer.settingsRepository.setFirstRunAcknowledged(true)
                }
            },
        )
        return
    }

    AuroraShell(
        appContainer = appContainer,
        settings = settings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuroraShell(
    appContainer: AppContainer,
    settings: AppSettings,
) {
    var selectedScreen by rememberSaveable { mutableStateOf(AppScreen.Library) }
    var selectedVideoId by rememberSaveable { mutableStateOf<Long?>(null) }
    val videos by appContainer.libraryRepository.videos.collectAsStateWithLifecycle(initialValue = emptyList())
    val playbackState by appContainer.playerController.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        appContainer.libraryRepository.refreshAccessChecks()
    }

    DisposableEffect(lifecycleOwner, appContainer.playerController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                appContainer.playerController.saveProgress()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedScreen.title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppScreen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        label = { Text(screen.navLabel) },
                        icon = { Text(screen.icon) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (selectedScreen) {
                AppScreen.Library -> LibraryScreen(
                    videos = videos,
                    repository = appContainer.libraryRepository,
                    onOpenDetails = { videoId ->
                        selectedVideoId = videoId
                        selectedScreen = AppScreen.VideoDetails
                    },
                )
                AppScreen.Player -> PlayerScreen(
                    selectedVideo = videos.firstOrNull { it.id == selectedVideoId },
                    playbackState = playbackState,
                    appContainer = appContainer,
                )
                AppScreen.VideoDetails -> VideoDetailsScreen(
                    video = videos.firstOrNull { it.id == selectedVideoId },
                    playbackState = playbackState,
                    onPlay = { video ->
                        selectedVideoId = video.id
                        appContainer.playerController.select(video)
                        selectedScreen = AppScreen.Player
                    },
                    onDelete = { videoId ->
                        selectedVideoId = null
                        selectedScreen = AppScreen.Library
                        scope.launch {
                            appContainer.libraryRepository.deleteLibraryEntry(videoId)
                        }
                    },
                )
                AppScreen.Settings -> SettingsScreen(
                    settings = settings,
                    repository = appContainer.settingsRepository,
                )
                AppScreen.Renderer -> RendererScreen()
                AppScreen.Calibration -> CalibrationScreen()
                AppScreen.About -> AboutScreen(appContainer = appContainer)
            }
        }
    }
}

@Composable
private fun SafetyAcknowledgementScreen(onContinue: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        ScreenColumn(verticalArrangement = Arrangement.Center) {
            Text(
                text = "Aurora",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Use seated in a safe space. Start with short sessions, keep brightness comfortable, and stop if you feel discomfort.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(onClick = onContinue) {
                Text("I understand")
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    videos: List<VideoItem>,
    repository: OfflineLibraryRepository,
    onOpenDetails: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.Recent) }
    var importMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                importMessage = repository.importVideo(uri).toMessage()
            }
        }
    }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                importMessage = repository.importFolder(uri).toMessage()
            }
        }
    }
    val filteredVideos = videos
        .filter { video -> video.displayName.contains(query, ignoreCase = true) }
        .sortedWith(
            when (sort) {
                LibrarySort.Recent -> compareByDescending<VideoItem> { it.lastSeenAt }
                LibrarySort.Title -> compareBy { it.displayName.lowercase() }
                LibrarySort.Duration -> compareByDescending { it.durationMs }
            },
        )

    ScreenColumn {
        ScreenHeader(
            title = "Library",
            subtitle = "Import local videos from Android's file picker and keep them available for offline viewing.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { filePicker.launch(videoMimeTypes) }) {
                Text("Import video")
            }
            OutlinedButton(onClick = { folderPicker.launch(null) }) {
                Text("Import folder")
            }
        }
        if (importMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = importMessage.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search videos") },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibrarySort.entries.forEach { option ->
                FilterChip(
                    selected = sort == option,
                    onClick = { sort = option },
                    label = { Text(option.label) },
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        if (filteredVideos.isEmpty()) {
            ActionPanel(
                title = if (videos.isEmpty()) "No videos yet" else "No matching videos",
                body = if (videos.isEmpty()) {
                    "Import a video file or a folder of videos to build your offline cinema library."
                } else {
                    "Try a different search term."
                },
                actionLabel = "Refresh access",
                onAction = {
                    scope.launch {
                        repository.refreshAccessChecks()
                        importMessage = "Library access checked"
                    }
                },
            )
        } else {
            filteredVideos.forEach { video ->
                VideoListItem(
                    video = video,
                    onOpenDetails = { onOpenDetails(video.id) },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PlayerScreen(
    selectedVideo: VideoItem?,
    playbackState: PlaybackState,
    appContainer: AppContainer,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val renderEngine = remember { RenderEngine() }
    val renderTelemetry by renderEngine.telemetry.collectAsStateWithLifecycle()
    val videoSurface by renderEngine.videoSurface.collectAsStateWithLifecycle()

    LaunchedEffect(videoSurface, appContainer.playerController) {
        appContainer.playerController.setVideoSurface(videoSurface)
    }

    LaunchedEffect(playbackState.videoWidth, playbackState.videoHeight) {
        renderEngine.setVideoSize(playbackState.videoWidth, playbackState.videoHeight)
    }

    DisposableEffect(lifecycleOwner, renderEngine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> renderEngine.resume()
                Lifecycle.Event.ON_PAUSE -> renderEngine.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            renderEngine.resume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            appContainer.playerController.setVideoSurface(null)
            renderEngine.release()
        }
    }

    ScreenColumn {
        ScreenHeader(
            title = "Player",
            subtitle = "OpenGL-rendered playback for the selected offline video.",
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16 / 9f),
            factory = { context ->
                AuroraRenderView(context, renderEngine = renderEngine)
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
        StatusRow(label = "Selected video", value = playbackState.selectedVideo?.displayName ?: selectedVideo?.displayName ?: "No video selected")
        StatusRow(label = "Position", value = "${playbackState.positionMs.timeLabel()} / ${playbackState.durationMs.timeLabel()}")
        StatusRow(
            label = "Video surface",
            value = if (renderTelemetry.videoSurfaceAttached) "Connected" else "Waiting",
        )
        StatusRow(
            label = "Video frames",
            value = "${renderTelemetry.videoFramesPresented} presented / ${renderTelemetry.videoFramesAvailable} available",
        )
        if (playbackState.error != null) {
            Text(
                text = playbackState.error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = selectedVideo != null || playbackState.selectedVideo != null,
                onClick = {
                    if (playbackState.selectedVideo == null && selectedVideo != null) {
                        appContainer.playerController.select(selectedVideo)
                    }
                    appContainer.playerController.play()
                },
            ) {
                Text("Play")
            }
            OutlinedButton(
                enabled = playbackState.selectedVideo != null,
                onClick = { appContainer.playerController.pause() },
            ) {
                Text("Pause")
            }
            OutlinedButton(
                enabled = playbackState.selectedVideo != null,
                onClick = { appContainer.playerController.seekTo((playbackState.positionMs - 10_000L).coerceAtLeast(0L)) },
            ) {
                Text("-10s")
            }
            OutlinedButton(
                enabled = playbackState.selectedVideo != null,
                onClick = { appContainer.playerController.seekTo(playbackState.positionMs + 10_000L) },
            ) {
                Text("+10s")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            enabled = playbackState.selectedVideo != null,
            onClick = { appContainer.playerController.stop() },
        ) {
            Text("Stop")
        }
        StatusRow(label = "VR entry", value = "Not connected yet")
    }
}

@Composable
private fun VideoDetailsScreen(
    video: VideoItem?,
    playbackState: PlaybackState,
    onPlay: (VideoItem) -> Unit,
    onDelete: (Long) -> Unit,
) {
    ScreenColumn {
        ScreenHeader(
            title = "Video Details",
            subtitle = "Selected video metadata and library access state.",
        )
        if (video == null) {
            Text(
                text = "No video selected.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            )
        } else {
            StatusRow(label = "Title", value = video.displayName)
            StatusRow(label = "Duration", value = video.durationLabel())
            StatusRow(label = "Resolution", value = video.resolutionLabel())
            StatusRow(label = "Mime type", value = video.mimeType.ifBlank { "Unknown" })
            StatusRow(label = "Video codec", value = video.probeResult.codecFamily)
            StatusRow(label = "Profile / level", value = video.probeResult.profileLevel)
            StatusRow(label = "Decoder", value = video.probeResult.decoderName.ifBlank { "Not found" })
            StatusRow(label = "Codec status", value = video.probeResult.supportStatus.label())
            StatusRow(label = "Frame rate", value = video.probeResult.frameRate.frameRateLabel())
            StatusRow(label = "Bitrate", value = video.probeResult.bitrate.bitrateLabel())
            StatusRow(label = "Bit depth", value = video.probeResult.bitDepth.bitDepthLabel())
            StatusRow(label = "HDR", value = video.probeResult.hdrFormat)
            StatusRow(label = "Source", value = video.sourceType)
            StatusRow(label = "Access", value = video.accessLabel())
            StatusRow(
                label = "Playback",
                value = if (playbackState.selectedVideo?.id == video.id) "Loaded" else "Not loaded",
            )
            if (video.probeResult.warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Warnings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.probeResult.warnings.joinToString(separator = "\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                enabled = video.accessState == VideoAccessState.Available,
                onClick = { onPlay(video) },
            ) {
                Text("Play video")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { onDelete(video.id) }) {
                Text("Remove from library")
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    repository: AppSettingsRepository,
) {
    val scope = rememberCoroutineScope()

    ScreenColumn {
        ScreenHeader(
            title = "Settings",
            subtitle = "Persistent viewing preferences for comfort and cinema setup.",
        )
        SettingSwitchRow(
            label = "Comfort mode",
            body = "Keep conservative defaults for brightness, motion, and session comfort.",
            checked = settings.comfortModeEnabled,
            onCheckedChange = { enabled ->
                scope.launch { repository.setComfortModeEnabled(enabled) }
            },
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Default screen distance: ${settings.defaultScreenDistanceMeters.toInt()} m",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Slider(
            value = settings.defaultScreenDistanceMeters,
            onValueChange = { distance ->
                scope.launch { repository.setDefaultScreenDistanceMeters(distance) }
            },
            valueRange = 3.0f..20.0f,
        )
    }
}

@Composable
private fun RendererScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val renderEngine = remember { RenderEngine() }
    val telemetry by renderEngine.telemetry.collectAsStateWithLifecycle()
    var diagnosticMeshEnabled by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, renderEngine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> renderEngine.resume()
                Lifecycle.Event.ON_PAUSE -> renderEngine.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            renderEngine.resume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            renderEngine.pause()
        }
    }

    DisposableEffect(renderEngine) {
        onDispose {
            renderEngine.release()
        }
    }

    ScreenColumn {
        ScreenHeader(
            title = "Render Diagnostics",
            subtitle = "Custom EGL and OpenGL ES rendering foundation for the cinema surface.",
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16 / 9f),
            factory = { context ->
                AuroraRenderView(context, renderEngine = renderEngine)
            },
            update = {
                renderEngine.setDiagnosticMeshEnabled(diagnosticMeshEnabled)
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
        SettingSwitchRow(
            label = "Diagnostic mesh",
            body = "Draw a flat test screen to verify shader, buffer, and swap-chain behavior.",
            checked = diagnosticMeshEnabled,
            onCheckedChange = { diagnosticMeshEnabled = it },
        )
        Spacer(modifier = Modifier.height(20.dp))
        StatusRow(label = "State", value = telemetry.state.label())
        StatusRow(
            label = "Surface",
            value = if (telemetry.surfaceWidth > 0) {
                "${telemetry.surfaceWidth}x${telemetry.surfaceHeight}"
            } else {
                "Waiting"
            },
        )
        StatusRow(label = "Frame rate", value = "%.1f fps".format(telemetry.framesPerSecond))
        StatusRow(label = "Frame time", value = "%.2f ms".format(telemetry.averageFrameTimeMs))
        StatusRow(label = "Rendered frames", value = telemetry.renderedFrames.toString())
        StatusRow(label = "Estimated drops", value = telemetry.estimatedDroppedFrames.toString())
        StatusRow(label = "GL renderer", value = telemetry.glRenderer.ifBlank { "Waiting" })
        StatusRow(label = "GL version", value = telemetry.glVersion.ifBlank { "Waiting" })
        telemetry.lastError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CalibrationScreen() {
    ScreenColumn {
        ScreenHeader(
            title = "Headset Calibration",
            subtitle = "Headset profile, IPD, FOV, and lens distortion controls will be added here.",
        )
        StatusRow(label = "Profile", value = "Default mobile headset")
        StatusRow(label = "IPD", value = "Not calibrated")
        StatusRow(label = "Distortion", value = "Not calibrated")
    }
}

@Composable
private fun AboutScreen(appContainer: AppContainer) {
    val displayInfo = appContainer.deviceDisplayInfo.read()
    val codecs = appContainer.codecCapabilityService.summarizeDeviceCodecs()

    ScreenColumn {
        ScreenHeader(
            title = "About / Diagnostics",
            subtitle = "App and runtime diagnostics for the Aurora cinema engine.",
        )
        StatusRow(label = "Package", value = appContainer.applicationContext.packageName)
        StatusRow(label = "Native core", value = NativeCore.engineName())
        StatusRow(label = "App shell", value = "Ready")
        StatusRow(label = "Device", value = displayInfo.deviceName)
        StatusRow(label = "Android", value = displayInfo.androidVersion)
        StatusRow(
            label = "Display",
            value = "${displayInfo.widthPixels}x${displayInfo.heightPixels} @ ${displayInfo.densityDpi} dpi",
        )
        if (displayInfo.refreshRate > 0f) {
            StatusRow(label = "Refresh", value = "${displayInfo.refreshRate.toInt()} Hz")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Video decoders",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        codecs.forEach { codec ->
            StatusRow(
                label = codec.codecFamily,
                value = if (codec.supported) codec.decoderName else "Not found",
            )
        }
    }
}

@Composable
private fun ScreenColumn(
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp),
            content = content,
        )
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
    )
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun ActionPanel(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onAction) {
        Text(actionLabel)
    }
}

@Composable
private fun VideoListItem(
    video: VideoItem,
    onOpenDetails: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${video.durationLabel()} • ${video.resolutionLabel()} • ${video.accessLabel()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.66f),
                )
            }
            OutlinedButton(onClick = onOpenDetails) {
                Text("Details")
            }
        }
        if (video.accessState == VideoAccessState.Missing) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Access is missing. Re-import this file or folder to reconnect it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
    Spacer(modifier = Modifier.height(12.dp))
}

private enum class AppScreen(
    val title: String,
    val navLabel: String,
    val icon: String,
) {
    Library("Library", "Library", "L"),
    Player("Player", "Player", "P"),
    VideoDetails("Video Details", "Details", "D"),
    Settings("Settings", "Settings", "S"),
    Renderer("Render Diagnostics", "Render", "R"),
    Calibration("Headset Calibration", "Calibrate", "C"),
    About("About / Diagnostics", "About", "A"),
}

private fun RenderState.label(): String {
    return name.replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase() else character.toString()
    }
}

private val videoMimeTypes = arrayOf(
    "video/*",
    "application/octet-stream",
    "application/x-matroska",
)

private fun ImportResult.toMessage(): String {
    return when {
        importedCount > 0 && skippedCount > 0 -> "Imported $importedCount video(s), skipped $skippedCount item(s)"
        importedCount > 0 -> "Imported $importedCount video(s)"
        else -> "No videos imported"
    }
}

private fun VideoItem.durationLabel(): String {
    if (durationMs <= 0L) return "Unknown duration"
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun VideoItem.resolutionLabel(): String {
    return if (width > 0 && height > 0) {
        "${width}x$height"
    } else {
        "Unknown resolution"
    }
}

private fun VideoItem.accessLabel(): String {
    return when (accessState) {
        VideoAccessState.Available -> if (persistedPermission) "Available offline" else "Available this session"
        VideoAccessState.Missing -> "Reconnect needed"
        VideoAccessState.Unknown -> "Not checked"
    }
}

private fun CodecSupportStatus.label(): String {
    return when (this) {
        CodecSupportStatus.Supported -> "Supported"
        CodecSupportStatus.Risky -> "Risky"
        CodecSupportStatus.Unsupported -> "Unsupported"
        CodecSupportStatus.Unknown -> "Unknown"
    }
}

private fun Float.frameRateLabel(): String {
    return if (this > 0f) {
        "%.2f fps".format(this)
    } else {
        "Unknown"
    }
}

private fun Long.bitrateLabel(): String {
    return if (this > 0L) {
        "%.1f Mbps".format(this / 1_000_000f)
    } else {
        "Unknown"
    }
}

private fun Int.bitDepthLabel(): String {
    return if (this > 0) "$this-bit" else "Unknown"
}

private fun Long.timeLabel(): String {
    if (this <= 0L) return "0:00"
    val totalSeconds = this / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Preview(showBackground = true)
@Composable
private fun AuroraAppPreview() {
    AuroraTheme {
        AuroraApp(
            appContainer = object : AppContainer {
                override val applicationContext = androidx.compose.ui.platform.LocalContext.current
                override val libraryRepository: OfflineLibraryRepository = object : OfflineLibraryRepository {
                    override val videos: Flow<List<VideoItem>> = MutableStateFlow(emptyList())

                    override suspend fun importVideo(uri: Uri, sourceType: String): ImportResult {
                        return ImportResult(importedCount = 0, skippedCount = 0)
                    }

                    override suspend fun importFolder(uri: Uri): ImportResult {
                        return ImportResult(importedCount = 0, skippedCount = 0)
                    }

                    override suspend fun refreshAccessChecks() = Unit

                    override suspend fun deleteLibraryEntry(videoId: Long) = Unit
                }
                override val playerController = object : com.aurora.cinema.playback.PlayerController {
                    override val state: kotlinx.coroutines.flow.StateFlow<PlaybackState> =
                        MutableStateFlow(PlaybackState())
                    override val mediaPlayer: androidx.media3.common.Player? = null

                    override fun select(video: VideoItem) = Unit

                    override fun play() = Unit

                    override fun pause() = Unit

                    override fun seekTo(positionMs: Long) = Unit

                    override fun stop() = Unit

                    override fun saveProgress() = Unit

                    override fun setVideoSurface(surface: android.view.Surface?) = Unit
                }
                override val mediaSessionController: com.aurora.cinema.playback.MediaSessionController
                    get() = error("Preview does not create a media session")
                override val codecCapabilityService = com.aurora.cinema.media.CodecCapabilityService()
                override val deviceDisplayInfo = com.aurora.cinema.media.DeviceDisplayInfo(applicationContext)
                override val settingsRepository = object : AppSettingsRepository {
                    override val settings: Flow<AppSettings> = MutableStateFlow(
                        AppSettings(firstRunAcknowledged = true),
                    )

                    override suspend fun setFirstRunAcknowledged(acknowledged: Boolean) = Unit

                    override suspend fun setComfortModeEnabled(enabled: Boolean) = Unit

                    override suspend fun setDefaultScreenDistanceMeters(distanceMeters: Float) = Unit
                }
            },
        )
    }
}
