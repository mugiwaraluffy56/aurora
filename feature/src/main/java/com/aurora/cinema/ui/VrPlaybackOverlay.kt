package com.aurora.cinema.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.sqrt
import com.aurora.cinema.playback.SubtitleSettings
import com.aurora.cinema.render.CinemaScreenConfig
import kotlinx.coroutines.delay

@Composable
fun VrPlaybackOverlay(
    viewMatrix: FloatArray,
    tracking: Boolean,
    sensorName: String,
    isPlaying: Boolean,
    progressLabel: String,
    subtitleText: String,
    subtitleSettings: SubtitleSettings,
    controlsLocked: Boolean,
    onControlsLockedChange: (Boolean) -> Unit,
    onTarget: (VrGazeTarget) -> Unit,
    screenConfig: CinemaScreenConfig = CinemaScreenConfig(),
    onScreenWidthChange: (Float) -> Unit = {},
    onScreenDistanceChange: (Float) -> Unit = {},
    positionMs: Long = 0L,
    durationMs: Long = 1L,
    onSeek: (Long) -> Unit = {},
) {
    var showControls by remember { mutableStateOf(false) }
    var recenterFlash by remember { mutableStateOf(false) }

    // #7 Head-locked UI: extract yaw/pitch from viewMatrix → screen-space offset
    // viewMatrix is column-major. Forward = -col2 = (-m[8], -m[9], -m[10])
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current.density
    val fovRad = (screenWidthDp / 360f) * (2f * Math.PI.toFloat()) * 0.5f // rough per-eye FOV
    val headLockOffsetX: Float
    val headLockOffsetY: Float
    if (viewMatrix.size == 16) {
        val fx = -viewMatrix[8]; val fy = -viewMatrix[9]; val fz = -viewMatrix[10]
        val yaw   = atan2(fx, fz)
        val pitch = atan2(fy, sqrt(fx * fx + fz * fz))
        val pxPerRad = (screenWidthDp * density) / (fovRad * 2f)
        headLockOffsetX = -yaw * pxPerRad
        headLockOffsetY =  pitch * pxPerRad
    } else {
        headLockOffsetX = 0f; headLockOffsetY = 0f
    }

    LaunchedEffect(showControls) {
        if (showControls) { delay(6000); showControls = false }
    }
    LaunchedEffect(recenterFlash) {
        if (recenterFlash) { delay(500); recenterFlash = false }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = {
                        onTarget(VrGazeTarget.Recenter)
                        recenterFlash = true
                        showControls = false
                    },
                )
            },
    ) {
        // Recenter flash
        if (recenterFlash) {
            Box(modifier = Modifier.align(Alignment.Center).size(64.dp)
                .background(Color.White.copy(alpha = 0.18f), CircleShape))
        }

        // Gaze cursor
        Box(modifier = Modifier.align(Alignment.Center).size(7.dp)
            .background(Color.White.copy(alpha = 0.60f), CircleShape))

        // Subtitle — head-locked
        if (subtitleSettings.enabled && subtitleText.isNotBlank()) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .graphicsLayer { translationX = headLockOffsetX; translationY = headLockOffsetY * 0.3f }
                    .padding(horizontal = 24.dp, vertical = 56.dp).widthIn(max = 720.dp),
                color = Color.Black.copy(alpha = 0.62f), shape = RoundedCornerShape(12.dp),
            ) {
                Text(subtitleText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.White, fontSize = (18f * subtitleSettings.sizeScale).sp)
            }
        }

        // Compact control bar — head-locked
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.BottomCenter)
                .graphicsLayer { translationX = headLockOffsetX; translationY = headLockOffsetY * 0.5f },
            enter = slideInVertically(tween(200)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(160)),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
                color = Color.Black.copy(alpha = 0.78f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {

                    // Row 1: timeline
                    val dur = durationMs.coerceAtLeast(1L)
                    val pos = positionMs.coerceIn(0L, dur)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(pos.timeLabel(), fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f), fontWeight = FontWeight.W500)
                        Slider(modifier = Modifier.weight(1f), value = pos.toFloat(),
                            onValueChange = { onSeek(it.toLong()); showControls = true },
                            valueRange = 0f..dur.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = Color.White,
                                activeTrackColor = Color(0xFF549BFF), inactiveTrackColor = Color.White.copy(alpha = 0.20f)))
                        Text("-${(dur - pos).coerceAtLeast(0L).timeLabel()}", fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.55f), fontWeight = FontWeight.W500)
                    }

                    // Row 2: [transport] [W slider] [D slider] [recenter][exit]
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Transport
                        VrCtrlBtn(Icons.Default.Replay10, size = 36) { onTarget(VrGazeTarget.Back) }
                        VrCtrlBtn(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, size = 44, accent = true) { onTarget(VrGazeTarget.PlayPause) }
                        VrCtrlBtn(Icons.Default.Forward10, size = 36) { onTarget(VrGazeTarget.Forward) }
                        Spacer(Modifier.width(2.dp))
                        // W slider
                        Text("W", fontSize = 10.sp, color = Color.White.copy(alpha = 0.40f), fontWeight = FontWeight.W700)
                        Slider(modifier = Modifier.weight(1f), value = screenConfig.widthMeters,
                            onValueChange = { onScreenWidthChange(it); showControls = true }, valueRange = 8f..30f,
                            colors = SliderDefaults.colors(thumbColor = Color.White,
                                activeTrackColor = Color(0xFF549BFF).copy(alpha = 0.75f), inactiveTrackColor = Color.White.copy(alpha = 0.15f)))
                        // D slider
                        Text("D", fontSize = 10.sp, color = Color.White.copy(alpha = 0.40f), fontWeight = FontWeight.W700)
                        Slider(modifier = Modifier.weight(1f), value = screenConfig.distanceMeters,
                            onValueChange = { onScreenDistanceChange(it); showControls = true }, valueRange = 2f..20f,
                            colors = SliderDefaults.colors(thumbColor = Color.White,
                                activeTrackColor = Color(0xFF549BFF).copy(alpha = 0.75f), inactiveTrackColor = Color.White.copy(alpha = 0.15f)))
                        Spacer(Modifier.width(2.dp))
                        // Actions
                        VrCtrlBtn(Icons.Default.CenterFocusStrong, size = 36) { onTarget(VrGazeTarget.Recenter); recenterFlash = true; showControls = false }
                        VrCtrlBtn(Icons.Default.Close, size = 36, danger = true) { onTarget(VrGazeTarget.Exit) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VrCtrlBtn(
    icon: ImageVector,
    size: Int = 36,
    accent: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(size.dp).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        color = when { accent -> Color.White.copy(alpha = 0.92f); danger -> Color(0xFFE36A60).copy(alpha = 0.80f); else -> Color.White.copy(alpha = 0.10f) },
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (accent) Color(0xFF0A0B0E) else Color.White,
                modifier = Modifier.size((size * 0.48f).dp))
        }
    }
}
