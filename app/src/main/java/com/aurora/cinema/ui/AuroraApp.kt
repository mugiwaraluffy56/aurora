package com.aurora.cinema.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.aurora.cinema.render.CinemaScreenConfig
import com.aurora.cinema.render.RenderEngine
import com.aurora.cinema.render.RenderState
import com.aurora.cinema.render.ScreenAspectRatio
import com.aurora.cinema.render.ScreenCropMode
import com.aurora.cinema.render.StereoConfig
import com.aurora.cinema.render.StereoRenderMode
import com.aurora.cinema.settings.AppSettings
import com.aurora.cinema.settings.AppSettingsRepository
import com.aurora.cinema.tracking.AndroidHeadTracker
import com.aurora.cinema.tracking.HeadPose
import com.aurora.cinema.ui.theme.AuroraTheme
import com.aurora.cinema.ui.theme.AuroraGlass
import com.aurora.cinema.ui.theme.AuroraControlTrack
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
    var previousScreen by rememberSaveable { mutableStateOf(AppScreen.Library) }
    var selectedVideoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var vrMode by rememberSaveable { mutableStateOf(false) }
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

    val isSecondaryScreen = selectedScreen !in primaryScreens

    fun navigateTo(screen: AppScreen) {
        if (screen != selectedScreen) {
            previousScreen = selectedScreen
            selectedScreen = screen
        }
    }

    Scaffold(
        topBar = {
            if (!vrMode) TopAppBar(
                title = {
                    Text(
                        text = if (selectedScreen == AppScreen.Library) "Aurora" else selectedScreen.title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    if (isSecondaryScreen) {
                        IconButton(onClick = { selectedScreen = previousScreen }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.86f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            if (!isSecondaryScreen && !vrMode) {
                GlassNavigationBar(
                    selectedScreen = selectedScreen,
                    onSelect = ::navigateTo,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black,
                            Color(0xFF0A0A0B),
                            Color.Black,
                        ),
                    ),
                ),
        ) {
            LiquidBackdrop()
            AnimatedContent(
                targetState = selectedScreen,
                transitionSpec = {
                    (
                        fadeIn(animationSpec = tween(220)) +
                            scaleIn(initialScale = 0.985f, animationSpec = tween(260))
                        ).togetherWith(
                        fadeOut(animationSpec = tween(150)) +
                            scaleOut(targetScale = 0.995f, animationSpec = tween(150)),
                    )
                },
                label = "screen-transition",
            ) { screen ->
                when (screen) {
                    AppScreen.Library -> LibraryScreen(
                        videos = videos,
                        repository = appContainer.libraryRepository,
                        onOpenDetails = { videoId ->
                            selectedVideoId = videoId
                            navigateTo(AppScreen.VideoDetails)
                        },
                    )
                    AppScreen.Player -> PlayerScreen(
                        selectedVideo = videos.firstOrNull { it.id == selectedVideoId },
                        playbackState = playbackState,
                        appContainer = appContainer,
                        screenConfig = settings.cinemaScreenConfig,
                        stereoConfig = settings.stereoConfig,
                        vrMode = vrMode,
                        onVrModeChanged = { vrMode = it },
                        onBrowseLibrary = { navigateTo(AppScreen.Library) },
                    )
                    AppScreen.VideoDetails -> VideoDetailsScreen(
                        video = videos.firstOrNull { it.id == selectedVideoId },
                        playbackState = playbackState,
                        onPlay = { video ->
                            selectedVideoId = video.id
                            appContainer.playerController.select(video)
                            navigateTo(AppScreen.Player)
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
                        onOpenCalibration = { navigateTo(AppScreen.Calibration) },
                        onOpenAbout = { navigateTo(AppScreen.About) },
                    )
                    AppScreen.Renderer -> RendererScreen(
                        settings = settings,
                        repository = appContainer.settingsRepository,
                    )
                    AppScreen.Calibration -> CalibrationScreen(
                        settings = settings,
                        repository = appContainer.settingsRepository,
                    )
                    AppScreen.About -> AboutScreen(appContainer = appContainer)
                }
            }
        }
    }
}

@Composable
private fun SafetyAcknowledgementScreen(onContinue: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black,
                        Color(0xFF0A0A0B),
                        Color.Black,
                    ),
                ),
            ),
    ) {
        LiquidBackdrop()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 36.dp),
        ) {
            Text(
                text = "AURORA",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                GlassIconBadge(icon = Icons.Default.Tv, size = 112, pulsing = true)
            }
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Before you begin",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Use Aurora while seated in a clear space. Keep brightness comfortable and stop immediately if you feel discomfort.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                GlassPrimaryButton(
                    label = "Continue",
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun GlassNavigationBar(
    selectedScreen: AppScreen,
    onSelect: (AppScreen) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .heightIn(min = 92.dp),
        color = Color.White.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(56.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        shadowElevation = 18.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            primaryScreens.forEach { screen ->
                val selected = selectedScreen == screen
                val itemColor by animateColorAsState(
                    targetValue = if (selected) {
                        Color.White.copy(alpha = 0.18f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(durationMillis = 260),
                    label = "nav-item-color",
                )
                val itemScale by animateFloatAsState(
                    targetValue = if (selected) 1.02f else 0.96f,
                    animationSpec = tween(durationMillis = 260),
                    label = "nav-item-scale",
                )
                val itemElevation by animateDpAsState(
                    targetValue = if (selected) 10.dp else 0.dp,
                    animationSpec = tween(durationMillis = 260),
                    label = "nav-item-elevation",
                )
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 72.dp)
                        .graphicsLayer {
                            scaleX = itemScale
                            scaleY = itemScale
                        }
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(screen)
                        },
                    color = itemColor,
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = RoundedCornerShape(44.dp),
                    shadowElevation = itemElevation,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.navLabel,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = screen.navLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.LiquidBackdrop() {
    Box(
        modifier = Modifier
            .size(360.dp)
            .align(Alignment.TopEnd)
            .offset(x = 120.dp, y = 40.dp)
            .blur(90.dp)
            .background(Color.White.copy(alpha = 0.07f), CircleShape),
    )
    Box(
        modifier = Modifier
            .size(280.dp)
            .align(Alignment.CenterStart)
            .offset(x = (-120).dp, y = 80.dp)
            .blur(100.dp)
            .background(Color.White.copy(alpha = 0.08f), CircleShape),
    )
    Box(
        modifier = Modifier
            .size(240.dp)
            .align(Alignment.BottomEnd)
            .offset(x = 80.dp, y = (-40).dp)
            .blur(80.dp)
            .background(Color.White.copy(alpha = 0.05f), CircleShape),
    )
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        shadowElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun GlassIconBadge(
    icon: ImageVector,
    size: Int = 72,
    pulsing: Boolean = false,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glass-badge-pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glass-badge-pulse-scale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glass-badge-pulse-alpha",
    )
    Box(
        modifier = Modifier.size((size + 18).dp),
        contentAlignment = Alignment.Center,
    ) {
        if (pulsing) {
            Box(
                modifier = Modifier
                    .size(size.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = pulseAlpha
                    }
                    .blur(18.dp)
                    .background(Color.White, RoundedCornerShape((size / 3).dp)),
            )
        }
        Surface(
            modifier = Modifier.size(size.dp),
            color = Color.White.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape((size / 3).dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
            shadowElevation = 12.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size((size * 0.46f).dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun GlassPrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "glass-primary-scale",
    )
    val lift by animateDpAsState(
        targetValue = if (pressed && enabled) 4.dp else 12.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "glass-primary-lift",
    )
    val fill by animateColorAsState(
        targetValue = if (pressed && enabled) {
            Color.White.copy(alpha = 0.22f)
        } else {
            Color.White.copy(alpha = 0.16f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "glass-primary-fill",
    )
    Surface(
        modifier = modifier
            .heightIn(min = 56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.42f
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
        color = fill,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
        shadowElevation = lift,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null)
                Spacer(modifier = Modifier.size(10.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun GlassSecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "glass-secondary-scale",
    )
    val fill by animateColorAsState(
        targetValue = if (pressed && enabled) {
            Color.White.copy(alpha = 0.14f)
        } else {
            Color.White.copy(alpha = 0.08f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "glass-secondary-fill",
    )
    Surface(
        modifier = modifier
            .heightIn(min = 56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.42f
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
        color = fill,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null)
                Spacer(modifier = Modifier.size(10.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Your library",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = if (videos.isEmpty()) "Offline cinema, ready when you are" else "${videos.size} offline ${if (videos.size == 1) "title" else "titles"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (videos.isNotEmpty()) {
                IconButton(onClick = { filePicker.launch(videoMimeTypes) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add video")
                }
            }
        }
        if (importMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = importMessage.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        if (videos.isEmpty()) {
            EmptyLibrary(
                onImportVideo = { filePicker.launch(videoMimeTypes) },
                onImportFolder = { folderPicker.launch(null) },
            )
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null)
                },
                placeholder = { Text("Search library") },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibrarySort.entries.forEach { option ->
                FilterChip(
                    selected = sort == option,
                    onClick = { sort = option },
                    label = { Text(option.label) },
                    colors = glassChipColors(),
                )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            if (filteredVideos.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.VideoLibrary,
                    title = "No matches",
                    body = "Try a different title or sorting option.",
                )
            } else {
                filteredVideos.forEach { video ->
                    VideoListItem(
                        video = video,
                        onOpenDetails = { onOpenDetails(video.id) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerScreen(
    selectedVideo: VideoItem?,
    playbackState: PlaybackState,
    appContainer: AppContainer,
    screenConfig: CinemaScreenConfig,
    stereoConfig: StereoConfig,
    vrMode: Boolean,
    onVrModeChanged: (Boolean) -> Unit,
    onBrowseLibrary: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val renderEngine = remember { RenderEngine() }
    val renderTelemetry by renderEngine.telemetry.collectAsStateWithLifecycle()
    val videoSurface by renderEngine.videoSurface.collectAsStateWithLifecycle()
    var headPose by remember { mutableStateOf(HeadPose()) }
    val headTracker = remember(context, renderEngine) {
        AndroidHeadTracker(context.applicationContext) { pose ->
            headPose = pose
            renderEngine.setHeadPose(pose.viewMatrix)
        }
    }

    LaunchedEffect(videoSurface, appContainer.playerController) {
        appContainer.playerController.setVideoSurface(videoSurface)
    }

    LaunchedEffect(playbackState.videoWidth, playbackState.videoHeight) {
        renderEngine.setVideoSize(playbackState.videoWidth, playbackState.videoHeight)
    }

    LaunchedEffect(screenConfig) {
        renderEngine.setCinemaScreenConfig(screenConfig)
    }

    LaunchedEffect(stereoConfig, vrMode) {
        renderEngine.setStereoConfig(stereoConfig.copy(enabled = vrMode))
    }

    LaunchedEffect(vrMode) {
        if (vrMode) {
            headTracker.start()
        } else {
            headTracker.stop()
            renderEngine.setHeadPose(com.aurora.cinema.render.MatrixMath.identity())
        }
    }

    VrSystemUiEffect(active = vrMode)

    DisposableEffect(lifecycleOwner, renderEngine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    renderEngine.resume()
                    if (vrMode) headTracker.start()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    headTracker.stop()
                    renderEngine.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            renderEngine.resume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            headTracker.stop()
            appContainer.playerController.setVideoSurface(null)
            renderEngine.release()
            onVrModeChanged(false)
        }
    }

    DisposableEffect(lifecycleOwner, headTracker, vrMode) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (vrMode) headTracker.start()
                Lifecycle.Event.ON_PAUSE -> headTracker.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (vrMode) headTracker.stop()
        }
    }

    val activeVideo = playbackState.selectedVideo ?: selectedVideo
    if (vrMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context -> AuroraRenderView(context, renderEngine = renderEngine) },
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    IconButton(onClick = {
                        if (headTracker.available) headTracker.recenter() else renderEngine.recenter()
                    }) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = "Recenter view")
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    IconButton(onClick = { onVrModeChanged(false) }) {
                        Icon(Icons.Default.Close, contentDescription = "Exit VR")
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = if (headPose.tracking) "Head tracking active" else headPose.sensorName,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        ScreenColumn(contentPadding = 0.dp) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16 / 9f),
            factory = { context -> AuroraRenderView(context, renderEngine = renderEngine) },
        )
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            if (activeVideo == null) {
                EmptyState(
                    icon = Icons.Default.PlayCircle,
                    title = "Nothing queued",
                    body = "Choose an offline video from your library to start watching.",
                    actionLabel = "Browse library",
                    actionIcon = Icons.Default.VideoLibrary,
                    onAction = onBrowseLibrary,
                )
                GlassSecondaryButton(
                    label = "Enter VR preview",
                    icon = Icons.Default.Fullscreen,
                    onClick = { onVrModeChanged(true) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = activeVideo.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${playbackState.positionMs.timeLabel()} / ${playbackState.durationMs.timeLabel()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = playbackState.positionMs.toFloat().coerceAtMost(playbackState.durationMs.toFloat()),
                    onValueChange = { appContainer.playerController.seekTo(it.toLong()) },
                    valueRange = 0f..playbackState.durationMs.coerceAtLeast(1L).toFloat(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaybackControl(Icons.Default.Replay10, "Back 10 seconds") {
                        appContainer.playerController.seekTo((playbackState.positionMs - 10_000L).coerceAtLeast(0L))
                    }
                    PlaybackControl(
                        icon = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        description = if (playbackState.isPlaying) "Pause" else "Play",
                        emphasized = true,
                    ) {
                        if (playbackState.selectedVideo == null) {
                            appContainer.playerController.select(activeVideo)
                        }
                        if (playbackState.isPlaying) appContainer.playerController.pause() else appContainer.playerController.play()
                    }
                    PlaybackControl(Icons.Default.Forward10, "Forward 10 seconds") {
                        appContainer.playerController.seekTo(playbackState.positionMs + 10_000L)
                    }
                    PlaybackControl(Icons.Default.Stop, "Stop") {
                        appContainer.playerController.stop()
                    }
                }
                playbackState.error?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                GlassPrimaryButton(
                    label = "Enter VR",
                    icon = Icons.Default.Fullscreen,
                    onClick = { onVrModeChanged(true) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (renderTelemetry.videoSurfaceAttached) {
                        "Cinema surface connected · ${headTracker.sensorName}"
                    } else {
                        "Preparing cinema surface"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
        if (video == null) {
            EmptyState(
                icon = Icons.Default.VideoLibrary,
                title = "No video selected",
                body = "Open a title from your library to see its media details.",
            )
        } else {
            Text(text = video.displayName, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${video.durationLabel()}  ·  ${video.resolutionLabel()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            SectionLabel("MEDIA")
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
            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("LIBRARY")
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
            GlassPrimaryButton(
                label = "Play video",
                enabled = video.accessState == VideoAccessState.Available,
                onClick = { onPlay(video) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            GlassSecondaryButton(
                label = "Remove from library",
                onClick = { onDelete(video.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    repository: AppSettingsRepository,
    onOpenCalibration: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    ScreenColumn {
        SectionLabel("VIEWING")
        SettingSwitchRow(
            label = "Comfort mode",
            body = "Conservative brightness and motion defaults",
            checked = settings.comfortModeEnabled,
            onCheckedChange = { enabled ->
                scope.launch { repository.setComfortModeEnabled(enabled) }
            },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Screen distance",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "${settings.defaultScreenDistanceMeters.toInt()} metres",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = settings.defaultScreenDistanceMeters,
            onValueChange = { distance ->
                scope.launch { repository.setDefaultScreenDistanceMeters(distance) }
            },
            valueRange = 3.0f..20.0f,
            colors = glassSliderColors(),
        )
        Spacer(modifier = Modifier.height(24.dp))
        SectionLabel("HEADSET")
        NavigationRow(
            icon = Icons.Default.Tune,
            title = "Calibration",
            subtitle = "Lens, IPD and field of view",
            onClick = onOpenCalibration,
        )
        Spacer(modifier = Modifier.height(8.dp))
        NavigationRow(
            icon = Icons.Default.Info,
            title = "About and diagnostics",
            subtitle = "Device, display and decoder information",
            onClick = onOpenAbout,
        )
    }
}

@Composable
private fun RendererScreen(
    settings: AppSettings,
    repository: AppSettingsRepository,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val renderEngine = remember { RenderEngine() }
    val telemetry by renderEngine.telemetry.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var diagnosticMeshEnabled by rememberSaveable { mutableStateOf(false) }
    var stereoPreviewEnabled by rememberSaveable { mutableStateOf(false) }
    var draftConfig by remember { mutableStateOf(settings.cinemaScreenConfig) }

    LaunchedEffect(settings.cinemaScreenConfig) {
        draftConfig = settings.cinemaScreenConfig
    }

    LaunchedEffect(draftConfig) {
        renderEngine.setCinemaScreenConfig(draftConfig)
    }

    LaunchedEffect(settings.stereoConfig, stereoPreviewEnabled) {
        renderEngine.setStereoConfig(settings.stereoConfig.copy(enabled = stereoPreviewEnabled))
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
            renderEngine.pause()
        }
    }

    DisposableEffect(renderEngine) {
        onDispose {
            renderEngine.release()
        }
    }

    ScreenColumn {
        PageIntro(
            title = "Cinema engine",
            subtitle = "Live renderer health and display diagnostics.",
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
        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            label = "Stereo preview",
            body = "Render independent left and right eye views",
            checked = stereoPreviewEnabled,
            onCheckedChange = { stereoPreviewEnabled = it },
        )
        Spacer(modifier = Modifier.height(24.dp))
        SectionLabel("SCREEN PRESET")
        Text("Aspect ratio", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScreenAspectRatio.entries.forEach { mode ->
                FilterChip(
                    selected = draftConfig.aspectRatioMode == mode,
                    onClick = {
                        draftConfig = draftConfig.copy(aspectRatioMode = mode)
                        scope.launch { repository.setCinemaScreenConfig(draftConfig) }
                    },
                    label = { Text(mode.label) },
                    colors = glassChipColors(),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Content framing", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenCropMode.entries.forEach { mode ->
                FilterChip(
                    selected = draftConfig.cropMode == mode,
                    onClick = {
                        draftConfig = draftConfig.copy(cropMode = mode)
                        scope.launch { repository.setCinemaScreenConfig(draftConfig) }
                    },
                    label = { Text(mode.label) },
                    colors = glassChipColors(),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        SettingSwitchRow(
            label = "Curved screen",
            body = "Wrap the screen around the viewing position",
            checked = draftConfig.curvatureRadiusMeters > 0f,
            onCheckedChange = { enabled ->
                draftConfig = draftConfig.copy(curvatureRadiusMeters = if (enabled) 16f else 0f)
                scope.launch { repository.setCinemaScreenConfig(draftConfig) }
            },
        )
        if (draftConfig.curvatureRadiusMeters > 0f) {
            CinemaConfigSlider(
                label = "Curve radius",
                valueLabel = "${draftConfig.curvatureRadiusMeters.toInt()} m",
                value = draftConfig.curvatureRadiusMeters,
                range = 8f..40f,
                onValueChange = { draftConfig = draftConfig.copy(curvatureRadiusMeters = it) },
                onValueChangeFinished = {
                    scope.launch { repository.setCinemaScreenConfig(draftConfig) }
                },
            )
        }
        CinemaConfigSlider(
            label = "Distance",
            valueLabel = "${draftConfig.distanceMeters.toInt()} m",
            value = draftConfig.distanceMeters,
            range = 3f..20f,
            onValueChange = { draftConfig = draftConfig.copy(distanceMeters = it) },
            onValueChangeFinished = {
                scope.launch { repository.setCinemaScreenConfig(draftConfig) }
            },
        )
        CinemaConfigSlider(
            label = "Width",
            valueLabel = "${draftConfig.widthMeters.toInt()} m",
            value = draftConfig.widthMeters,
            range = 6f..30f,
            onValueChange = { draftConfig = draftConfig.copy(widthMeters = it) },
            onValueChangeFinished = {
                scope.launch { repository.setCinemaScreenConfig(draftConfig) }
            },
        )
        CinemaConfigSlider(
            label = "Vertical offset",
            valueLabel = "%.1f m".format(draftConfig.verticalOffsetMeters),
            value = draftConfig.verticalOffsetMeters,
            range = -4f..4f,
            onValueChange = { draftConfig = draftConfig.copy(verticalOffsetMeters = it) },
            onValueChangeFinished = {
                scope.launch { repository.setCinemaScreenConfig(draftConfig) }
            },
        )
        CinemaConfigSlider(
            label = "Tilt",
            valueLabel = "${draftConfig.tiltDegrees.toInt()}°",
            value = draftConfig.tiltDegrees,
            range = -15f..15f,
            onValueChange = { draftConfig = draftConfig.copy(tiltDegrees = it) },
            onValueChangeFinished = {
                scope.launch { repository.setCinemaScreenConfig(draftConfig) }
            },
        )
        CinemaConfigSlider(
            label = "Brightness",
            valueLabel = "${(draftConfig.brightness * 100).toInt()}%",
            value = draftConfig.brightness,
            range = 0.5f..1.5f,
            onValueChange = { draftConfig = draftConfig.copy(brightness = it) },
            onValueChangeFinished = {
                scope.launch { repository.setCinemaScreenConfig(draftConfig) }
            },
        )
        CinemaConfigSlider(
            label = "Contrast",
            valueLabel = "${(draftConfig.contrast * 100).toInt()}%",
            value = draftConfig.contrast,
            range = 0.5f..1.5f,
            onValueChange = { draftConfig = draftConfig.copy(contrast = it) },
            onValueChangeFinished = {
                scope.launch { repository.setCinemaScreenConfig(draftConfig) }
            },
        )
        Spacer(modifier = Modifier.height(24.dp))
        SectionLabel("LIVE TELEMETRY")
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
private fun CalibrationScreen(
    settings: AppSettings,
    repository: AppSettingsRepository,
) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(settings.stereoConfig) }

    LaunchedEffect(settings.stereoConfig) {
        draft = settings.stereoConfig
    }

    ScreenColumn {
        PageIntro(
            title = "Headset profile",
            subtitle = "Tune the stereo camera for your phone and headset.",
        )
        SectionLabel("STEREO CAMERA")
        CinemaConfigSlider(
            label = "Interpupillary distance",
            valueLabel = "${(draft.ipdMeters * 1000).toInt()} mm",
            value = draft.ipdMeters,
            range = 0.04f..0.09f,
            onValueChange = { draft = draft.copy(ipdMeters = it) },
            onValueChangeFinished = {
                scope.launch { repository.setStereoConfig(draft) }
            },
        )
        CinemaConfigSlider(
            label = "Field of view",
            valueLabel = "${draft.fieldOfViewDegrees.toInt()}°",
            value = draft.fieldOfViewDegrees,
            range = 60f..110f,
            onValueChange = { draft = draft.copy(fieldOfViewDegrees = it) },
            onValueChangeFinished = {
                scope.launch { repository.setStereoConfig(draft) }
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Eye rendering", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StereoRenderMode.entries.forEach { mode ->
                FilterChip(
                    selected = draft.renderMode == mode,
                    onClick = {
                        draft = draft.copy(renderMode = mode)
                        scope.launch { repository.setStereoConfig(draft) }
                    },
                    label = { Text(mode.label) },
                    colors = glassChipColors(),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        SectionLabel("NEXT CALIBRATION STAGE")
        StatusRow(label = "Lens distortion", value = "Phase 10")
    }
}

@Composable
private fun AboutScreen(appContainer: AppContainer) {
    val displayInfo = appContainer.deviceDisplayInfo.read()
    val codecs = appContainer.codecCapabilityService.summarizeDeviceCodecs()

    ScreenColumn {
        PageIntro(
            title = "Aurora",
            subtitle = "Offline mobile cinema runtime diagnostics.",
        )
        SectionLabel("APP")
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
        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel("VIDEO DECODERS")
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
    contentPadding: androidx.compose.ui.unit.Dp = 32.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp),
            content = content,
        )
    }
}

@Composable
private fun VrSystemUiEffect(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(active, view) {
        if (!active) {
            onDispose { }
        } else {
            val activity = view.context.findActivity()
            val previousOrientation = activity?.requestedOrientation
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, view).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            onDispose {
                activity?.window?.let { window ->
                    WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                }
                if (previousOrientation != null) {
                    activity.requestedOrientation = previousOrientation
                }
            }
        }
    }
}

@Composable
private fun PageIntro(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun EmptyLibrary(
    onImportVideo: () -> Unit,
    onImportFolder: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            EmptyState(
                icon = Icons.Default.VideoLibrary,
                title = "Build your offline cinema",
                body = "Add a local video or folder. Aurora keeps access on this device for playback without a network connection.",
                actionLabel = "Add video",
                onAction = onImportVideo,
            )
            GlassSecondaryButton(
                label = "Add folder",
                icon = Icons.Default.FolderOpen,
                onClick = onImportFolder,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    actionIcon: ImageVector = Icons.Default.Add,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassIconBadge(icon = icon, size = 88, pulsing = true)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(20.dp))
            GlassPrimaryButton(
                label = actionLabel,
                icon = actionIcon,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VideoListItem(
    video: VideoItem,
    onOpenDetails: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetails),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${video.durationLabel()}  ·  ${video.resolutionLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = AuroraControlTrack,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
private fun CinemaConfigSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(
            valueLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = range,
        colors = glassSliderColors(),
    )
}

@Composable
private fun glassSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    activeTickColor = Color.Transparent,
    inactiveTrackColor = AuroraControlTrack,
    inactiveTickColor = Color.Transparent,
)

@Composable
private fun glassChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.62f),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun NavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaybackControl(
    icon: ImageVector,
    description: String,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(12.dp),
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = description)
        }
    }
}

private enum class AppScreen(
    val title: String,
    val navLabel: String,
    val icon: ImageVector,
) {
    Library("Library", "Library", Icons.Default.VideoLibrary),
    Player("Player", "Player", Icons.Default.PlayCircle),
    Renderer("Cinema", "Cinema", Icons.Default.Tv),
    Settings("Settings", "Settings", Icons.Default.Settings),
    VideoDetails("Video details", "Details", Icons.Default.Info),
    Calibration("Calibration", "Calibration", Icons.Default.Tune),
    About("About", "About", Icons.Default.Info),
}

private val primaryScreens = listOf(
    AppScreen.Library,
    AppScreen.Player,
    AppScreen.Renderer,
    AppScreen.Settings,
)

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

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
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

                    override suspend fun setCinemaScreenConfig(config: CinemaScreenConfig) = Unit

                    override suspend fun setStereoConfig(config: StereoConfig) = Unit
                }
            },
        )
    }
}
