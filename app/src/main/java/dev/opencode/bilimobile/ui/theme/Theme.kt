package dev.opencode.bilimobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFFE6537D),
    secondary = Color(0xFFB74A68),
    background = Color(0xFFF7F7F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDEDF2),
    onBackground = Color(0xFF19191D),
    onSurface = Color(0xFF19191D)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF87A8),
    secondary = Color(0xFFFF8CAE),
    background = Color(0xFF0E0E10),
    surface = Color(0xFF18181B),
    surfaceVariant = Color(0xFF252529)
)

@Composable
fun BiliTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) Dark else Light
    MaterialTheme(colorScheme = colors, content = content)
}
