package com.taplink.app.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
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

/**
 * High-contrast palettes keep text at or above 7:1 against its background (WCAG 2.2 AAA for body
 * text) and avoid relying on colour alone, because every meaning is also carried by a label.
 */
private val HighContrastLight = lightColorScheme(
    primary = Color(0xFF00327A), onPrimary = Color.White, primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF00132E), secondary = Color(0xFF00463F),
    secondaryContainer = Color(0xFFC8F5EE), onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF4A2C86), background = Color.White, surface = Color.White,
    surfaceVariant = Color(0xFFF0F0F0), onSurface = Color.Black, onBackground = Color.Black,
    onSurfaceVariant = Color(0xFF1A1A1A), outline = Color.Black, outlineVariant = Color(0xFF4A4A4A),
    error = Color(0xFF8C0009), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)
private val HighContrastDark = darkColorScheme(
    primary = Color(0xFFA8C8FF), onPrimary = Color(0xFF001B3F), primaryContainer = Color(0xFF00306B),
    onPrimaryContainer = Color(0xFFD6E3FF), secondary = Color(0xFF7DF5E8),
    secondaryContainer = Color(0xFF00443E), onSecondaryContainer = Color(0xFFC8F5EE),
    background = Color.Black, surface = Color(0xFF0A0A0A), surfaceVariant = Color(0xFF1E1E1E),
    onSurface = Color.White, onBackground = Color.White, onSurfaceVariant = Color(0xFFF0F0F0),
    outline = Color.White, outlineVariant = Color(0xFFBDBDBD),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF690005), onErrorContainer = Color(0xFFFFDAD6),
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

/** True below a high-contrast theme, so surfaces can add a border alongside the palette change. */
val LocalHighContrast = staticCompositionLocalOf { false }

/** 150% text: large enough for most low-vision users without breaking single-line labels. */
const val LARGE_TEXT_SCALE = 1.5f

@Composable
fun TapLinkTheme(
    mode: ThemeMode,
    largeText: Boolean = false,
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; else -> false }
    val scheme = when {
        highContrast && dark -> HighContrastDark
        highContrast -> HighContrastLight
        dark -> Dark
        else -> Light
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>().firstOrNull()
            activity?.window?.let { window ->
                // The app can choose a different theme from Android's system setting.
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    val density = LocalDensity.current
    // Compose has no "text scale" switch, so the font scale is multiplied. Everything in sp grows;
    // dp-based touch targets stay at least 48dp and therefore remain tappable.
    val scaled = if (largeText) {
        Density(density.density, (density.fontScale * LARGE_TEXT_SCALE).coerceAtMost(2.6f))
    } else density
    CompositionLocalProvider(LocalDensity provides scaled, LocalHighContrast provides highContrast) {
        MaterialTheme(colorScheme = scheme, typography = Type, content = content)
    }
}
