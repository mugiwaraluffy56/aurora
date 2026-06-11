package com.aurora.cinema.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
import com.aurora.cinema.core.nativebridge.NativeCore
import com.aurora.cinema.input.InputAction
import com.aurora.cinema.library.ImportResult
import com.aurora.cinema.library.LibrarySort
import com.aurora.cinema.library.OfflineLibraryRepository
import com.aurora.cinema.library.VideoAccessState
import com.aurora.cinema.library.VideoItem
import com.aurora.cinema.media.CodecSupportStatus
import com.aurora.cinema.playback.PlaybackState
import com.aurora.cinema.playback.SubtitleSettings
import com.aurora.cinema.render.AuroraRenderView
import com.aurora.cinema.render.CinemaScreenConfig
import com.aurora.cinema.render.RenderEngine
import com.aurora.cinema.render.ScreenAspectRatio
import com.aurora.cinema.render.ScreenCropMode
import com.aurora.cinema.render.StereoConfig
import com.aurora.cinema.render.StereoRenderMode
import com.aurora.cinema.render.VideoProjection
import com.aurora.cinema.session.AndroidSessionEnvironment
import com.aurora.cinema.session.SessionComfortPolicy
import com.aurora.cinema.session.SessionEnvironment
import com.aurora.cinema.settings.AppSettings
import com.aurora.cinema.settings.AppSettingsRepository
import com.aurora.cinema.telemetry.PerformanceDiagnosticsPolicy
import com.aurora.cinema.tracking.AndroidHeadTracker
import com.aurora.cinema.tracking.HeadPose
import com.aurora.cinema.ui.theme.AuroraTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// ============================================================================
// DESIGN TOKENS — translated from index.html :root
// ============================================================================
internal val BgColor = Color.Black
internal val BodyBg = Color.Black
internal val AccentBlue = Color(0xFF549BFF)
internal val GreenColor = Color(0xFF4CC38A)
internal val AmberColor = Color(0xFFE0A24E)
internal val RedColor = Color(0xFFE36A60)
internal val TealColor = Color(0xFF5BC0CE)
internal val VioletColor = Color(0xFF9A8CFF)
internal val SlateColor = Color(0xFF8E97A8)

internal val Label1 = Color.White.copy(alpha = 0.94f)
internal val Label2 = Color(0xFFE1E6F0).copy(alpha = 0.60f)
internal val Label3 = Color(0xFFE1E6F0).copy(alpha = 0.34f)
internal val Separator = Color.White.copy(alpha = 0.07f)

internal val GlassFill = Color.White.copy(alpha = 0.055f)
internal val GlassRaised = Color.White.copy(alpha = 0.09f)
internal val GlassBorder = Color.White.copy(alpha = 0.085f)
// Glass base — elevated surface simulating backdrop blur frosting against black bg.
internal val GlassBase = Color(0xFF21242E)

internal val CardShape = RoundedCornerShape(18.dp)
internal val ControlShape = RoundedCornerShape(13.dp)
private const val ScreenBottomChromePadding = 220

// Map: secondary screens route back to their owning primary tab (mirrors HTML's TAB_SCREENS).
internal val TAB_SCREENS: Map<AppScreen, AppScreen> = mapOf(
    AppScreen.Library to AppScreen.Library,
    AppScreen.Player to AppScreen.Player,
    AppScreen.Settings to AppScreen.Settings,
    AppScreen.VideoDetails to AppScreen.Library,
    AppScreen.Renderer to AppScreen.Settings,
    AppScreen.Calibration to AppScreen.Settings,
    AppScreen.About to AppScreen.Settings,
)

