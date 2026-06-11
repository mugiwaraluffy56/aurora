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
import com.aurora.cinema.render.HeadsetProfile
import com.aurora.cinema.render.StereoConfig
import com.aurora.cinema.render.StereoRenderMode
import com.aurora.cinema.settings.AppSettings
import com.aurora.cinema.settings.AppSettingsRepository
import kotlinx.coroutines.launch

@Composable
internal fun CalibrationScreen(
    settings: AppSettings,
    repository: AppSettingsRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(settings.stereoConfig) }
    var profile by remember { mutableStateOf(settings.headsetProfile) }

    LaunchedEffect(settings.stereoConfig) { draft = settings.stereoConfig }
    LaunchedEffect(settings.headsetProfile) { profile = settings.headsetProfile }

    val saveAll: () -> Unit = { scope.launch { repository.setStereoConfig(draft); repository.setHeadsetProfile(profile) } }

    Box(modifier = Modifier.fillMaxSize().background(BodyBg)) {
        ScreenScroll(topPadding = 76) {
            LargeTitle(title = "Headset calibration", subtitle = "Profile · unsaved changes")

            SectionHeader(title = "Optics")
            GlassCard {
                SliderRow("IPD", "${(profile.ipdMeters * 1000f).toInt()} mm", profile.ipdMeters, 0.054f..0.074f,
                    onValueChange = { profile = profile.copy(ipdMeters = it); draft = draft.copy(ipdMeters = it) }, onValueChangeFinished = saveAll)
                RowDivider(startIndent = 16)
                SliderRow("Field of view", "${profile.fovDegrees.toInt()}°", profile.fovDegrees, 70f..120f,
                    onValueChange = { profile = profile.copy(fovDegrees = it); draft = draft.copy(fieldOfViewDegrees = it) }, onValueChangeFinished = saveAll)
                RowDivider(startIndent = 16)
                SliderRow("Lens center offset", "%+.2f".format(profile.verticalLensOffset), profile.verticalLensOffset, -0.05f..0.05f,
                    onValueChange = { profile = profile.copy(verticalLensOffset = it) }, onValueChangeFinished = saveAll)
            }

            SectionHeader(title = "Rendering path")
            GlassCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    val modes = StereoRenderMode.entries
                    Segmented(options = modes.map { it.label }, selectedIndex = modes.indexOf(draft.renderMode).coerceAtLeast(0),
                        onSelect = { idx -> draft = draft.copy(renderMode = modes[idx]); scope.launch { repository.setStereoConfig(draft) } }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(9.dp))
                    Text("Direct renders each eye straight to the display. Offscreen renders to a texture first — needed for barrel distortion correction.",
                        fontSize = 13.sp, color = Label3, modifier = Modifier.padding(horizontal = 4.dp), lineHeight = 19.sp)
                }
            }

            SectionHeader(title = "Barrel distortion")
            GlassCard {
                SliderRow("K1", "%.2f".format(profile.distortionK1), profile.distortionK1, -1f..1f, onValueChange = { profile = profile.copy(distortionK1 = it) }, onValueChangeFinished = saveAll)
                RowDivider(startIndent = 16)
                SliderRow("K2", "%.2f".format(profile.distortionK2), profile.distortionK2, -1f..1f, onValueChange = { profile = profile.copy(distortionK2 = it) }, onValueChangeFinished = saveAll)
                RowDivider(startIndent = 16)
                SliderRow("K3", "%.2f".format(profile.distortionK3), profile.distortionK3, -1f..1f, onValueChange = { profile = profile.copy(distortionK3 = it) }, onValueChangeFinished = saveAll)
            }

            SectionHeader(title = "Chromatic aberration")
            GlassCard {
                SliderRow("Red shift", "%+.3f".format(profile.chromaticAberrationRed), profile.chromaticAberrationRed, -0.02f..0.02f,
                    onValueChange = { profile = profile.copy(chromaticAberrationRed = it) }, onValueChangeFinished = saveAll, labelColor = Color(0xFFE08A82))
                RowDivider(startIndent = 16)
                SliderRow("Blue shift", "%+.3f".format(profile.chromaticAberrationBlue), profile.chromaticAberrationBlue, -0.02f..0.02f,
                    onValueChange = { profile = profile.copy(chromaticAberrationBlue = it) }, onValueChangeFinished = saveAll, labelColor = Color(0xFF7FB6E8))
            }

            SectionHeader(title = "Profile")
            GlassCard {
                NoIconRow("Profile name", "Custom"); RowDivider(startIndent = 16)
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Preset picker", fontSize = 16.sp, color = Label1, fontWeight = FontWeight.W500)
                        Text("Coming soon — choose from common headsets", fontSize = 13.sp, color = Label2, modifier = Modifier.padding(top = 2.dp))
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Label3.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(text = "Save profile", onClick = saveAll, modifier = Modifier.weight(1f))
                GlassButton(text = "Reset", onClick = { profile = HeadsetProfile(); draft = StereoConfig(); saveAll() }, modifier = Modifier.weight(1f))
            }
        }

        Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            NavBar(onBack = onBack, title = "Calibration", actions = { NavButton(icon = Icons.Default.Save, onClick = saveAll) })
        }
    }
}
