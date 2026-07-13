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
    primary = Color(0xFF365FCE),
    onPrimary = Color(0xFFFDFDFF),
    secondary = Color(0xFF52658C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E8F5),
    onSecondaryContainer = Color(0xFF25324A),
    tertiary = Color(0xFF596780),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDDE6F7),
    onTertiaryContainer = Color(0xFF25334B),
    background = Color(0xFFF5F7FA),
    surface = Color(0xFFFDFDFF),
    surfaceVariant = Color(0xFFEAEEF5),
    surfaceDim = Color(0xFFD9DEE8),
    surfaceBright = Color(0xFFFDFDFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FB),
    surfaceContainer = Color(0xFFF0F3F8),
    surfaceContainerHigh = Color(0xFFE9EDF4),
    surfaceContainerHighest = Color(0xFFE2E7EF),
    onBackground = Color(0xFF161A22),
    onSurface = Color(0xFF161A22),
    onSurfaceVariant = Color(0xFF596170),
    outline = Color(0xFF747D8E),
    outlineVariant = Color(0xFFD9DEE8),
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF17336F),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF690005),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF2B3039),
    inverseOnSurface = Color(0xFFF0F1F5),
    inversePrimary = Color(0xFFAEC6FF),
    surfaceTint = Color(0xFF365FCE),
    scrim = Color(0xFF000000)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFAEC6FF),
    onPrimary = Color(0xFF0E2D66),
    secondary = Color(0xFFB8C7E9),
    onSecondary = Color(0xFF23304A),
    secondaryContainer = Color(0xFF2A3444),
    onSecondaryContainer = Color(0xFFDDE6F7),
    tertiary = Color(0xFFC1CAE0),
    onTertiary = Color(0xFF2A354A),
    tertiaryContainer = Color(0xFF354158),
    onTertiaryContainer = Color(0xFFDDE6F7),
    background = Color(0xFF0C0F14),
    surface = Color(0xFF13171E),
    surfaceVariant = Color(0xFF202630),
    surfaceDim = Color(0xFF0C0F14),
    surfaceBright = Color(0xFF323741),
    surfaceContainerLowest = Color(0xFF080A0E),
    surfaceContainerLow = Color(0xFF11151B),
    surfaceContainer = Color(0xFF171B22),
    surfaceContainerHigh = Color(0xFF20252D),
    surfaceContainerHighest = Color(0xFF2B313B),
    onBackground = Color(0xFFE7EAF0),
    onSurface = Color(0xFFE7EAF0),
    onSurfaceVariant = Color(0xFFB9C0CD),
    outline = Color(0xFF8A93A2),
    outlineVariant = Color(0xFF303743),
    primaryContainer = Color(0xFF233F78),
    onPrimaryContainer = Color(0xFFD9E4FF),
    errorContainer = Color(0xFF6D2024),
    onErrorContainer = Color(0xFFFFDAD6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    inverseSurface = Color(0xFFE7EAF0),
    inverseOnSurface = Color(0xFF2C3037),
    inversePrimary = Color(0xFF365FCE),
    surfaceTint = Color(0xFFAEC6FF),
    scrim = Color(0xFF000000)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
)

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
)

@Composable
fun BiliTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) Dark else Light
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