// ============================================================================
// ENTRY POINT
// ============================================================================
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
internal fun AuroraShell(
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
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isSecondaryScreen = selectedScreen !in primaryScreens
    var showNav by remember { mutableStateOf(false) }

    fun navigateTo(screen: AppScreen) {
        if (screen != selectedScreen) {
            previousScreen = selectedScreen
            selectedScreen = screen
        }
    }

    // Back gesture: secondary screens → go back; primary screens → do nothing (let system handle)
    BackHandler(enabled = isSecondaryScreen) {
        selectedScreen = previousScreen
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BodyBg),
        ) {
            if (selectedScreen == AppScreen.Player) {
                LiquidBackdrop()
            }

            // Screen-edge vignette — matches index.html dark corner depth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.65f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.55f),
                            ),
                        )
                    )
            )

            AnimatedContent(
                targetState = selectedScreen,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.985f, animationSpec = tween(260)))
                        .togetherWith(fadeOut(tween(150)) + scaleOut(targetScale = 0.995f, animationSpec = tween(150)))
                },
                label = "screen-transition",
            ) { screen ->
                when (screen) {
                    AppScreen.Library -> LibraryScreen(
                        videos = videos,
                        repository = appContainer.libraryRepository,
                        onOpenDetails = { id ->
                            selectedVideoId = id
                            navigateTo(AppScreen.VideoDetails)
                        },
                        onResume = { navigateTo(AppScreen.Player) },
                        onReviewSafety = {
                            scope.launch { appContainer.settingsRepository.setFirstRunAcknowledged(false) }
                        },
                    )
                    AppScreen.Player -> PlayerScreen(
                        selectedVideo = videos.firstOrNull { it.id == selectedVideoId },
                        playbackState = playbackState,
                        appContainer = appContainer,
                        screenConfig = settings.cinemaScreenConfig,
                        stereoConfig = settings.stereoConfig,
                        videoProjection = settings.videoProjection,
                        subtitleSettings = settings.subtitleSettings,
                        comfortModeEnabled = settings.comfortModeEnabled,
                        vrMode = vrMode,
                        onVrModeChanged = { vrMode = it },
                        onBack = { navigateTo(AppScreen.VideoDetails) },
                    )
                    AppScreen.VideoDetails -> VideoDetailsScreen(
                        video = videos.firstOrNull { it.id == selectedVideoId },
                        playbackState = playbackState,
                        onBack = { navigateTo(AppScreen.Library) },
                        onPlay = { v ->
                            selectedVideoId = v.id
                            appContainer.playerController.select(v)
                            navigateTo(AppScreen.Player)
                        },
                        onDelete = { id ->
                            selectedVideoId = null
                            selectedScreen = AppScreen.Library
                            scope.launch { appContainer.libraryRepository.deleteLibraryEntry(id) }
                        },
                        onRefresh = { scope.launch { appContainer.libraryRepository.refreshAccessChecks() } },
                    )
                    AppScreen.Settings -> SettingsScreen(
                        settings = settings,
                        repository = appContainer.settingsRepository,
                        onOpenCalibration = { navigateTo(AppScreen.Calibration) },
                        onOpenRenderer = { navigateTo(AppScreen.Renderer) },
                        onOpenAbout = { navigateTo(AppScreen.About) },
                        onReviewSafety = {
                            scope.launch { appContainer.settingsRepository.setFirstRunAcknowledged(false) }
                        },
                    )
                    AppScreen.Renderer -> RendererScreen(
                        settings = settings,
                        repository = appContainer.settingsRepository,
                        appContainer = appContainer,
                        playbackState = playbackState,
                        onBack = { navigateTo(AppScreen.Settings) },
                    )
                    AppScreen.Calibration -> CalibrationScreen(
                        settings = settings,
                        repository = appContainer.settingsRepository,
                        onBack = { navigateTo(AppScreen.Settings) },
                    )
                    AppScreen.About -> AboutScreen(
                        appContainer = appContainer,
                        onBack = { navigateTo(AppScreen.Settings) },
                    )
                }
            }

            val showBottomChrome = !isSecondaryScreen && selectedScreen != AppScreen.Player && !vrMode
            if (showBottomChrome) {
                GlassNavigationBar(
                    selectedScreen = selectedScreen,
                    onSelect = ::navigateTo,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 14.dp)
                        .navigationBarsPadding(),
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 14.dp)
                        .navigationBarsPadding()
                        .size(58.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showNav = true },
                    color = GlassFill,
                    contentColor = Label1,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, GlassBorder),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "All screens",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            if (showNav) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showNav = false },
                    sheetState = sheetState,
                    containerColor = Color(0xFF0F1014),
                    contentColor = Label1,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .navigationBarsPadding()
                            .padding(bottom = 14.dp),
                    ) {
                        Text(
                            text = "All screens",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.W700,
                            color = Label1,
                            modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
                        )
                        val items = listOf(
                            "01" to AppScreen.Library,
                            "02" to AppScreen.VideoDetails,
                            "03" to AppScreen.Player,
                            "04" to AppScreen.Renderer,
                            "05" to AppScreen.Settings,
                            "06" to AppScreen.Calibration,
                            "07" to AppScreen.About,
                        )
                        items.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowItems.forEach { (num, screen) ->
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) {
                                                navigateTo(screen)
                                                showNav = false
                                            },
                                        color = Color.White.copy(alpha = 0.045f),
                                        shape = ControlShape,
                                        border = BorderStroke(1.dp, GlassBorder),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                                        ) {
                                            Text(
                                                text = num,
                                                color = Label3,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.W500,
                                            )
                                            Text(
                                                text = screen.title,
                                                color = Label1,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.W600,
                                            )
                                        }
                                    }
                                }
                                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// COMMON UI PRIMITIVES — pixel-faithful translations of HTML CSS classes
