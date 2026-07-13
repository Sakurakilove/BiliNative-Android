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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF9D3D49),
    onPrimary = Color(0xFFFFF8F7),
    secondary = Color(0xFF665C5D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE3E3),
    onSecondaryContainer = Color(0xFF33292A),
    tertiary = Color(0xFF6A5D4F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDE5DC),
    onTertiaryContainer = Color(0xFF332B23),
    background = Color(0xFFF4F4F1),
    surface = Color(0xFFFAFAF7),
    surfaceVariant = Color(0xFFE9E8E3),
    surfaceDim = Color(0xFFDCDCD6),
    surfaceBright = Color(0xFFFAFAF7),
    surfaceContainerLowest = Color(0xFFFEFEFB),
    surfaceContainerLow = Color(0xFFF6F6F2),
    surfaceContainer = Color(0xFFEFEFEA),
    surfaceContainerHigh = Color(0xFFE7E7E1),
    surfaceContainerHighest = Color(0xFFDEDED8),
    onBackground = Color(0xFF1C1C1B),
    onSurface = Color(0xFF1C1C1B),
    onSurfaceVariant = Color(0xFF5F5E5A),
    outline = Color(0xFF777570),
    outlineVariant = Color(0xFFD2D1CB),
    primaryContainer = Color(0xFFF3DADF),
    onPrimaryContainer = Color(0xFF5C1824),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF690005),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF292928),
    inverseOnSurface = Color(0xFFF1F0EB),
    inversePrimary = Color(0xFFFFB2BC),
    surfaceTint = Color(0xFF9D3D49),
    scrim = Color(0xFF000000)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB2BC),
    onPrimary = Color(0xFF5F1724),
    secondary = Color(0xFFD8C2C4),
    onSecondary = Color(0xFF3C2B2E),
    secondaryContainer = Color(0xFF49383B),
    onSecondaryContainer = Color(0xFFF4DDE0),
    tertiary = Color(0xFFD8C4AE),
    onTertiary = Color(0xFF3B2F23),
    tertiaryContainer = Color(0xFF4A3E32),
    onTertiaryContainer = Color(0xFFF4E1CD),
    background = Color(0xFF121211),
    surface = Color(0xFF191918),
    surfaceVariant = Color(0xFF292827),
    surfaceDim = Color(0xFF121211),
    surfaceBright = Color(0xFF393837),
    surfaceContainerLowest = Color(0xFF0D0D0C),
    surfaceContainerLow = Color(0xFF171716),
    surfaceContainer = Color(0xFF1D1D1C),
    surfaceContainerHigh = Color(0xFF252524),
    surfaceContainerHighest = Color(0xFF30302E),
    onBackground = Color(0xFFECEBE6),
    onSurface = Color(0xFFECEBE6),
    onSurfaceVariant = Color(0xFFC9C7C1),
    outline = Color(0xFF96938D),
    outlineVariant = Color(0xFF3B3A37),
    primaryContainer = Color(0xFF672430),
    onPrimaryContainer = Color(0xFFFFD9DD),
    errorContainer = Color(0xFF6D2024),
    onErrorContainer = Color(0xFFFFDAD6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    inverseSurface = Color(0xFF242321),
    inverseOnSurface = Color(0xFFECEBE6),
    inversePrimary = Color(0xFF9D3D49),
    surfaceTint = Color(0xFFFFB2BC),
    scrim = Color(0xFF000000)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 35.sp, letterSpacing = (-0.35).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 31.sp, letterSpacing = (-0.25).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.15).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
)

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
)

enum class AppThemeMode { System, Light, Dark }

@Composable
fun BiliTheme(mode: AppThemeMode = AppThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) { AppThemeMode.System -> isSystemInDarkTheme(); AppThemeMode.Light -> false; AppThemeMode.Dark -> true }
    val colors = if (dark) Dark else Light
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
