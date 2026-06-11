package com.aurora.cinema.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
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
private val BgColor = Color(0xFF0A0B0E)
private val BodyBg = Color(0xFF101115)
private val AccentBlue = Color(0xFF549BFF)
private val GreenColor = Color(0xFF4CC38A)
private val AmberColor = Color(0xFFE0A24E)
private val RedColor = Color(0xFFE36A60)
private val TealColor = Color(0xFF5BC0CE)
private val VioletColor = Color(0xFF9A8CFF)
private val SlateColor = Color(0xFF8E97A8)

private val Label1 = Color.White.copy(alpha = 0.94f)
private val Label2 = Color(0xFFE1E6F0).copy(alpha = 0.60f)
private val Label3 = Color(0xFFE1E6F0).copy(alpha = 0.34f)
private val Separator = Color.White.copy(alpha = 0.07f)

private val GlassFill = Color.White.copy(alpha = 0.055f)
private val GlassRaised = Color.White.copy(alpha = 0.09f)
private val GlassBorder = Color.White.copy(alpha = 0.085f)
// Glass base — elevated surface simulating backdrop blur frosting against #101115 bg
private val GlassBase = Color(0xFF21242E)

private val CardShape = RoundedCornerShape(18.dp)
private val ControlShape = RoundedCornerShape(13.dp)
private const val ScreenBottomChromePadding = 220