// ============================================================================

@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = CardShape,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                spotColor = Color.Black.copy(alpha = 0.35f),
                ambientColor = Color.Black.copy(alpha = 0.15f),
            )
            .clip(shape)
    ) {
        // Frosted base — elevated dark panel simulating backdrop blur
        Box(modifier = Modifier.matchParentSize().background(GlassBase))
        // Glass fill: rgba(255,255,255,.055)
        Box(modifier = Modifier.matchParentSize().background(GlassFill))
        // Inner top-edge specular: inset 0 1px 0 rgba(255,255,255,.10)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.12f),
                        0.06f to Color.Transparent,
                    )
                )
        )
        // Border via outline
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(1.dp, GlassBorder, shape)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    action: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 26.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 13.sp,
            fontWeight = FontWeight.W600,
            color = Label2,
            letterSpacing = 0.6.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                text = action,
                fontSize = 13.sp,
                fontWeight = FontWeight.W600,
                color = AccentBlue,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = onActionClick != null,
                ) { onActionClick?.invoke() },
            )
        }
    }
}

@Composable
internal fun ListRow(
    title: String,
    subtitle: String? = null,
    iconTint: Color = SlateColor,
    leadingIcon: ImageVector? = null,
    value: String? = null,
    valuePill: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    danger: Boolean = false,
    link: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() } else Modifier,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(iconTint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = if (danger || link) FontWeight.W500 else FontWeight.W500,
                color = when {
                    danger -> RedColor
                    link -> AccentBlue
                    else -> Label1
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Label2,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                fontSize = 15.sp,
                color = Label2,
                fontWeight = FontWeight.W400,
            )
        }
        if (valuePill != null) valuePill()
        if (trailing != null) trailing() else if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Label3,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun NoIconRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, fontSize = 16.sp, color = Label1, fontWeight = FontWeight.W500, modifier = Modifier.weight(1f))
        Text(text = value, fontSize = 15.sp, color = Label2)
    }
}

@Composable
internal fun RowDivider(startIndent: Int = 58) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent.dp),
        thickness = 1.dp,
        color = Separator,
    )
}

@Composable
internal fun Pill(
    text: String,
    color: Color,
    bgAlpha: Float = 0.13f,
    icon: ImageVector? = null,
    showDot: Boolean = false,
) {
    Surface(
        color = color.copy(alpha = bgAlpha),
        shape = RoundedCornerShape(100.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(color, CircleShape),
                )
            }
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = text,
                fontSize = 12.sp,
                color = color,
                fontWeight = FontWeight.W600,
                letterSpacing = (-0.1).sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun CodecBadge(label: String, status: CodecPillStatus) {
    val color = when (status) {
        CodecPillStatus.Ok -> GreenColor
        CodecPillStatus.Sw -> AmberColor
        CodecPillStatus.None -> RedColor
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(5.dp),
    ) {
        Text(
            text = label.uppercase(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.W700,
            color = color,
            letterSpacing = 0.4.sp,
        )
    }
}

internal enum class CodecPillStatus { Ok, Sw, None }

@Composable
internal fun Banner(
    title: String,
    body: String,
    icon: ImageVector,
    tone: BannerTone,
    modifier: Modifier = Modifier,
) {
    val color = when (tone) {
        BannerTone.Warn -> AmberColor
        BannerTone.Error -> RedColor
        BannerTone.Ok -> GreenColor
    }
    Surface(
        modifier = modifier,
        color = color.copy(alpha = if (tone == BannerTone.Ok) 0.08f else 0.09f),
        shape = CardShape,
        border = BorderStroke(1.dp, color.copy(alpha = if (tone == BannerTone.Ok) 0.15f else 0.16f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp).padding(top = 2.dp),
            )
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W600,
                    color = Label1,
                    letterSpacing = (-0.1).sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = body,
                    fontSize = 13.sp,
                    color = Label2,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

internal enum class BannerTone { Warn, Error, Ok }

@Composable
internal fun Segmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = GlassFill,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GlassBorder),
    ) {
        Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEachIndexed { idx, opt ->
                val selected = idx == selectedIndex
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(idx) },
                    color = if (selected) GlassRaised else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = opt,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W600,
                            color = if (selected) Label1 else Label2,
                            letterSpacing = (-0.1).sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    labelColor: Color = Label1,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                color = labelColor,
                letterSpacing = (-0.1).sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                fontSize = 13.sp,
                color = Label2,
                fontWeight = FontWeight.W500,
            )
        }
        Spacer(modifier = Modifier.height(11.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = AccentBlue,
                inactiveTrackColor = Color(0xFF7B8090).copy(alpha = 0.28f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
internal fun ToggleRow(
    title: String,
    subtitle: String?,
    iconTint: Color,
    leadingIcon: ImageVector?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(iconTint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, color = Label1, fontWeight = FontWeight.W500)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Label2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = GreenColor,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.30f),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        color = if (enabled) AccentBlue else AccentBlue.copy(alpha = 0.38f),
        contentColor = Color.White,
        shape = ControlShape,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = 17.sp,
                fontWeight = FontWeight.W600,
                color = Color.White,
                letterSpacing = (-0.2).sp,
            )
        }
    }
}

