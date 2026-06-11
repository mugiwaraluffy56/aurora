package com.aurora.cinema.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.cinema.app.AppContainer
import com.aurora.cinema.core.nativebridge.NativeCore

@Composable
internal fun AboutScreen(appContainer: AppContainer, onBack: () -> Unit) {
    val displayInfo = appContainer.deviceDisplayInfo.read()
    val codecs = appContainer.codecCapabilityService.summarizeDeviceCodecs()

    Box(modifier = Modifier.fillMaxSize().background(BodyBg)) {
        ScreenScroll(topPadding = 76) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(68.dp).background(
                    Brush.linearGradient(listOf(Color(0xFF2E4B82), Color(0xFF22304F))), RoundedCornerShape(19.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ViewInAr, null, tint = Color(0xFFCFE0FF), modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Aurora", fontSize = 22.sp, fontWeight = FontWeight.W800, color = Label1, letterSpacing = (-0.4).sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Offline VR video player", fontSize = 13.sp, color = Label2)
            }

            SectionHeader(title = "App")
            GlassCard {
                NoIconRow("Package", appContainer.applicationContext.packageName); RowDivider(startIndent = 16)
                NoIconRow("Version", "1.0.0"); RowDivider(startIndent = 16)
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Native core", fontSize = 16.sp, color = Label1, fontWeight = FontWeight.W500, modifier = Modifier.weight(1f))
                    Pill(text = NativeCore.engineName(), color = GreenColor, showDot = true)
                }
            }

            SectionHeader(title = "Device")
            GlassCard {
                NoIconRow("Model", displayInfo.deviceName); RowDivider(startIndent = 16)
                NoIconRow("Android", "${displayInfo.androidVersion} (API ${Build.VERSION.SDK_INT})"); RowDivider(startIndent = 16)
                NoIconRow("Display", "${displayInfo.widthPixels} × ${displayInfo.heightPixels} · ${displayInfo.densityDpi} dpi")
                if (displayInfo.refreshRate > 0f) { RowDivider(startIndent = 16); NoIconRow("Refresh rate", "${displayInfo.refreshRate.toInt()} Hz") }
            }

            SectionHeader(title = "Decoders")
            GlassCard {
                codecs.forEachIndexed { idx, codec ->
                    val status = if (codec.supported) CodecPillStatus.Ok else CodecPillStatus.None
                    val badge = when (status) { CodecPillStatus.Ok -> "HW"; CodecPillStatus.Sw -> "SW"; CodecPillStatus.None -> "N/A" }
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(codec.codecFamily, fontSize = 16.sp, color = Label1, fontWeight = FontWeight.W500)
                            Text(codec.decoderName.ifBlank { "No decoder found on this device" },
                                fontSize = 13.sp, color = Label2, modifier = Modifier.padding(top = 2.dp))
                        }
                        CodecBadge(label = badge, status = status)
                    }
                    if (idx < codecs.lastIndex) RowDivider(startIndent = 16)
                }
            }

            SectionHeader(title = "Diagnostics")
            GlassCard {
                ListRow("GL & renderer info", "Opens Cinema & Renderer", link = true, onClick = {})
                RowDivider(startIndent = 16)
                ListRow("Copy build & runtime diagnostics", "For bug reports", link = true,
                    trailing = { Icon(Icons.Default.ContentCopy, null, tint = Label3, modifier = Modifier.size(18.dp)) }, onClick = {})
            }
        }

        Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) { NavBar(onBack = onBack, title = "About") }
    }
}
