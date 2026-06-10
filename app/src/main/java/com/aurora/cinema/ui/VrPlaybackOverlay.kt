package com.aurora.cinema.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.cinema.playback.SubtitleSettings
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
) {
    val dwellSelector = remember { VrDwellSelector() }
    var dwellSelection by remember { mutableStateOf(DwellSelection()) }
    var overlayVisible by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(viewMatrix, controlsLocked, overlayVisible) {
        while (true) {
            val target = if (controlsLocked || !overlayVisible) {
                VrGazeTarget.None
            } else {
                VrGazeMapper.targetFromViewMatrix(viewMatrix)
            }
            val nextSelection = dwellSelector.update(target, System.currentTimeMillis())
            dwellSelection = nextSelection
            if (nextSelection.fired != VrGazeTarget.None) {
                if (nextSelection.fired == VrGazeTarget.LockControls) {
                    onControlsLockedChange(!controlsLocked)
                }
                onTarget(nextSelection.fired)
            }
            delay(100L)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (overlayVisible) {
            VrPlaybackControls(
                isPlaying = isPlaying,
                progressLabel = progressLabel,
                dwellSelection = dwellSelection,
                onTarget = onTarget,
            )
        }
        VrGazeCursor(locked = controlsLocked)
        VrSubtitleLayer(
            subtitleText = subtitleText,
            settings = subtitleSettings,
        )
        VrSystemControls(
            controlsLocked = controlsLocked,
            dwellSelection = dwellSelection,
            onToggleOverlay = { overlayVisible = !overlayVisible },
            onToggleLock = {
                onControlsLockedChange(!controlsLocked)
                onTarget(VrGazeTarget.LockControls)
            },
            onTarget = onTarget,
        )
        VrStatusPill(
            tracking = tracking,
            sensorName = sensorName,
            controlsLocked = controlsLocked,
            selection = dwellSelection,
        )
    }
}

@Composable
private fun BoxScope.VrSubtitleLayer(
    subtitleText: String,
    settings: SubtitleSettings,
) {
    if (!settings.enabled || subtitleText.isBlank()) return
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(
                start = 24.dp,
                end = 24.dp,
                bottom = (92 + (settings.verticalOffset * 120f).toInt()).dp,
            )
            .widthIn(max = 720.dp),
        color = Color.Black.copy(alpha = 0.62f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = subtitleText,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = (20f * settings.sizeScale).sp),
        )
    }
}

@Composable
private fun BoxScope.VrPlaybackControls(
    isPlaying: Boolean,
    progressLabel: String,
    dwellSelection: DwellSelection,
    onTarget: (VrGazeTarget) -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VrGazeButton(VrGazeTarget.Back, dwellSelection, Icons.Default.Replay10, "Back", onTarget)
            VrGazeButton(
                target = VrGazeTarget.PlayPause,
                selection = dwellSelection,
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                label = if (isPlaying) "Pause" else "Play",
                onTarget = onTarget,
            )
            VrGazeButton(VrGazeTarget.Forward, dwellSelection, Icons.Default.Forward10, "Forward", onTarget)
        }
        Text(
            text = progressLabel,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VrGazeButton(VrGazeTarget.ScreenSmaller, dwellSelection, Icons.Default.Tv, "Smaller", onTarget)
            VrGazeButton(VrGazeTarget.Timeline, dwellSelection, Icons.Default.PlayCircle, "Middle", onTarget)
            VrGazeButton(VrGazeTarget.ScreenLarger, dwellSelection, Icons.Default.Fullscreen, "Larger", onTarget)
        }
    }
}

@Composable
private fun BoxScope.VrGazeCursor(locked: Boolean) {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(10.dp)
            .background(Color.White.copy(alpha = if (locked) 0.22f else 0.72f), CircleShape),
    )
}

@Composable
private fun BoxScope.VrSystemControls(
    controlsLocked: Boolean,
    dwellSelection: DwellSelection,
    onToggleOverlay: () -> Unit,
    onToggleLock: () -> Unit,
    onTarget: (VrGazeTarget) -> Unit,
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VrGazeButton(VrGazeTarget.Recenter, dwellSelection, Icons.Default.CenterFocusStrong, "Recenter", onTarget)
        VrGazeButton(
            target = VrGazeTarget.LockControls,
            selection = dwellSelection,
            icon = Icons.Default.Settings,
            label = if (controlsLocked) "Unlock" else "Lock",
            onTarget = { onToggleLock() },
        )
        VrGazeButton(VrGazeTarget.Timeline, dwellSelection, Icons.Default.PlayCircle, "Overlay", onTarget = {
            onToggleOverlay()
        })
        VrGazeButton(VrGazeTarget.Exit, dwellSelection, Icons.Default.Close, "Exit", onTarget)
    }
}

@Composable
private fun BoxScope.VrStatusPill(
    tracking: Boolean,
    sensorName: String,
    controlsLocked: Boolean,
    selection: DwellSelection,
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            text = if (controlsLocked) {
                "Controls locked"
            } else if (tracking) {
                "Gaze ${selection.target.name.lowercase()} ${(selection.progress * 100).toInt()}%"
            } else {
                sensorName
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VrGazeButton(
    target: VrGazeTarget,
    selection: DwellSelection,
    icon: ImageVector,
    label: String,
    onTarget: (VrGazeTarget) -> Unit,
) {
    val active = selection.target == target
    val semanticLabel = "${target.name}: $label"
    Surface(
        modifier = Modifier.widthIn(min = 82.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (active) 0.9f else 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (active) 0.95f else 0.22f),
        ),
    ) {
        Column(
            modifier = Modifier
                .clickable { onTarget(target) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = semanticLabel, modifier = Modifier.size(24.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(selection.progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}