@Composable
internal fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        color = GlassRaised,
        contentColor = Label1,
        shape = ControlShape,
        border = BorderStroke(1.dp, GlassBorder),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = Label1)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = 17.sp,
                fontWeight = FontWeight.W600,
                color = Label1,
                letterSpacing = (-0.2).sp,
            )
        }
    }
}

@Composable
internal fun NavBar(
    onBack: (() -> Unit)? = null,
    title: String? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) NavButton(icon = Icons.Default.ChevronLeft, onClick = onBack)
        if (title != null) {
            Surface(
                color = GlassFill,
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(1.dp, GlassBorder),
            ) {
                Text(
                    text = title,
                    modifier = Modifier
                        .padding(horizontal = 17.dp, vertical = 9.dp)
                        .widthIn(max = 230.dp),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W600,
                    color = Label1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-0.1).sp,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
internal fun NavButton(icon: ImageVector, onClick: () -> Unit, tint: Color = Label1) {
    Surface(
        modifier = Modifier
            .size(38.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        color = GlassRaised,
        shape = CircleShape,
        border = BorderStroke(1.dp, GlassBorder),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
internal fun LargeTitle(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 18.dp)) {
        Text(
            text = title,
            fontSize = 34.sp,
            fontWeight = FontWeight.W800,
            color = Label1,
            letterSpacing = (-0.7).sp,
            lineHeight = 38.sp,
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = subtitle,
                fontSize = 15.sp,
                color = Label2,
                letterSpacing = (-0.1).sp,
            )
        }
    }
}

@Composable
internal fun ScreenScroll(
    modifier: Modifier = Modifier,
    topPadding: Int = 112,
    bottomPadding: Int = ScreenBottomChromePadding,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = topPadding.dp, bottom = bottomPadding.dp),
        content = content,
    )
}

