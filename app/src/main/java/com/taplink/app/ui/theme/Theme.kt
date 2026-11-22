package com.taplink.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.taplink.app.domain.ThemeMode

val Ocean = Color(0xFF1266D6)
val Teal = Color(0xFF007F85)
val Ink = Color(0xFF142C45)
private val Light = lightColorScheme(
    primary = Ocean, onPrimary = Color.White, primaryContainer = Color(0xFFDCEAFF),
    onPrimaryContainer = Color(0xFF103D78), secondary = Teal,
    secondaryContainer = Color(0xFFD2F2ED), onSecondaryContainer = Color(0xFF064C48),
    tertiary = Color(0xFF7257AD), background = Color(0xFFF4F7FB),
    surface = Color.White, surfaceVariant = Color(0xFFEAF0F7),
    onSurface = Ink, onBackground = Ink, onSurfaceVariant = Color(0xFF536578),
    outline = Color(0xFF7D8C9D), outlineVariant = Color(0xFFDCE4EF),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF9AC3FF), onPrimary = Color(0xFF003267),
    primaryContainer = Color(0xFF164C84), secondary = Color(0xFF71D9D0),
    secondaryContainer = Color(0xFF07524F), background = Color(0xFF0D1825),
    surface = Color(0xFF142233), surfaceVariant = Color(0xFF233448),
    onSurface = Color(0xFFE3EDFA), onBackground = Color(0xFFE3EDFA),
    onSurfaceVariant = Color(0xFFB4C5D8), outlineVariant = Color(0xFF34465B),
)
private val Type = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun TapLinkTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; else -> false }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
}
