package com.aurora.cinema.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SafetyAcknowledgementScreen(onContinue: () -> Unit) {
    var ack by rememberSaveable { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 86.dp, bottom = 40.dp),
        ) {
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
                Text(text = "A few things to know each time you use VR playback.", fontSize = 15.sp, color = Label2)
            }

            GlassCard {
                OnbItem(icon = Icons.Default.EventSeat, tint = AccentBlue, title = "Use VRPlay seated",
                    body = "Stay seated for the full session. Standing playback isn't supported and increases the chance of losing balance.")
                HorizontalDivider(color = Separator)
                OnbItem(icon = Icons.Default.Waves, tint = AmberColor, title = "Stop if you feel unwell",
                    body = "Dizziness, nausea, or eye strain can build quickly. Remove the headset and rest before continuing.")
                HorizontalDivider(color = Separator)
                OnbItem(icon = Icons.Outlined.Schedule, tint = TealColor, title = "Take breaks",
                    body = "Rest for a few minutes every 30 minutes of headset use.")
            }

            Column { SectionHeader(title = "Your data") }
            GlassCard {
                OnbItem(icon = Icons.Default.Security, tint = GreenColor, title = "Everything stays on this device",
                    body = "VRPlay only reads videos you import. Playback positions, calibration, and settings are stored locally. Nothing is uploaded.")
            }

            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(
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
                        modifier = Modifier.size(23.dp).background(if (ack) AccentBlue else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (ack) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        } else {
                            Surface(modifier = Modifier.size(23.dp), color = Color.Transparent, shape = CircleShape,
                                border = BorderStroke(1.8.dp, Label3)) {}
                        }
                    }
                    Text("I've read and understand the safety guidance", fontSize = 15.sp, fontWeight = FontWeight.W500, color = Label1)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            PrimaryButton(text = "Continue", enabled = ack, onClick = onContinue)
            Spacer(modifier = Modifier.height(9.dp))
            Text(
                text = "Shown on first launch. You can re-read this from Settings.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                fontSize = 13.sp, color = Label3, textAlign = TextAlign.Center, lineHeight = 19.sp,
            )
        }
    }
}

@Composable
internal fun OnbItem(icon: ImageVector, tint: Color, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.W600, color = Label1, letterSpacing = (-0.1).sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = body, fontSize = 13.sp, color = Label2, lineHeight = 19.sp)
        }
    }
}