// Map: secondary screens route back to their owning primary tab (mirrors HTML's TAB_SCREENS).
private val TAB_SCREENS: Map<AppScreen, AppScreen> = mapOf(
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
private fun GlassCard(
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
private fun SectionHeader(
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
private fun ListRow(
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
private fun RowDivider(startIndent: Int = 58) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent.dp),
        thickness = 1.dp,
        color = Separator,
    )
}

@Composable
private fun Pill(
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
private fun CodecBadge(label: String, status: CodecPillStatus) {
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

private enum class CodecPillStatus { Ok, Sw, None }

@Composable
private fun Banner(
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

private enum class BannerTone { Warn, Error, Ok }

@Composable
private fun Segmented(
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
private fun SliderRow(
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
private fun ToggleRow(
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
private fun PrimaryButton(
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
private fun GlassButton(
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
private fun NavBar(
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
private fun NavButton(icon: ImageVector, onClick: () -> Unit, tint: Color = Label1) {
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
private fun LargeTitle(title: String, subtitle: String? = null) {
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
private fun ScreenScroll(
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
private fun SearchBar(
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
private fun GlassNavigationBar(
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
private fun BoxScope.LiquidBackdrop() {
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
// 1. SAFETY / ONBOARDING SCREEN
// ============================================================================
@Composable
private fun SafetyAcknowledgementScreen(onContinue: () -> Unit) {
    var ack by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 86.dp, bottom = 40.dp),
        ) {
            // Onb hero
            Column(modifier = Modifier.padding(horizontal = 4.dp).padding(top = 6.dp, bottom = 22.dp)) {
                Text(
                    text = "Before you put on the headset",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.W800,
                    color = Label1,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 32.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "A few things to know each time you use VR playback.",
                    fontSize = 15.sp,
                    color = Label2,
                )
            }

            GlassCard {
                OnbItem(
                    icon = Icons.Default.EventSeat,
                    tint = AccentBlue,
                    title = "Use VRPlay seated",
                    body = "Stay seated for the full session. Standing playback isn't supported and increases the chance of losing balance.",
                )
                HorizontalDivider(color = Separator)
                OnbItem(
                    icon = Icons.Default.Waves,
                    tint = AmberColor,
                    title = "Stop if you feel unwell",
                    body = "Dizziness, nausea, or eye strain can build quickly. Remove the headset and rest before continuing.",
                )
                HorizontalDivider(color = Separator)
                OnbItem(
                    icon = Icons.Outlined.Schedule,
                    tint = TealColor,
                    title = "Take breaks",
                    body = "Rest for a few minutes every 30 minutes of headset use.",
                )
            }

            Column { SectionHeader(title = "Your data") }

            GlassCard {
                OnbItem(
                    icon = Icons.Default.Security,
                    tint = GreenColor,
                    title = "Everything stays on this device",
                    body = "VRPlay only reads videos you import. Playback positions, calibration, and settings are stored locally. Nothing is uploaded.",
                )
            }

            // Ack row
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { ack = !ack },
                color = GlassFill,
                shape = CardShape,
                border = BorderStroke(1.dp, GlassBorder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(23.dp)
                            .background(
                                if (ack) AccentBlue else Color.Transparent,
                                CircleShape,
                            )
                            .then(
                                if (!ack) Modifier else Modifier,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (ack) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(23.dp),
                                color = Color.Transparent,
                                shape = CircleShape,
                                border = BorderStroke(1.8.dp, Label3),
                            ) {}
                        }
                    }
                    Text(
                        text = "I've read and understand the safety guidance",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W500,
                        color = Label1,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            PrimaryButton(
                text = "Continue",
                enabled = ack,
                onClick = onContinue,
            )
            Spacer(modifier = Modifier.height(9.dp))
            Text(
                text = "Shown on first launch. You can re-read this from Settings.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                fontSize = 13.sp,
                color = Label3,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun OnbItem(icon: ImageVector, tint: Color, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(tint.copy(alpha = 0.14f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.W600,
                color = Label1,
                letterSpacing = (-0.1).sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = body, fontSize = 13.sp, color = Label2, lineHeight = 19.sp)
        }
    }
}

// ============================================================================
// 2. LIBRARY SCREEN
// ============================================================================
@Composable
private fun LibraryScreen(
    videos: List<VideoItem>,
    repository: OfflineLibraryRepository,
    onOpenDetails: (Long) -> Unit,
    onResume: () -> Unit,
    onReviewSafety: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var sortIndex by rememberSaveable { mutableStateOf(0) }
    val sortOptions = listOf("Recent", "Title", "Duration", "Resolution")

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { repository.importVideo(uri) }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) scope.launch { repository.importFolder(uri) }
    }

    val filtered = videos.filter { it.displayName.contains(query, ignoreCase = true) }
    val continueWatching = videos
        .filter { it.playbackPositionMs > 0L && !it.playbackCompleted }
        .sortedByDescending { it.playbackUpdatedAt }
        .take(6)
    val recentVideos = videos.sortedByDescending { it.lastSeenAt }.take(4)
    val missing = videos.count { it.accessState == VideoAccessState.Missing }
    val sessionOnly = videos.count { it.accessState == VideoAccessState.Available && !it.persistedPermission }
    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        ScreenScroll {
            LargeTitle(
                title = "Library",
                subtitle = "${videos.size} ${if (videos.size == 1) "video" else "videos"} on device",
            )
            SearchBar(value = query, onValueChange = { query = it })

            SectionHeader(title = "Sort")
            Segmented(
                options = sortOptions,
                selectedIndex = sortIndex,
                onSelect = { sortIndex = it },
                modifier = Modifier.fillMaxWidth(),
            )

            if (continueWatching.isNotEmpty()) {
                SectionHeader(title = "Continue watching", action = "Resume", onActionClick = onResume)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    continueWatching.forEach { v ->
                        VCard(
                            title = v.displayName,
                            subtitle = "${v.resolutionShort()} · resumes at ${v.playbackPositionMs.timeLabel()}",
                            duration = v.durationMs.timeLabel(),
                            progress = v.progressFraction(),
                            gradient = v.cardGradient(),
                            onClick = { onOpenDetails(v.id) },
                        )
                    }
                }
            }

            SectionHeader(title = "Recently added")
            if (recentVideos.isEmpty()) {
                GlassCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Movie, null, tint = Label2, modifier = Modifier.size(26.dp))
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("No videos yet", fontSize = 17.sp, fontWeight = FontWeight.W700, color = Label1)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Import a video file or a whole folder to start building your library.",
                            fontSize = 13.sp,
                            color = Label2,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        PrimaryButton(
                            text = "Import video",
                            onClick = { filePicker.launch(arrayOf("video/*")) },
                            modifier = Modifier.widthIn(max = 220.dp),
                        )
                    }
                }
            } else {
                GlassCard {
                    recentVideos.forEachIndexed { idx, v ->
                        val tint = listOf(AccentBlue, VioletColor, TealColor, SlateColor)[idx % 4]
                        ListRow(
                            title = v.displayName,
                            subtitle = "${v.durationMs.timeLabel()} · ${v.resolutionLabel()}",
                            iconTint = tint,
                            leadingIcon = Icons.Default.Movie,
                            valuePill = { CodecBadge(label = v.codecBadge(), status = v.codecStatus()) },
                            onClick = { onOpenDetails(v.id) },
                        )
                        if (idx < recentVideos.lastIndex) RowDivider()
                    }
                }
            }

            if (missing > 0 || sessionOnly > 0) {
                SectionHeader(title = "Needs attention")
                if (missing > 0) {
                    Banner(
                        title = "$missing file${if (missing == 1) "" else "s"} can't be found",
                        body = "Files were moved or deleted from their original folder. Locate the files or remove them from the library.",
                        icon = Icons.Default.Warning,
                        tone = BannerTone.Warn,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                if (sessionOnly > 0) {
                    Banner(
                        title = "Folder access expired",
                        body = "Permission was revoked by the system. Re-grant access to keep these $sessionOnly video${if (sessionOnly == 1) "" else "s"} available.",
                        icon = Icons.Default.Lock,
                        tone = BannerTone.Error,
                    )
                }
            }

            if (videos.isEmpty()) {
                SectionHeader(title = "Empty state · preview")
                GlassCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Movie, null, tint = Label2, modifier = Modifier.size(26.dp))
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("No videos yet", fontSize = 17.sp, fontWeight = FontWeight.W700, color = Label1)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Import a video file or a whole folder to start building your library.",
                            fontSize = 13.sp,
                            color = Label2,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton(
                                text = "Import video",
                                onClick = { filePicker.launch(arrayOf("video/*")) },
                                modifier = Modifier.weight(1f),
                            )
                            GlassButton(
                                text = "Folder",
                                icon = Icons.Default.Folder,
                                onClick = { folderPicker.launch(null) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        // Top-right navbar buttons
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 16.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NavButton(icon = Icons.Default.Add, onClick = { filePicker.launch(arrayOf("video/*")) })
            NavButton(icon = Icons.Default.Folder, onClick = { folderPicker.launch(null) })
        }
    }
}

@Composable
private fun VCard(
    title: String,
    subtitle: String,
    duration: String,
    progress: Float,
    gradient: Brush,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(158.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        color = GlassFill,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, GlassBorder),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(gradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp),
                )
                // Duration badge bottom-right
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 7.dp, bottom = 10.dp),
                    color = BgColor.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = duration,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W600,
                        color = Label1,
                    )
                }
                // Progress bar
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.16f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(AccentBlue),
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W600,
                    color = Label1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-0.1).sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Label2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ============================================================================
// 3. VIDEO DETAILS SCREEN
// ============================================================================
@Composable
private fun VideoDetailsScreen(
    video: VideoItem?,
    playbackState: PlaybackState,
    onBack: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onDelete: (Long) -> Unit,
    onRefresh: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(BodyBg)) {
        ScreenScroll(topPadding = 76) {
            if (video == null) {
                Spacer(modifier = Modifier.height(40.dp))
                Text(
                    text = "No video selected",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.W700,
                    color = Label1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Open a title from your library to see its media details.",
                    fontSize = 15.sp,
                    color = Label2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                return@ScreenScroll
            }
            // Hero card
            GlassCard {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .background(video.cardGradient())
                        .clip(CardShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Movie, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(30.dp))
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 7.dp, bottom = 10.dp),
                        color = BgColor.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = video.durationMs.timeLabel(),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.W600,
                            color = Label1,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.16f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(video.progressFraction())
                                .height(3.dp)
                                .background(AccentBlue),
                        )
                    }
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 17.dp)) {
                    Text(
                        text = video.displayName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.W800,
                        color = Label1,
                        letterSpacing = (-0.4).sp,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = video.uri,
                        fontSize = 13.sp,
                        color = Label2,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        val status = video.codecStatus()
                        val pillColor = when (status) {
                            CodecPillStatus.Ok -> GreenColor
                            CodecPillStatus.Sw -> AmberColor
                            CodecPillStatus.None -> RedColor
                        }
                        Pill(
                            text = "${video.codecBadge()} · ${when (status) {
                                CodecPillStatus.Ok -> "hardware decode"
                                CodecPillStatus.Sw -> "software decode"
                                CodecPillStatus.None -> "no decoder"
                            }}",
                            color = pillColor,
                            showDot = true,
                        )
                        if (video.playbackPositionMs > 0L) {
                            Pill(
                                text = "Resume at ${video.playbackPositionMs.timeLabel()}",
                                color = TealColor,
                                icon = Icons.Outlined.Schedule,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(15.dp))
                    PrimaryButton(
                        text = "Play",
                        icon = Icons.Default.PlayArrow,
                        enabled = video.accessState == VideoAccessState.Available,
                        onClick = { onPlay(video) },
                    )
                }
            }

            SectionHeader(title = "File")
            GlassCard {
                NoIconRow(label = "Duration", value = video.fullDurationLabel())
                RowDivider(startIndent = 16)
                NoIconRow(label = "Resolution", value = video.resolutionLabel())
                RowDivider(startIndent = 16)
                NoIconRow(label = "File size", value = video.fileSizeLabel())
                RowDivider(startIndent = 16)
                NoIconRow(label = "Container", value = video.mimeType.ifBlank { "Unknown" })
                RowDivider(startIndent = 16)
                NoIconRow(
                    label = "Video codec",
                    value = "${video.probeResult.codecFamily}${
                        if (video.probeResult.frameRate > 0f) " · ${video.probeResult.frameRate.toInt()} fps" else ""
                    }",
                )
                RowDivider(startIndent = 16)
                NoIconRow(label = "Audio", value = video.probeResult.profileLevel.ifBlank { "Unknown" })
            }

            SectionHeader(title = "Decoder")
            when (video.codecStatus()) {
                CodecPillStatus.Ok -> Banner(
                    title = "Hardware decoding available",
                    body = "${video.probeResult.decoderName.ifBlank { "Hardware decoder" }} supports this stream at full resolution.",
                    icon = Icons.Default.Check,
                    tone = BannerTone.Ok,
                )
                CodecPillStatus.Sw -> Banner(
                    title = "Software decoding will be used",
                    body = "No hardware decoder was matched. Playback will work but may use more battery.",
                    icon = Icons.Default.Warning,
                    tone = BannerTone.Warn,
                )
                CodecPillStatus.None -> Banner(
                    title = "No decoder found",
                    body = "This codec isn't supported on this device. Try a different file or transcode it first.",
                    icon = Icons.Default.Warning,
                    tone = BannerTone.Error,
                )
            }

            SectionHeader(title = "Manage")
            GlassCard {
                ListRow(
                    title = "Refresh metadata",
                    subtitle = "Re-probe duration, streams, and codec support",
                    link = true,
                    trailing = {
                        Icon(Icons.Default.Refresh, null, tint = Label3, modifier = Modifier.size(18.dp))
                    },
                    onClick = onRefresh,
                )
                RowDivider(startIndent = 16)
                ListRow(
                    title = "Rename display title",
                    subtitle = "Changes how it appears in the library only",
                    link = true,
                    trailing = {
                        Icon(Icons.Default.Edit, null, tint = Label3, modifier = Modifier.size(18.dp))
                    },
                    onClick = { /* rename — not exposed in this UI prototype */ },
                )
                RowDivider(startIndent = 16)
                ListRow(
                    title = "Remove from library",
                    subtitle = "The source file on your device is kept",
                    danger = true,
                    trailing = {
                        Icon(Icons.Default.Delete, null, tint = RedColor, modifier = Modifier.size(18.dp))
                    },
                    onClick = { onDelete(video.id) },
                )
            }
        }

        // Top nav
        Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            NavBar(
                onBack = onBack,
                actions = {
                    NavButton(icon = Icons.Default.Edit, onClick = { /* rename dialog */ })
                },
            )
        }
    }
}

@Composable
private fun NoIconRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 16.sp, color = Label1, fontWeight = FontWeight.W500, modifier = Modifier.weight(1f))
        Text(text = value, fontSize = 15.sp, color = Label2)
    }
}

