package com.aurora.cinema.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.cinema.settings.AppSettings
import com.aurora.cinema.settings.AppSettingsRepository
import kotlinx.coroutines.launch

@Composable
internal fun SettingsScreen(
    settings: AppSettings,
    repository: AppSettingsRepository,
    onOpenCalibration: () -> Unit,
    onOpenRenderer: () -> Unit,
    onOpenAbout: () -> Unit,
    onReviewSafety: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var distance by remember(settings.defaultScreenDistanceMeters) { mutableStateOf(settings.defaultScreenDistanceMeters) }

    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        ScreenScroll {
            LargeTitle(title = "Settings")

            SectionHeader(title = "Comfort")
            GlassCard {
                ToggleRow("Comfort mode", "Slower fades, narrower FOV during seeks",
                    iconTint = GreenColor, leadingIcon = Icons.Default.EventSeat,
                    checked = settings.comfortModeEnabled,
                    onCheckedChange = { scope.launch { repository.setComfortModeEnabled(it) } })
                RowDivider()
                SliderRow("Default screen distance", "%.1f m".format(distance), distance, 3f..20f,
                    onValueChange = { distance = it },
                    onValueChangeFinished = { scope.launch { repository.setDefaultScreenDistanceMeters(distance) } })
            }

            SectionHeader(title = "Library & storage")
            GlassCard {
                ListRow("Managed folders", "Tap to review folder access", iconTint = AccentBlue, leadingIcon = Icons.Default.Folder, onClick = {})
                RowDivider()
                ListRow("Clear playback positions", "Resets all resume points", iconTint = SlateColor, leadingIcon = Icons.Default.History, onClick = {})
            }

            SectionHeader(title = "VR")
            GlassCard {
                ListRow("Headset calibration", "IPD, lenses, distortion", iconTint = VioletColor, leadingIcon = Icons.Default.ViewInAr, onClick = onOpenCalibration)
                RowDivider()
                ListRow("Cinema & renderer", "Screen, picture, performance", iconTint = TealColor, leadingIcon = Icons.Default.Build, onClick = onOpenRenderer)
            }

            SectionHeader(title = "Safety")
            GlassCard {
                ListRow("Review safety guidance", "Re-show the first-run safety screen", iconTint = AmberColor, leadingIcon = Icons.Default.Security, onClick = onReviewSafety)
            }

            SectionHeader(title = "About")
            GlassCard {
                ListRow("About & device diagnostics", "Version, device, decoders", iconTint = AccentBlue, leadingIcon = Icons.Default.Info, onClick = onOpenAbout)
                RowDivider()
                ListRow("What's next", "Planned features", iconTint = VioletColor, leadingIcon = Icons.Default.Explore, onClick = {})
            }

            SectionHeader(title = "Appearance")
            GlassCard {
                NoIconRow("Theme", "System")
                RowDivider(startIndent = 16)
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reduce transparency", fontSize = 16.sp, color = Label1, fontWeight = FontWeight.W500)
                        Text("Coming soon", fontSize = 13.sp, color = Label2, modifier = Modifier.padding(top = 2.dp))
                    }
                    Switch(checked = false, onCheckedChange = {}, enabled = false,
                        colors = SwitchDefaults.colors(
                            disabledUncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                            disabledUncheckedTrackColor = Color.White.copy(alpha = 0.12f)))
                }
            }
        }
    }
}