@Composable
internal fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Search titles",
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = GlassFill,
        shape = ControlShape,
        border = BorderStroke(1.dp, GlassBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Label3,
                modifier = Modifier.size(15.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(text = placeholder, color = Label3, fontSize = 17.sp)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(color = Label1, fontSize = 17.sp),
                    singleLine = true,
                    cursorBrush = Brush.verticalGradient(listOf(AccentBlue, AccentBlue)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun GlassNavigationBar(
    selectedScreen: AppScreen,
    onSelect: (AppScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val selectedIndex = primaryScreens.indexOf(selectedScreen).coerceAtLeast(0)
    val innerPadding = 6.dp
    val tabHeight = 46.dp   // pill height 58 - 2*6 padding
    val tabItemWidth = 56.dp
    val indicatorOffsetX by animateDpAsState(
        targetValue = tabItemWidth * selectedIndex,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "nav-indicator-x",
    )
    Surface(
        modifier = modifier.wrapContentWidth().height(58.dp),
        color = GlassFill,
        contentColor = Color.White,
        shape = RoundedCornerShape(100.dp),
        border = BorderStroke(1.dp, GlassBorder),
    ) {
        Box(modifier = Modifier.padding(innerPadding)) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffsetX)
                    .width(tabItemWidth)
                    .height(tabHeight)
                    .background(AccentBlue.copy(alpha = 0.88f), RoundedCornerShape(100.dp)),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                primaryScreens.forEach { screen ->
                    val selected = selectedScreen == screen
                    val interactionSource = remember(screen) { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .width(tabItemWidth)
                            .height(tabHeight)
                            .clickable(interactionSource = interactionSource, indication = null) {
                                if (selectedScreen != screen) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(screen)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.navLabel,
                            modifier = Modifier.size(26.dp),
                            tint = if (selected) Color.White else Label2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.LiquidBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF233A66).copy(alpha = 0.55f),
                        Color.Transparent,
                    ),
                    radius = 900f,
                ),
            ),
    )
}

// ============================================================================
// VR SYSTEM UI EFFECT
// ============================================================================
@Composable
internal fun VrSystemUiEffect(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(active, view) {
        val activity = view.context.findActivity()
        if (!active) {
            // Ensure portrait + system bars restored whenever VR is not active
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.let { window ->
                WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
            onDispose { }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, view).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            onDispose {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                activity?.window?.let { window ->
                    WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                }
            }
        }
    }
}

// ============================================================================
// APP SCREEN ENUM & SUPPORT
// ============================================================================
internal enum class AppScreen(
    val title: String,
    val navLabel: String,
    val icon: ImageVector,
) {
    Library("Library", "Library", Icons.Default.Movie),
    Player("Player", "Player", Icons.Default.PlayArrow),
    Settings("Settings", "Settings", Icons.Default.Settings),
    VideoDetails("Video details", "Details", Icons.Default.Info),
    Renderer("Cinema", "Cinema", Icons.Default.Tv),
    Calibration("Calibration", "Calibration", Icons.Default.ViewInAr),
    About("About", "About", Icons.Default.Info),
}

internal val primaryScreens = listOf(
    AppScreen.Library,
    AppScreen.Player,
    AppScreen.Settings,
)

// ============================================================================
// EXTENSION HELPERS
// ============================================================================
internal fun VideoItem.cardGradient(): Brush {
    // Pick a stable gradient based on id
    val palettes = listOf(
        listOf(Color(0xFF24385C), Color(0xFF141F38)),
        listOf(Color(0xFF473152), Color(0xFF241A30)),
        listOf(Color(0xFF1F4540), Color(0xFF10241F)),
        listOf(Color(0xFF3D2C42), Color(0xFF1A1426)),
    )
    val colors = palettes[(id.toInt().let { if (it < 0) -it else it }) % palettes.size]
    return Brush.linearGradient(colors)
}

internal fun VideoItem.codecBadge(): String {
    val raw = probeResult.codecFamily.uppercase()
    // Shorten verbose names to match index.html badge style: "HEVC · HW" not "H.265 / HEVC · HW"
    val fam = when {
        raw.contains("HEVC") || raw.contains("H.265") || raw.contains("H265") -> "HEVC"
        raw.contains("H.264") || raw.contains("H264") || raw.contains("AVC") -> "H.264"
        raw.contains("AV1") -> "AV1"
        raw.contains("VP9") -> "VP9"
        raw.contains("VP8") -> "VP8"
        raw.isBlank() -> ""
        else -> raw.split("/", " ").firstOrNull { it.isNotBlank() }?.trim() ?: raw
    }
    val suffix = when (codecStatus()) {
        CodecPillStatus.Ok -> "HW"
        CodecPillStatus.Sw -> "SW"
        CodecPillStatus.None -> "N/A"
    }
    return if (fam.isBlank()) suffix else "$fam · $suffix"
}

internal fun VideoItem.codecStatus(): CodecPillStatus = when (probeResult.supportStatus) {
    CodecSupportStatus.Supported -> CodecPillStatus.Ok
    CodecSupportStatus.Risky -> CodecPillStatus.Sw
    CodecSupportStatus.Unsupported, CodecSupportStatus.Unknown -> CodecPillStatus.None
}

internal fun VideoItem.resolutionLabel(): String =
    if (width > 0 && height > 0) "$width × $height" else "Unknown"

internal fun VideoItem.resolutionShort(): String = when {
    width <= 0 || height <= 0 -> "Unknown"
    height >= 2160 -> "4K"
    height >= 1440 -> "1440p"
    height >= 1080 -> "1080p"
    height >= 720 -> "720p"
    else -> "${height}p"
}

internal fun VideoItem.fullDurationLabel(): String {
    if (durationMs <= 0L) return "Unknown"
    val totalSeconds = durationMs / 1000L
    val h = totalSeconds / 3600L
    val m = (totalSeconds % 3600L) / 60L
    val s = totalSeconds % 60L
    return if (h > 0) "%d h %02d m %02d s".format(h, m, s) else "%d m %02d s".format(m, s)
}

internal fun VideoItem.fileSizeLabel(): String {
    // VideoItem doesn't track file size in this codebase; estimate from bitrate × duration when available.
    val bytes = (probeResult.bitrate.coerceAtLeast(0L) / 8L) * (durationMs / 1000L)
    if (bytes <= 0L) return "Unknown"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) "%.1f GB".format(gb) else "%.0f MB".format(bytes / (1024.0 * 1024.0))
}

internal fun VideoItem.progressFraction(): Float {
    if (durationMs <= 0L) return 0f
    return (playbackPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

internal fun Long.timeLabel(): String {
    if (this <= 0L) return "0:00"
    val totalSeconds = this / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ============================================================================
// PREVIEW
// ============================================================================
@Preview(showBackground = true)
@Composable
internal fun AuroraAppPreview() {
    AuroraTheme {
        AuroraApp(
            appContainer = object : AppContainer {
                override val applicationContext = LocalContext.current
                override val libraryRepository: OfflineLibraryRepository = object : OfflineLibraryRepository {
                    override val videos: Flow<List<VideoItem>> = MutableStateFlow(emptyList())
                    override suspend fun importVideo(uri: Uri, sourceType: String): ImportResult = ImportResult(0, 0)
                    override suspend fun importFolder(uri: Uri): ImportResult = ImportResult(0, 0)
                    override suspend fun refreshAccessChecks() = Unit
                    override suspend fun renameDisplayTitle(videoId: Long, title: String) = Unit
                    override suspend fun deleteLibraryEntry(videoId: Long) = Unit
                }
                override val playerController = object : com.aurora.cinema.playback.PlayerController {
                    override val state: kotlinx.coroutines.flow.StateFlow<PlaybackState> = MutableStateFlow(PlaybackState())
                    override val mediaPlayer: androidx.media3.common.Player? = null
                    override fun select(video: VideoItem) = Unit
                    override fun play() = Unit
                    override fun pause() = Unit
                    override fun seekTo(positionMs: Long) = Unit
                    override fun stop() = Unit
                    override fun saveProgress() = Unit
                    override fun setVideoSurface(surface: android.view.Surface?) = Unit
                    override fun setSubtitleSettings(settings: SubtitleSettings) = Unit
                }
                override val hardwareInputController = object : com.aurora.cinema.input.HardwareInputController {
                    override val actions: kotlinx.coroutines.flow.SharedFlow<InputAction> = MutableSharedFlow()
                    override fun updateSettings(settings: com.aurora.cinema.input.HardwareInputSettings) = Unit
                }
                override val mediaSessionController: com.aurora.cinema.playback.MediaSessionController
                    get() = error("Preview does not create a media session")
                override val codecCapabilityService = com.aurora.cinema.media.CodecCapabilityService()
                override val deviceDisplayInfo = com.aurora.cinema.media.DeviceDisplayInfo(applicationContext)
                override val settingsRepository = object : AppSettingsRepository {
                    override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings(firstRunAcknowledged = true))
                    override suspend fun setFirstRunAcknowledged(acknowledged: Boolean) = Unit
                    override suspend fun setComfortModeEnabled(enabled: Boolean) = Unit
                    override suspend fun setDefaultScreenDistanceMeters(distanceMeters: Float) = Unit
                    override suspend fun setCinemaScreenConfig(config: CinemaScreenConfig) = Unit
                    override suspend fun setStereoConfig(config: StereoConfig) = Unit
                    override suspend fun setVideoProjection(projection: VideoProjection) = Unit
                    override suspend fun setTheatreSceneConfig(config: com.aurora.cinema.render.TheatreSceneConfig) = Unit
                    override suspend fun setHeadsetProfile(profile: com.aurora.cinema.render.HeadsetProfile) = Unit
                    override suspend fun setSubtitleSettings(settings: SubtitleSettings) = Unit
                    override suspend fun setHardwareInputSettings(
                        settings: com.aurora.cinema.input.HardwareInputSettings,
                    ) = Unit
                }
            },
        )
    }
}
