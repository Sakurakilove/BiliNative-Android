package dev.opencode.bilimobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build

private val Light = lightColorScheme(
    primary = Color(0xFFE6537D),
    secondary = Color(0xFFB74A68),
    background = Color(0xFFF7F7FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDEDF2),
    onBackground = Color(0xFF19191D),
    onSurface = Color(0xFF19191D)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF87A8),
    secondary = Color(0xFFFF8CAE),
    background = Color(0xFF101014),
    surface = Color(0xFF1C1C21),
    surfaceVariant = Color(0xFF2A2A30)
)

@Composable
fun BiliTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) Dark else Light
    MaterialTheme(colorScheme = colors, content = content)
}
