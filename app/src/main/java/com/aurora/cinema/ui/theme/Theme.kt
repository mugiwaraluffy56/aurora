package com.aurora.cinema.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AuroraColorScheme = darkColorScheme(
    primary = AuroraPrimary,
    background = AuroraBlack,
    surface = AuroraSurface,
    onBackground = AuroraOnDark,
    onSurface = AuroraOnDark,
)

@Composable
fun AuroraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuroraColorScheme,
        typography = AuroraTypography,
        content = content,
    )
}
