package com.expensetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Premium dark — deep background, vibrant accents
private val PremiumDarkScheme = darkColorScheme(
    primary = Color(0xFF9D7BFF),           // Vibrant purple (lifted for glass contrast)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2D1B69),
    onPrimaryContainer = Color(0xFFE8DDFF),
    secondary = Color(0xFF00E5FF),          // Cyan accent
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF004D65),
    onSecondaryContainer = Color(0xFF97F0FF),
    tertiary = Color(0xFFFFAB40),           // Orange accent
    onTertiary = Color(0xFF3E2700),
    background = Color(0xFF07070C),        // Deep dark blue-black
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF14141F),           // Card surfaces
    onSurface = Color(0xFFF0F0F0),
    surfaceVariant = Color(0xFF1E1E2E),    // Slightly lighter cards
    onSurfaceVariant = Color(0xFFB8B8CC),
    outline = Color(0xFF3A3A50),
    error = Color(0xFFFF5252),             // Bright red for debits
    onError = Color(0xFFFFFFFF),
)

// Premium light — clean with colorful accents
private val PremiumLightScheme = lightColorScheme(
    primary = Color(0xFF5C2FC2),           // Deep purple
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE4FF),
    onPrimaryContainer = Color(0xFF1F0060),
    secondary = Color(0xFF0097A7),          // Teal
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB2EBF2),
    onSecondaryContainer = Color(0xFF003640),
    tertiary = Color(0xFFE65100),           // Deep orange
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF3F1FB),        // Soft lavender white
    onBackground = Color(0xFF1A1A2E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFF3F0FA),
    onSurfaceVariant = Color(0xFF4A4A5E),
    outline = Color(0xFFD4D0E0),
    error = Color(0xFFD50000),
    onError = Color(0xFFFFFFFF),
)

/**
 * Glassmorphism design tokens: the translucent fills, gradient hairline borders, and
 * background gradient + glow "orbs" that give every screen its frosted-glass depth. Access
 * via [AppTheme]; provided by [ExpenseTrackerTheme].
 */
data class GlassTokens(
    val dark: Boolean,
    /** Full-screen gradient painted behind all content. */
    val backgroundGradient: Brush,
    /** Blurred decorative orbs layered over the background for depth. */
    val orbPrimary: Color,
    val orbSecondary: Color,
    /** Translucent fill for glass cards (top→bottom subtle sheen). */
    val glassFill: Brush,
    /** Gradient hairline stroke around glass cards. */
    val glassBorder: Brush,
    /** Solid-ish fill used where full translucency would hurt legibility (dialogs, sheets). */
    val glassSolid: Color,
    /** Accent gradient for primary actions / hero numbers. */
    val accentGradient: List<Color>,
)

private val DarkGlass = GlassTokens(
    dark = true,
    backgroundGradient = Brush.linearGradient(
        listOf(Color(0xFF0B0A18), Color(0xFF07070C), Color(0xFF0E0A1C))
    ),
    orbPrimary = Color(0xFF7C4DFF),
    orbSecondary = Color(0xFF00E5FF),
    glassFill = Brush.verticalGradient(
        listOf(Color(0x2EFFFFFF), Color(0x14FFFFFF))
    ),
    glassBorder = Brush.linearGradient(
        listOf(Color(0x66FFFFFF), Color(0x14FFFFFF))
    ),
    glassSolid = Color(0xFF16121F),
    accentGradient = listOf(Color(0xFF9D7BFF), Color(0xFF00E5FF)),
)

private val LightGlass = GlassTokens(
    dark = false,
    backgroundGradient = Brush.linearGradient(
        listOf(Color(0xFFF4F1FF), Color(0xFFEFF3FF), Color(0xFFF6EEFF))
    ),
    orbPrimary = Color(0xFFB39DFF),
    orbSecondary = Color(0xFF7FE6F5),
    glassFill = Brush.verticalGradient(
        listOf(Color(0xF2FFFFFF), Color(0xCCFFFFFF))
    ),
    glassBorder = Brush.linearGradient(
        listOf(Color(0x99FFFFFF), Color(0x33B39DFF))
    ),
    glassSolid = Color(0xFFFFFFFF),
    accentGradient = listOf(Color(0xFF7C4DFF), Color(0xFF0097A7)),
)

private val LocalGlassTokens = staticCompositionLocalOf { DarkGlass }

/** Ambient accessor for glassmorphism tokens. */
object AppTheme {
    val glass: GlassTokens
        @Composable @ReadOnlyComposable get() = LocalGlassTokens.current
}

private val PremiumTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.3.sp
    ),
)

@Composable
fun ExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PremiumDarkScheme else PremiumLightScheme
    val glass = if (darkTheme) DarkGlass else LightGlass

    CompositionLocalProvider(LocalGlassTokens provides glass) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PremiumTypography,
            content = content
        )
    }
}