// ============================================================================
// 4. PLAYER SCREEN
// ============================================================================
@Composable
private fun PlayerScreen(
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
    var comfortState by remember {
        mutableStateOf(
            SessionComfortPolicy.evaluate(
                SessionEnvironment(headsetMode = vrMode, comfortModeEnabled = comfortModeEnabled),
            ),
        )
    }
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
    LaunchedEffect(appContainer.hardwareInputController, headTracker.available) {
        appContainer.hardwareInputController.actions.collect { action ->
            if (action == InputAction.Recenter) {
                if (headTracker.available) headTracker.recenter() else renderEngine.recenter()
            }
        }
    }
    LaunchedEffect(playbackState.videoWidth, playbackState.videoHeight) {
        renderEngine.setVideoSize(playbackState.videoWidth, playbackState.videoHeight)
    }
    LaunchedEffect(videoProjection) { renderEngine.setVideoProjection(videoProjection) }
    LaunchedEffect(appContainer.settingsRepository, appContainer) {
        appContainer.settingsRepository.settings.collect { settings ->
            renderEngine.setTheatreSceneConfig(settings.theatreSceneConfig)
        }
    }
    LaunchedEffect(vrMode, comfortModeEnabled) {
        while (vrMode) {
            val env = AndroidSessionEnvironment.read(
                context = context,
                headsetMode = true,
                comfortModeEnabled = comfortModeEnabled,
            )
            comfortState = SessionComfortPolicy.evaluate(env)
            delay(30_000L)
        }
        comfortState = SessionComfortPolicy.evaluate(
            SessionEnvironment(headsetMode = false, comfortModeEnabled = comfortModeEnabled),
        )
    }
    LaunchedEffect(vrMode, comfortState.saveProgressIntervalMs) {
        while (vrMode) {
            delay(comfortState.saveProgressIntervalMs)
            appContainer.playerController.saveProgress()
        }
    }
    LaunchedEffect(screenConfig, comfortState.brightnessLimit) {
        renderEngine.setCinemaScreenConfig(
            screenConfig.copy(brightness = screenConfig.brightness.coerceAtMost(comfortState.brightnessLimit)),
        )
    }
    LaunchedEffect(stereoConfig, vrMode) {
        renderEngine.setStereoConfig(stereoConfig.copy(enabled = vrMode))
    }
    LaunchedEffect(subtitleSettings) { appContainer.playerController.setSubtitleSettings(subtitleSettings) }
    LaunchedEffect(vrMode) {
        if (vrMode) headTracker.start() else {
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
                    appContainer.playerController.saveProgress()
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
            appContainer.playerController.saveProgress()
            headTracker.stop()
            appContainer.playerController.setVideoSurface(null)
            renderEngine.release()
            onVrModeChanged(false)
        }
    }

    val activeVideo = playbackState.selectedVideo ?: selectedVideo

    if (vrMode) {
        var vrControlsLocked by rememberSaveable { mutableStateOf(false) }
        var vrScreenConfig by remember { mutableStateOf(screenConfig) }
        LaunchedEffect(screenConfig) { vrScreenConfig = screenConfig }
        fun executeGazeTarget(target: VrGazeTarget) {
            when (target) {
                VrGazeTarget.Back -> appContainer.playerController.seekTo(
                    (playbackState.positionMs - 10_000L).coerceAtLeast(0L),
                )
                VrGazeTarget.PlayPause -> {
                    if (activeVideo != null && playbackState.selectedVideo == null) {
                        appContainer.playerController.select(activeVideo)
                    }
                    if (playbackState.isPlaying) appContainer.playerController.pause() else appContainer.playerController.play()
                }
                VrGazeTarget.Forward -> appContainer.playerController.seekTo(playbackState.positionMs + 10_000L)
                VrGazeTarget.Timeline -> {
                    val mid = playbackState.durationMs / 2L
                    if (mid > 0L) appContainer.playerController.seekTo(mid)
                }
                VrGazeTarget.Recenter -> if (headTracker.available) headTracker.recenter() else renderEngine.recenter()
                VrGazeTarget.ScreenSmaller -> {
                    vrScreenConfig = vrScreenConfig.copy(widthMeters = (vrScreenConfig.widthMeters - 1f).coerceAtLeast(6f))
                    scope.launch { appContainer.settingsRepository.setCinemaScreenConfig(vrScreenConfig) }
                }
                VrGazeTarget.ScreenLarger -> {
                    vrScreenConfig = vrScreenConfig.copy(widthMeters = (vrScreenConfig.widthMeters + 1f).coerceAtMost(30f))
                    scope.launch { appContainer.settingsRepository.setCinemaScreenConfig(vrScreenConfig) }
                }
                VrGazeTarget.LockControls -> vrControlsLocked = !vrControlsLocked
                VrGazeTarget.Exit -> onVrModeChanged(false)
                VrGazeTarget.None -> Unit
            }
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> AuroraRenderView(ctx, renderEngine = renderEngine) },
            )
            VrPlaybackOverlay(
                viewMatrix = headPose.viewMatrix,
                tracking = headPose.tracking,
                sensorName = headPose.sensorName,
                isPlaying = playbackState.isPlaying,
                progressLabel = "${playbackState.positionMs.timeLabel()} / ${playbackState.durationMs.timeLabel()}",
                subtitleText = playbackState.subtitleText,
                subtitleSettings = subtitleSettings,
                controlsLocked = vrControlsLocked,
                onControlsLockedChange = { vrControlsLocked = it },
                onTarget = ::executeGazeTarget,
            )
        }
        return
    }

    // 2D player
    var errorDismissed by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(286.dp)
                    .padding(top = 98.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF19233F), Color(0xFF0A0F1E), Color.Black),
                            radius = 600f,
                        ),
                    ),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx -> AuroraRenderView(ctx, renderEngine = renderEngine) },
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp, bottom = 132.dp),
            ) {
                // Error banner
                playbackState.error?.let { err ->
                    if (!errorDismissed) {
                        Banner(
                            title = "Playback error",
                            body = err.message,
                            icon = Icons.Default.Warning,
                            tone = BannerTone.Error,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }

                // Timeline
                Surface(
                    color = GlassFill,
                    shape = CardShape,
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        val pos = playbackState.positionMs
                        val dur = playbackState.durationMs.coerceAtLeast(1L)
                        Slider(
                            value = pos.toFloat().coerceAtMost(dur.toFloat()),
                            onValueChange = { appContainer.playerController.seekTo(it.toLong()) },
                            valueRange = 0f..dur.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White.copy(alpha = 0.92f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                            ),
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(pos.timeLabel(), fontSize = 12.sp, color = Label2)
                            Spacer(Modifier.weight(1f))
                            Text("-${(dur - pos).coerceAtLeast(0L).timeLabel()}", fontSize = 12.sp, color = Label2)
                        }
                    }
                }

                // Transport
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TBtn(icon = Icons.Default.Stop, size = 44, glass = true) {
                        appContainer.playerController.stop()
                        onBack()
                    }
                    Spacer(Modifier.width(12.dp))
                    TBtn(icon = Icons.Default.Replay10, size = 52, glass = true) {
                        appContainer.playerController.seekTo((playbackState.positionMs - 10_000L).coerceAtLeast(0L))
                    }
                    Spacer(Modifier.width(12.dp))
                    TBtn(
                        icon = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        size = 66,
                        main = true,
                    ) {
                        if (activeVideo != null && playbackState.selectedVideo == null) {
                            appContainer.playerController.select(activeVideo)
                        }
                        if (playbackState.isPlaying) appContainer.playerController.pause() else appContainer.playerController.play()
                    }
                    Spacer(Modifier.width(12.dp))
                    TBtn(icon = Icons.Default.Forward10, size = 52, glass = true) {
                        appContainer.playerController.seekTo(playbackState.positionMs + 10_000L)
                    }
                    Spacer(Modifier.width(12.dp))
                    TBtn(icon = Icons.Default.Warning, size = 44, glass = true) {
                        errorDismissed = !errorDismissed
                    }
                }

                Spacer(Modifier.height(14.dp))
                GlassButton(
                    text = "Enter VR",
                    icon = Icons.Default.ViewInAr,
                    onClick = { onVrModeChanged(true) },
                )

                SectionHeader(title = "Subtitles & audio")
                GlassCard {
                    ToggleRow(
                        title = "Subtitles",
                        subtitle = playbackState.timedTextTracks.firstOrNull()?.label ?: "No embedded tracks",
                        iconTint = TealColor,
                        leadingIcon = Icons.Default.ClosedCaption,
                        checked = subtitleSettings.enabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                appContainer.settingsRepository.setSubtitleSettings(subtitleSettings.copy(enabled = enabled))
                            }
                        },
                    )
                    RowDivider()
                    var audioDelay by remember(subtitleSettings) { mutableStateOf(subtitleSettings.audioDelayMs.toFloat()) }
                    SliderRow(
                        label = "Audio delay",
                        valueLabel = "${if (audioDelay.toInt() > 0) "+" else ""}${audioDelay.toInt()} ms",
                        value = audioDelay,
                        range = -500f..500f,
                        onValueChange = { audioDelay = it },
                        onValueChangeFinished = {
                            scope.launch {
                                appContainer.settingsRepository.setSubtitleSettings(
                                    subtitleSettings.copy(audioDelayMs = audioDelay.toLong()),
                                )
                            }
                        },
                    )
                }

                Spacer(Modifier.height(9.dp))
                Text(
                    text = "Position is saved automatically. Closing the app resumes here next time.",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    fontSize = 13.sp,
                    color = Label3,
                    lineHeight = 19.sp,
                )
            }
        }

        // Top navbar overlay
        Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            NavBar(
                onBack = onBack,
                title = activeVideo?.displayName ?: "Player",
                actions = {
                    NavButton(icon = Icons.Default.ClosedCaption, onClick = {})
                },
            )
        }
    }
}

