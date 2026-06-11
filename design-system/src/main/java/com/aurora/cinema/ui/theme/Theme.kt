package com.aurora.cinema.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AuroraColorScheme = darkColorScheme(
    primary = AuroraPrimary,
    onPrimary = AuroraOnPrimary,
    primaryContainer = AuroraPrimaryPressed,
    onPrimaryContainer = AuroraOnPrimary,
    secondary = AuroraPrimary,
    onSecondary = AuroraOnPrimary,
    secondaryContainer = AuroraSurfaceRaised,
    onSecondaryContainer = AuroraOnDark,
    tertiary = AuroraPrimary,
    onTertiary = AuroraOnPrimary,
    tertiaryContainer = AuroraSurfaceRaised,
    onTertiaryContainer = AuroraOnDark,
    background = AuroraBlack,
    surface = AuroraSurface,
    surfaceVariant = AuroraSurfaceRaised,
    onBackground = AuroraOnDark,
    onSurface = AuroraOnDark,
    onSurfaceVariant = AuroraMuted,
    outline = AuroraOutline,
    outlineVariant = AuroraOutline,
    inversePrimary = AuroraPrimary,
    inverseSurface = AuroraOnDark,
    inverseOnSurface = AuroraBlack,
    surfaceTint = AuroraPrimary,
)

@Composable
fun AuroraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuroraColorScheme,
        typography = AuroraTypography,
        content = content,
    )
}
