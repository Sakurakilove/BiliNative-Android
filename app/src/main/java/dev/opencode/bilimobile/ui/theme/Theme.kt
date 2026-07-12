package dev.opencode.bilimobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFFE6537D),
    secondary = Color(0xFFB74A68),
    background = Color(0xFFF7F7F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDEDF2),
    onBackground = Color(0xFF19191D),
    onSurface = Color(0xFF19191D),
    onSurfaceVariant = Color(0xFF5D5D67),
    outline = Color(0xFF777781),
    outlineVariant = Color(0xFFD8D8DE),
    primaryContainer = Color(0xFFFFD9E3),
    onPrimaryContainer = Color(0xFF521126)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF87A8),
    secondary = Color(0xFFFF8CAE),
    background = Color(0xFF0E0E10),
    surface = Color(0xFF18181B),
    surfaceVariant = Color(0xFF252529),
    onSurfaceVariant = Color(0xFFC7C5CC),
    outline = Color(0xFF929098),
    outlineVariant = Color(0xFF3E3D43),
    primaryContainer = Color(0xFF713247),
    onPrimaryContainer = Color(0xFFFFD9E3)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
)

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
)

@Composable
fun BiliTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) Dark else Light
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