@Composable
private fun TBtn(icon: ImageVector, size: Int, main: Boolean = false, glass: Boolean = false, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(size.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        color = when {
            main -> Color.White.copy(alpha = 0.94f)
            glass -> GlassFill
            else -> Color.Transparent
        },
        contentColor = if (main) BgColor else Label1,
        shape = CircleShape,
        border = if (glass) BorderStroke(1.dp, GlassBorder) else null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (main) BgColor else Label1,
                modifier = Modifier.size(if (main) 24.dp else if (size <= 44) 15.dp else 22.dp),
            )
        }
    }
}

// ============================================================================
// 5. SETTINGS SCREEN
// ============================================================================
@Composable
private fun SettingsScreen(
    settings: AppSettings,
    repository: AppSettingsRepository,
    onOpenCalibration: () -> Unit,
    onOpenRenderer: () -> Unit,
    onOpenAbout: () -> Unit,
    onReviewSafety: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var distance by remember(settings.defaultScreenDistanceMeters) {
        mutableStateOf(settings.defaultScreenDistanceMeters)
    }
    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        ScreenScroll {
            LargeTitle(title = "Settings")

            SectionHeader(title = "Comfort")
            GlassCard {
                ToggleRow(
                    title = "Comfort mode",
                    subtitle = "Slower fades, narrower FOV during seeks",
                    iconTint = GreenColor,
                    leadingIcon = Icons.Default.EventSeat,
                    checked = settings.comfortModeEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { repository.setComfortModeEnabled(enabled) }
                    },
                )
                RowDivider()
                SliderRow(
                    label = "Default screen distance",
                    valueLabel = "%.1f m".format(distance),
                    value = distance,
                    range = 3f..20f,
                    onValueChange = { distance = it },
                    onValueChangeFinished = {
                        scope.launch { repository.setDefaultScreenDistanceMeters(distance) }
                    },
                )
            }

            SectionHeader(title = "Library & storage")
            GlassCard {
                ListRow(
                    title = "Managed folders",
                    subtitle = "Tap to review folder access",
                    iconTint = AccentBlue,
                    leadingIcon = Icons.Default.Folder,
                    onClick = {},
                )
                RowDivider()
                ListRow(
                    title = "Clear playback positions",
                    subtitle = "Resets all resume points",
                    iconTint = SlateColor,
                    leadingIcon = Icons.Default.History,
                    onClick = {},
                )
            }

            SectionHeader(title = "VR")
            GlassCard {
                ListRow(
                    title = "Headset calibration",
                    subtitle = "IPD, lenses, distortion",
                    iconTint = VioletColor,
                    leadingIcon = Icons.Default.ViewInAr,
                    onClick = onOpenCalibration,
                )
                RowDivider()
                ListRow(
                    title = "Cinema & renderer",
                    subtitle = "Screen, picture, performance",
                    iconTint = TealColor,
                    leadingIcon = Icons.Default.Build,
                    onClick = onOpenRenderer,
                )
            }

            SectionHeader(title = "Safety")
            GlassCard {
                ListRow(
                    title = "Review safety guidance",
                    subtitle = "Re-show the first-run safety screen",
                    iconTint = AmberColor,
                    leadingIcon = Icons.Default.Security,
                    onClick = onReviewSafety,
                )
            }

            SectionHeader(title = "About")
            GlassCard {
                ListRow(
                    title = "About & device diagnostics",
                    subtitle = "Version, device, decoders",
                    iconTint = AccentBlue,
                    leadingIcon = Icons.Default.Info,
                    onClick = onOpenAbout,
                )
                RowDivider()
                ListRow(
                    title = "What's next",
                    subtitle = "Planned features",
                    iconTint = VioletColor,
                    leadingIcon = Icons.Default.Explore,
                    onClick = {},
                )
            }

            SectionHeader(title = "Appearance")
            GlassCard {
                NoIconRow(label = "Theme", value = "System")
                RowDivider(startIndent = 16)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reduce transparency", fontSize = 16.sp, color = Label1, fontWeight = FontWeight.W500)
                        Text("Coming soon", fontSize = 13.sp, color = Label2, modifier = Modifier.padding(top = 2.dp))
                    }
                    Switch(
                        checked = false,
                        onCheckedChange = {},
                        enabled = false,
                        colors = SwitchDefaults.colors(
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.30f),
                            disabledUncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                            disabledUncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                        ),
                    )
                }
            }
        }
    }
}

