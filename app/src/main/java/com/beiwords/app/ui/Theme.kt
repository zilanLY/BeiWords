package com.beiwords.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF3D7EFF)
private val BlueDark = Color(0xFF9DBDFF)

private val LightScheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EFFF),
    onPrimaryContainer = Color(0xFF0A2E6E),
    secondary = Color(0xFF5A6B8C),
    background = Color(0xFFF7F8FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF1F6)
)

private val DarkScheme = darkColorScheme(
    primary = BlueDark,
    primaryContainer = Color(0xFF1B3B6F),
    background = Color(0xFF121418),
    surface = Color(0xFF1A1D23)
)

@Composable
fun BeiWordsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content
    )
}
