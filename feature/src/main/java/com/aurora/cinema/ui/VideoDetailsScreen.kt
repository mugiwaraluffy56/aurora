package com.aurora.cinema.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.cinema.library.VideoAccessState
import com.aurora.cinema.library.VideoItem
import com.aurora.cinema.playback.PlaybackState

@Composable
internal fun VideoDetailsScreen(
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
                Text("No video selected", fontSize = 22.sp, fontWeight = FontWeight.W700, color = Label1,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text("Open a title from your library to see its media details.",
                    fontSize = 15.sp, color = Label2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                return@ScreenScroll
            }

            GlassCard {
                Box(modifier = Modifier.fillMaxWidth().height(168.dp).background(video.cardGradient()).clip(CardShape),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Movie, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(30.dp))
                    Surface(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 7.dp, bottom = 10.dp),
                        color = BgColor.copy(alpha = 0.6f), shape = RoundedCornerShape(6.dp)) {
                        Text(video.durationMs.timeLabel(), modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            fontSize = 11.sp, fontWeight = FontWeight.W600, color = Label1)
                    }
                    Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.16f))) {
                        Box(modifier = Modifier.fillMaxWidth(video.progressFraction()).height(3.dp).background(AccentBlue))
                    }
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 17.dp)) {
                    Text(video.displayName, fontSize = 22.sp, fontWeight = FontWeight.W800, color = Label1, letterSpacing = (-0.4).sp)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(video.uri, fontSize = 13.sp, color = Label2)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        val status = video.codecStatus()
                        val pillColor = when (status) { CodecPillStatus.Ok -> GreenColor; CodecPillStatus.Sw -> AmberColor; CodecPillStatus.None -> RedColor }
                        Pill(text = "${video.codecBadge()} · ${when (status) { CodecPillStatus.Ok -> "hardware decode"; CodecPillStatus.Sw -> "software decode"; CodecPillStatus.None -> "no decoder" }}",
                            color = pillColor, showDot = true)
                        if (video.playbackPositionMs > 0L)
                            Pill(text = "Resume at ${video.playbackPositionMs.timeLabel()}", color = TealColor, icon = Icons.Outlined.Schedule)
                    }
                    Spacer(modifier = Modifier.height(15.dp))
                    PrimaryButton(text = "Play", icon = Icons.Default.PlayArrow,
                        enabled = video.accessState == VideoAccessState.Available, onClick = { onPlay(video) })
                }
            }

            SectionHeader(title = "File")
            GlassCard {
                NoIconRow("Duration", video.fullDurationLabel()); RowDivider(startIndent = 16)
                NoIconRow("Resolution", video.resolutionLabel()); RowDivider(startIndent = 16)
                NoIconRow("File size", video.fileSizeLabel()); RowDivider(startIndent = 16)
                NoIconRow("Container", video.mimeType.ifBlank { "Unknown" }); RowDivider(startIndent = 16)
                NoIconRow("Video codec", "${video.probeResult.codecFamily}${if (video.probeResult.frameRate > 0f) " · ${video.probeResult.frameRate.toInt()} fps" else ""}"); RowDivider(startIndent = 16)
                NoIconRow("Audio", video.probeResult.profileLevel.ifBlank { "Unknown" })
            }

            SectionHeader(title = "Decoder")
            when (video.codecStatus()) {
                CodecPillStatus.Ok -> Banner("Hardware decoding available",
                    "${video.probeResult.decoderName.ifBlank { "Hardware decoder" }} supports this stream.", Icons.Default.Check, BannerTone.Ok)
                CodecPillStatus.Sw -> Banner("Software decoding will be used",
                    "No hardware decoder matched. May use more battery.", Icons.Default.Warning, BannerTone.Warn)
                CodecPillStatus.None -> Banner("No decoder found",
                    "This codec isn't supported. Try a different file.", Icons.Default.Warning, BannerTone.Error)
            }

            SectionHeader(title = "Manage")
            GlassCard {
                ListRow("Refresh metadata", "Re-probe duration, streams, and codec support", link = true,
                    trailing = { Icon(Icons.Default.Refresh, null, tint = Label3, modifier = Modifier.size(18.dp)) }, onClick = onRefresh)
                RowDivider(startIndent = 16)
                ListRow("Rename display title", "Changes how it appears in the library only", link = true,
                    trailing = { Icon(Icons.Default.Edit, null, tint = Label3, modifier = Modifier.size(18.dp)) }, onClick = {})
                RowDivider(startIndent = 16)
                ListRow("Remove from library", "The source file on your device is kept", danger = true,
                    trailing = { Icon(Icons.Default.Delete, null, tint = RedColor, modifier = Modifier.size(18.dp)) },
                    onClick = { onDelete(video.id) })
            }
        }

        Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            NavBar(onBack = onBack, actions = { NavButton(icon = Icons.Default.Edit, onClick = {}) })
        }
    }
}