// ============================================================================
// 6. RENDERER SCREEN  (Cinema & Renderer)
// ============================================================================
@Composable
private fun RendererScreen(
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
    var sessionEnvironment by remember {
        mutableStateOf(SessionEnvironment(headsetMode = stereoPreviewEnabled, comfortModeEnabled = settings.comfortModeEnabled))
    }
    var draftConfig by remember { mutableStateOf(settings.cinemaScreenConfig) }
    val diagnostics = remember(telemetry, playbackState, sessionEnvironment, codecSummaries) {
        PerformanceDiagnosticsPolicy.evaluate(
            renderTelemetry = telemetry,
            playbackState = playbackState,
            environment = sessionEnvironment,
            codecs = codecSummaries,
        )
    }

    LaunchedEffect(settings.cinemaScreenConfig) { draftConfig = settings.cinemaScreenConfig }
    LaunchedEffect(draftConfig) { renderEngine.setCinemaScreenConfig(draftConfig) }
    LaunchedEffect(settings.stereoConfig, stereoPreviewEnabled) {
        renderEngine.setStereoConfig(settings.stereoConfig.copy(enabled = stereoPreviewEnabled))
    }
    LaunchedEffect(settings.videoProjection) { renderEngine.setVideoProjection(settings.videoProjection) }
    LaunchedEffect(settings.theatreSceneConfig) { renderEngine.setTheatreSceneConfig(settings.theatreSceneConfig) }
    LaunchedEffect(settings.headsetProfile) { renderEngine.setHeadsetProfile(settings.headsetProfile) }
    LaunchedEffect(stereoPreviewEnabled, settings.comfortModeEnabled) {
        while (true) {
            sessionEnvironment = AndroidSessionEnvironment.read(
                context = context,
                headsetMode = stereoPreviewEnabled,
                comfortModeEnabled = settings.comfortModeEnabled,
            )
            delay(30_000L)
        }
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
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) renderEngine.resume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            renderEngine.pause()
        }
    }
    DisposableEffect(renderEngine) { onDispose { renderEngine.release() } }

    var previewMode by rememberSaveable { mutableStateOf(0) }
    val aspectModes = ScreenAspectRatio.entries
    val cropModes = ScreenCropMode.entries

    Box(modifier = Modifier.fillMaxSize().background(BodyBg)) {
        ScreenScroll(topPadding = 76) {
            // Preview card
            GlassCard {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF1B2A4C), Color(0xFF0B101F)),
                                radius = 360f,
                            ),
                        ),
                ) {
                    AndroidView(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.68f)
                            .aspectRatio(16f / 9f),
                        factory = { ctx -> AuroraRenderView(ctx, renderEngine = renderEngine) },
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                        color = TealColor.copy(alpha = 0.13f),
                        shape = RoundedCornerShape(100.dp),
                    ) {
                        Text(
                            "Live preview",
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                            fontSize = 12.sp,
                            color = TealColor,
                            fontWeight = FontWeight.W600,
                        )
                    }
                }
                Segmented(
                    options = listOf("Mono", "Stereo", "Mesh"),
                    selectedIndex = previewMode,
                    onSelect = { idx ->
                        previewMode = idx
                        stereoPreviewEnabled = (idx == 1)
                        renderEngine.setDiagnosticMeshEnabled(idx == 2)
                    },
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                )
            }

            SectionHeader(title = "Screen geometry")
            GlassCard {
                ListRow(
                    title = "Aspect ratio",
                    iconTint = AccentBlue,
                    leadingIcon = Icons.Default.CropFree,
                    value = "${draftConfig.aspectRatioMode.label} ›",
                    onClick = {
                        val next = aspectModes[(aspectModes.indexOf(draftConfig.aspectRatioMode) + 1) % aspectModes.size]
                        draftConfig = draftConfig.copy(aspectRatioMode = next)
                        scope.launch { repository.setCinemaScreenConfig(draftConfig) }
                    },
                )
                RowDivider()
                ListRow(
                    title = "Crop mode",
                    iconTint = AccentBlue,
                    leadingIcon = Icons.Default.Fullscreen,
                    value = "${draftConfig.cropMode.label} ›",
                    onClick = {
                        val next = cropModes[(cropModes.indexOf(draftConfig.cropMode) + 1) % cropModes.size]
                        draftConfig = draftConfig.copy(cropMode = next)
                        scope.launch { repository.setCinemaScreenConfig(draftConfig) }
                    },
                )
                RowDivider()
                ToggleRow(
                    title = "Curved screen",
                    subtitle = "Gentle 20° cylindrical curve",
                    iconTint = AccentBlue,
                    leadingIcon = Icons.Default.RemoveRedEye,
                    checked = draftConfig.curvatureRadiusMeters > 0f,
                    onCheckedChange = { enabled ->
                        draftConfig = draftConfig.copy(curvatureRadiusMeters = if (enabled) 16f else 0f)
                        scope.launch { repository.setCinemaScreenConfig(draftConfig) }
                    },
                )
                RowDivider()
                SliderRow(
                    label = "Screen distance",
                    valueLabel = "%.1f m".format(draftConfig.distanceMeters),
                    value = draftConfig.distanceMeters,
                    range = 1f..6f,
                    onValueChange = { draftConfig = draftConfig.copy(distanceMeters = it) },
                    onValueChangeFinished = { scope.launch { repository.setCinemaScreenConfig(draftConfig) } },
                )
                RowDivider(startIndent = 16)
                SliderRow(
                    label = "Screen width",
                    valueLabel = "%.1f m".format(draftConfig.widthMeters),
                    value = draftConfig.widthMeters,
                    range = 1f..8f,
                    onValueChange = { draftConfig = draftConfig.copy(widthMeters = it) },
                    onValueChangeFinished = { scope.launch { repository.setCinemaScreenConfig(draftConfig) } },
                )
                RowDivider(startIndent = 16)
                SliderRow(
                    label = "Vertical offset",
                    valueLabel = "%.1f m".format(draftConfig.verticalOffsetMeters),
                    value = draftConfig.verticalOffsetMeters,
                    range = -2f..2f,
                    onValueChange = { draftConfig = draftConfig.copy(verticalOffsetMeters = it) },
                    onValueChangeFinished = { scope.launch { repository.setCinemaScreenConfig(draftConfig) } },
                )
                RowDivider(startIndent = 16)
                SliderRow(
                    label = "Tilt",
                    valueLabel = "${draftConfig.tiltDegrees.toInt()}°",
                    value = draftConfig.tiltDegrees,
                    range = -30f..30f,
                    onValueChange = { draftConfig = draftConfig.copy(tiltDegrees = it) },
                    onValueChangeFinished = { scope.launch { repository.setCinemaScreenConfig(draftConfig) } },
                )
            }

            SectionHeader(title = "Picture")
            GlassCard {
                SliderRow(
                    label = "Brightness",
                    valueLabel = "${(draftConfig.brightness * 100).toInt()}%",
                    value = draftConfig.brightness,
                    range = 0.2f..1.6f,
                    onValueChange = { draftConfig = draftConfig.copy(brightness = it) },
                    onValueChangeFinished = { scope.launch { repository.setCinemaScreenConfig(draftConfig) } },
                )
                RowDivider(startIndent = 16)
                SliderRow(
                    label = "Contrast",
                    valueLabel = "${(draftConfig.contrast * 100).toInt()}%",
                    value = draftConfig.contrast,
                    range = 0.2f..1.6f,
                    onValueChange = { draftConfig = draftConfig.copy(contrast = it) },
                    onValueChangeFinished = { scope.launch { repository.setCinemaScreenConfig(draftConfig) } },
                )
            }

            SectionHeader(title = "Performance")
            GlassCard {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
                    val rows = listOf(
                        listOf(
                            Triple("FRAME RATE", "%.1f".format(telemetry.framesPerSecond), "fps"),
                            Triple("FRAME TIME", "%.1f".format(telemetry.averageFrameTimeMs), "ms"),
                            Triple("BACKLOG", diagnostics.estimatedVideoFrameBacklog.toString(), "frames"),
                        ),
                        listOf(
                            Triple("RENDERED", telemetry.renderedFrames.toString(), ""),
                            Triple("EST. DROPS", telemetry.estimatedDroppedFrames.toString(), ""),
                            Triple("MODE", diagnostics.mode.label, ""),
                        ),
                    )
                    rows.forEachIndexed { idx, row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { (label, value, unit) ->
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        color = Label2,
                                        letterSpacing = 0.2.sp,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    if (label == "MODE") {
                                        Pill(text = value, color = AmberColor)
                                    } else {
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(
                                                text = value,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.W700,
                                                color = if (label == "EST. DROPS" && telemetry.estimatedDroppedFrames > 0) AmberColor else Label1,
                                            )
                                            if (unit.isNotEmpty()) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(text = unit, fontSize = 12.sp, color = Label2, fontWeight = FontWeight.W500)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (idx < rows.lastIndex) Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (diagnostics.warning != null) {
                Banner(
                    title = "Degradation policy active",
                    body = diagnostics.warning ?: "",
                    icon = Icons.Default.Warning,
                    tone = BannerTone.Warn,
                )
            }

            SectionHeader(title = "Renderer")
            GlassCard {
                NoIconRow(label = "GL renderer", value = telemetry.glRenderer.ifBlank { "Waiting" })
                RowDivider(startIndent = 16)
                NoIconRow(label = "GL version", value = telemetry.glVersion.ifBlank { "Waiting" })
                RowDivider(startIndent = 16)
                val passing = diagnostics.profilerChecklist.size
                ListRow(
                    title = "Release profiler checklist",
                    subtitle = "$passing checks tracked",
                    link = true,
                    onClick = {},
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            NavBar(onBack = onBack, title = "Cinema & Renderer")
        }
    }
}

// ============================================================================
// 7. CALIBRATION SCREEN
// ============================================================================
@Composable
private fun CalibrationScreen(
    settings: AppSettings,
    repository: AppSettingsRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(settings.stereoConfig) }
    var profile by remember { mutableStateOf(settings.headsetProfile) }

    LaunchedEffect(settings.stereoConfig) { draft = settings.stereoConfig }
    LaunchedEffect(settings.headsetProfile) { profile = settings.headsetProfile }

    val saveAll: () -> Unit = {
        scope.launch {
            repository.setStereoConfig(draft)
            repository.setHeadsetProfile(profile)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BodyBg)) {
        ScreenScroll(topPadding = 76) {
            LargeTitle(title = "Headset calibration", subtitle = "Profile · unsaved changes")

            SectionHeader(title = "Optics")
            GlassCard {
                SliderRow(
                    label = "IPD",
                    valueLabel = "${(profile.ipdMeters * 1000f).toInt()} mm",
                    value = profile.ipdMeters,
                    range = 0.054f..0.074f,
                    onValueChange = {
                        profile = profile.copy(ipdMeters = it)
                        draft = draft.copy(ipdMeters = it)
                    },
                    onValueChangeFinished = saveAll,
                )
                RowDivider(startIndent = 16)
                SliderRow(
                    label = "Field of view",
                    valueLabel = "${profile.fovDegrees.toInt()}°",
                    value = profile.fovDegrees,
                    range = 70f..120f,
                    onValueChange = {
                        profile = profile.copy(fovDegrees = it)
                        draft = draft.copy(fieldOfViewDegrees = it)
                    },
                    onValueChangeFinished = saveAll,
                )
                RowDivider(startIndent = 16)
                SliderRow(
                    label = "Lens center offset",
                    valueLabel = "%+.2f".format(profile.verticalLensOffset),
                    value = profile.verticalLensOffset,
                    range = -0.05f..0.05f,
                    onValueChange = { profile = profile.copy(verticalLensOffset = it) },
                    onValueChangeFinished = saveAll,
                )
            }

            SectionHeader(title = "Rendering path")
            GlassCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    val modes = StereoRenderMode.entries
                    Segmented(
                        options = modes.map { it.label },
                        selectedIndex = modes.indexOf(draft.renderMode).coerceAtLeast(0),
                        onSelect = { idx ->
                            draft = draft.copy(renderMode = modes[idx])
                            scope.launch { repository.setStereoConfig(draft) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(9.dp))
                    Text(
                        text = "Direct renders each eye straight to the display. Offscreen renders to a texture first — needed for barrel distortion correction.",
                        fontSize = 13.sp,
                        color = Label3,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        lineHeight = 19.sp,
                    )
                }
            }

            SectionHeader(title = "Barrel distortion")
            GlassCard {
                SliderRow(
                    label = "K1",
                    valueLabel = "%.2f".format(profile.distortionK1),
                    value = profile.distortionK1,
                    range = -1f..1f,
                    onValueChange = { profile = profile.copy(distortionK1 = it) },
                    onValueChangeFinished = saveAll,
                )
                RowDivider(startIndent = 16)
                SliderRow(
                    label = "K2",
                    valueLabel = "%.2f".format(profile.distortionK2),
                    value = profile.distortionK2,
                    range = -1f..1f,
                    onValueChange = { profile = profile.copy(distortionK2 = it) },
                    onValueChangeFinished = saveAll,
                )
                RowDivider(startIndent = 16)
                SliderRow(
                    label = "K3",
                    valueLabel = "%.2f".format(profile.distortionK3),
                    value = profile.distortionK3,
                    range = -1f..1f,
                    onValueChange = { profile = profile.copy(distortionK3 = it) },
                    onValueChangeFinished = saveAll,
                )
            }

            SectionHeader(title = "Chromatic aberration")
            GlassCard {
                SliderRow(
                    label = "Red shift",
                    valueLabel = "%+.3f".format(profile.chromaticAberrationRed),
                    value = profile.chromaticAberrationRed,
                    range = -0.02f..0.02f,
                    onValueChange = { profile = profile.copy(chromaticAberrationRed = it) },
                    onValueChangeFinished = saveAll,
                    labelColor = Color(0xFFE08A82),
                )
                RowDivider(startIndent = 16)
                SliderRow(
                    label = "Blue shift",
                    valueLabel = "%+.3f".format(profile.chromaticAberrationBlue),
                    value = profile.chromaticAberrationBlue,
                    range = -0.02f..0.02f,
                    onValueChange = { profile = profile.copy(chromaticAberrationBlue = it) },
                    onValueChangeFinished = saveAll,
                    labelColor = Color(0xFF7FB6E8),
                )
            }

            SectionHeader(title = "Profile")
            GlassCard {
                NoIconRow(label = "Profile name", value = "Custom")
                RowDivider(startIndent = 16)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Preset picker", fontSize = 16.sp, color = Label1, fontWeight = FontWeight.W500)
                        Text(
                            "Coming soon — choose from common headsets",
                            fontSize = 13.sp,
                            color = Label2,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Label3.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = "Save profile",
                    onClick = saveAll,
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    text = "Reset",
                    onClick = {
                        profile = com.aurora.cinema.render.HeadsetProfile()
                        draft = StereoConfig()
                        saveAll()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            NavBar(
                onBack = onBack,
                title = "Calibration",
                actions = { NavButton(icon = Icons.Default.Save, onClick = saveAll) },
            )
        }
    }
}

// ============================================================================
// 8. ABOUT SCREEN
// ============================================================================
@Composable
private fun AboutScreen(appContainer: AppContainer, onBack: () -> Unit) {
    val displayInfo = appContainer.deviceDisplayInfo.read()
    val codecs = appContainer.codecCapabilityService.summarizeDeviceCodecs()

    Box(modifier = Modifier.fillMaxSize().background(BodyBg)) {
        ScreenScroll(topPadding = 76) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF2E4B82), Color(0xFF22304F))),
                            RoundedCornerShape(19.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ViewInAr,
                        null,
                        tint = Color(0xFFCFE0FF),
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Aurora",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.W800,
                    color = Label1,
                    letterSpacing = (-0.4).sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text("Offline VR video player", fontSize = 13.sp, color = Label2)
            }

            SectionHeader(title = "App")
            GlassCard {
                NoIconRow(label = "Package", value = appContainer.applicationContext.packageName)
                RowDivider(startIndent = 16)
                NoIconRow(label = "Version", value = "1.0.0")
                RowDivider(startIndent = 16)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Native core", fontSize = 16.sp, color = Label1, fontWeight = FontWeight.W500, modifier = Modifier.weight(1f))
                    Pill(text = NativeCore.engineName(), color = GreenColor, showDot = true)
                }
            }

            SectionHeader(title = "Device")
            GlassCard {
                NoIconRow(label = "Model", value = displayInfo.deviceName)
                RowDivider(startIndent = 16)
                NoIconRow(label = "Android", value = "${displayInfo.androidVersion} (API ${Build.VERSION.SDK_INT})")
                RowDivider(startIndent = 16)
                NoIconRow(
                    label = "Display",
                    value = "${displayInfo.widthPixels} × ${displayInfo.heightPixels} · ${displayInfo.densityDpi} dpi",
                )
                if (displayInfo.refreshRate > 0f) {
                    RowDivider(startIndent = 16)
                    NoIconRow(label = "Refresh rate", value = "${displayInfo.refreshRate.toInt()} Hz")
                }
            }

            SectionHeader(title = "Decoders")
            GlassCard {
                codecs.forEachIndexed { idx, codec ->
                    val status = if (codec.supported) CodecPillStatus.Ok else CodecPillStatus.None
                    val badge = when (status) {
                        CodecPillStatus.Ok -> "HW"
                        CodecPillStatus.Sw -> "SW"
                        CodecPillStatus.None -> "N/A"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(codec.codecFamily, fontSize = 16.sp, color = Label1, fontWeight = FontWeight.W500)
                            Text(
                                text = codec.decoderName.ifBlank { "No decoder found on this device" },
                                fontSize = 13.sp,
                                color = Label2,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        CodecBadge(label = badge, status = status)
                    }
                    if (idx < codecs.lastIndex) RowDivider(startIndent = 16)
                }
            }

            SectionHeader(title = "Diagnostics")
            GlassCard {
                ListRow(
                    title = "GL & renderer info",
                    subtitle = "Opens Cinema & Renderer",
                    link = true,
                    onClick = {},
                )
                RowDivider(startIndent = 16)
                ListRow(
                    title = "Copy build & runtime diagnostics",
                    subtitle = "For bug reports",
                    link = true,
                    trailing = {
                        Icon(Icons.Default.ContentCopy, null, tint = Label3, modifier = Modifier.size(18.dp))
                    },
                    onClick = {},
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            NavBar(onBack = onBack, title = "About")
        }
    }
}

// ============================================================================
// VR SYSTEM UI EFFECT
// ============================================================================
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
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            onDispose {
                activity?.window?.let { window ->
                    WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                }
                if (previousOrientation != null) activity.requestedOrientation = previousOrientation
            }
        }
    }
}

