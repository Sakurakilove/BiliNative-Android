package dev.opencode.bilimobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF0066CC),
    secondary = Color(0xFFFB7299),
    background = Color(0xFFF7F7FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDEDF2),
    onBackground = Color(0xFF19191D),
    onSurface = Color(0xFF19191D)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF62A9FF),
    secondary = Color(0xFFFF8CAE),
    background = Color(0xFF101014),
    surface = Color(0xFF1C1C21),
    surfaceVariant = Color(0xFF2A2A30)
)

@Composable
fun BiliTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
