package com.regtho.musicor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MusicDarkColors = darkColorScheme(
    primary = Color(0xFFCFCFCF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF3A3A3A),
    onPrimaryContainer = Color(0xFFF5F5F5),
    inversePrimary = Color(0xFF9E9E9E),
    secondary = Color(0xFF9E9E9E),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF262626),
    onSecondaryContainer = Color(0xFFDADADA),
    tertiary = Color(0xFFB0B0B0),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF2E2E2E),
    onTertiaryContainer = Color(0xFFE6E6E6),
    background = Color(0xFF0B0B0B),
    onBackground = Color(0xFFE2E2E2),
    surface = Color(0xFF0F0F0F),
    onSurface = Color(0xFFE2E2E2),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFB4B4B4),
    surfaceTint = Color(0xFFCFCFCF),
    inverseSurface = Color(0xFFE2E2E2),
    inverseOnSurface = Color(0xFF1A1A1A),
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF3B1A20),
    onErrorContainer = Color(0xFFF5C6CB),
    outline = Color(0xFF454545),
    outlineVariant = Color(0xFF2C2C2C),
    scrim = Color(0xFF000000),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MusicDarkColors,
        content = content,
    )
}