// ============================================================================
// APP SCREEN ENUM & SUPPORT
// ============================================================================
private enum class AppScreen(
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

private val primaryScreens = listOf(
    AppScreen.Library,
    AppScreen.Player,
    AppScreen.Settings,
)

// ============================================================================
// EXTENSION HELPERS
// ============================================================================
private fun VideoItem.cardGradient(): Brush {
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

private fun VideoItem.codecBadge(): String {
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

private fun VideoItem.codecStatus(): CodecPillStatus = when (probeResult.supportStatus) {
    CodecSupportStatus.Supported -> CodecPillStatus.Ok
    CodecSupportStatus.Risky -> CodecPillStatus.Sw
    CodecSupportStatus.Unsupported, CodecSupportStatus.Unknown -> CodecPillStatus.None
}

private fun VideoItem.resolutionLabel(): String =
    if (width > 0 && height > 0) "$width × $height" else "Unknown"

private fun VideoItem.resolutionShort(): String = when {
    width <= 0 || height <= 0 -> "Unknown"
    height >= 2160 -> "4K"
    height >= 1440 -> "1440p"
    height >= 1080 -> "1080p"
    height >= 720 -> "720p"
    else -> "${height}p"
}

private fun VideoItem.fullDurationLabel(): String {
    if (durationMs <= 0L) return "Unknown"
    val totalSeconds = durationMs / 1000L
    val h = totalSeconds / 3600L
    val m = (totalSeconds % 3600L) / 60L
    val s = totalSeconds % 60L
    return if (h > 0) "%d h %02d m %02d s".format(h, m, s) else "%d m %02d s".format(m, s)
}

private fun VideoItem.fileSizeLabel(): String {
    // VideoItem doesn't track file size in this codebase; estimate from bitrate × duration when available.
    val bytes = (probeResult.bitrate.coerceAtLeast(0L) / 8L) * (durationMs / 1000L)
    if (bytes <= 0L) return "Unknown"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) "%.1f GB".format(gb) else "%.0f MB".format(bytes / (1024.0 * 1024.0))
}

private fun VideoItem.progressFraction(): Float {
    if (durationMs <= 0L) return 0f
    return (playbackPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

private fun Long.timeLabel(): String {
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
private fun AuroraAppPreview() {
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
