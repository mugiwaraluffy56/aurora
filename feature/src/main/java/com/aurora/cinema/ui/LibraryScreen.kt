package com.aurora.cinema.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.cinema.library.OfflineLibraryRepository
import com.aurora.cinema.library.VideoAccessState
import com.aurora.cinema.library.VideoItem
import kotlinx.coroutines.launch

@Composable
internal fun LibraryScreen(
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
    val continueWatching = videos.filter { it.playbackPositionMs > 0L && !it.playbackCompleted }
        .sortedByDescending { it.playbackUpdatedAt }.take(6)
    val recentVideos = videos.sortedByDescending { it.lastSeenAt }.take(4)
    val missing = videos.count { it.accessState == VideoAccessState.Missing }
    val sessionOnly = videos.count { it.accessState == VideoAccessState.Available && !it.persistedPermission }

    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        ScreenScroll {
            LargeTitle(title = "Library", subtitle = "${videos.size} ${if (videos.size == 1) "video" else "videos"} on device")
            SearchBar(value = query, onValueChange = { query = it })

            SectionHeader(title = "Sort")
            Segmented(options = sortOptions, selectedIndex = sortIndex, onSelect = { sortIndex = it }, modifier = Modifier.fillMaxWidth())

            if (continueWatching.isNotEmpty()) {
                SectionHeader(title = "Continue watching", action = "Resume", onActionClick = onResume)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Movie, null, tint = Label2, modifier = Modifier.size(26.dp))
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("No videos yet", fontSize = 17.sp, fontWeight = FontWeight.W700, color = Label1)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Import a video file or a whole folder to start building your library.",
                            fontSize = 13.sp, color = Label2, textAlign = TextAlign.Center, lineHeight = 19.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        PrimaryButton(text = "Import video", onClick = { filePicker.launch(arrayOf("video/*")) },
                            modifier = Modifier.widthIn(max = 220.dp))
                    }
                }
            } else {
                GlassCard {
                    recentVideos.forEachIndexed { idx, v ->
                        val tint = listOf(AccentBlue, VioletColor, TealColor, SlateColor)[idx % 4]
                        ListRow(title = v.displayName, subtitle = "${v.durationMs.timeLabel()} · ${v.resolutionLabel()}",
                            iconTint = tint, leadingIcon = Icons.Default.Movie,
                            valuePill = { CodecBadge(label = v.codecBadge(), status = v.codecStatus()) },
                            onClick = { onOpenDetails(v.id) })
                        if (idx < recentVideos.lastIndex) RowDivider()
                    }
                }
            }

            if (missing > 0 || sessionOnly > 0) {
                SectionHeader(title = "Needs attention")
                if (missing > 0) Banner(
                    title = "$missing file${if (missing == 1) "" else "s"} can't be found",
                    body = "Files were moved or deleted. Locate them or remove from library.",
                    icon = Icons.Default.Warning, tone = BannerTone.Warn, modifier = Modifier.padding(bottom = 10.dp))
                if (sessionOnly > 0) Banner(
                    title = "Folder access expired",
                    body = "Permission was revoked. Re-grant access to keep $sessionOnly video${if (sessionOnly == 1) "" else "s"} available.",
                    icon = Icons.Default.Lock, tone = BannerTone.Error)
            }
        }

        Row(modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 16.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NavButton(icon = Icons.Default.Add, onClick = { filePicker.launch(arrayOf("video/*")) })
            NavButton(icon = Icons.Default.Folder, onClick = { folderPicker.launch(null) })
        }
    }
}

@Composable
internal fun VCard(title: String, subtitle: String, duration: String, progress: Float, gradient: Brush, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(158.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        color = GlassFill, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, GlassBorder),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(90.dp).background(gradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Movie, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
                Surface(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 7.dp, bottom = 10.dp),
                    color = BgColor.copy(alpha = 0.6f), shape = RoundedCornerShape(6.dp)) {
                    Text(text = duration, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.W600, color = Label1)
                }
                Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.16f))) {
                    Box(modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(AccentBlue))
                }
            }
            Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.W600, color = Label1,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.1).sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 11.sp, color = Label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
