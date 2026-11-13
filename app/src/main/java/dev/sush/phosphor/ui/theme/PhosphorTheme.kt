package dev.sush.phosphor.ui.theme

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.sush.phosphor.R

/**
 * Phosphor design tokens.
 *
 * Concept: night is light, day is ink. The fallback palette borrows from the artwork's two
 * treatments: deep sky with a pale phosphor blue at night; cool paper with ultramarine ink by
 * day. Ember is reserved for one meaning: "live on your phone."
 */
object Palette {
    // Night
    val Sky = Color(0xFF060914)
    val SkyRaised = Color(0xFF0E1324)
    val SkyHigh = Color(0xFF161C33)
    val SkyLine = Color(0xFF2A3253)
    val Starlight = Color(0xFFE9ECF6)
    val Haze = Color(0xFFA5ACC6)
    val Phosphor = Color(0xFFA9C0FF)
    val PhosphorDeep = Color(0xFF213A94)

    // Day
    val Paper = Color(0xFFEDF0F5)
    val PaperRaised = Color(0xFFF7F8FB)
    val PaperLow = Color(0xFFE2E6EE)
    val PaperLine = Color(0xFFC3C9D8)
    val Ink = Color(0xFF12141C)
    val InkSoft = Color(0xFF4A5068)
    val Ultramarine = Color(0xFF2742B8)
    val UltramarineWash = Color(0xFFD8E0FF)

    // Shared accent: the horizon line.
    val Ember = Color(0xFFFF6A3A)
    val EmberInk = Color(0xFFD5482A)
}

private val NightScheme = darkColorScheme(
    primary = Palette.Phosphor,
    onPrimary = Color(0xFF07185A),
    primaryContainer = Palette.PhosphorDeep,
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Palette.Ember,
    onSecondary = Color(0xFF3A0B00),
    // Selection containers stay in the blue family; ember is reserved for "live".
    secondaryContainer = Color(0xFF1D2A5C),
    onSecondaryContainer = Color(0xFFDCE4FF),
    tertiary = Color(0xFF8CE6D2),
    background = Palette.Sky,
    onBackground = Palette.Starlight,
    surface = Palette.Sky,
    onSurface = Palette.Starlight,
    surfaceVariant = Palette.SkyHigh,
    onSurfaceVariant = Palette.Haze,
    surfaceContainerLowest = Color(0xFF03050B),
    surfaceContainerLow = Color(0xFF0A0E1C),
    surfaceContainer = Palette.SkyRaised,
    surfaceContainerHigh = Palette.SkyHigh,
    surfaceContainerHighest = Color(0xFF1E2540),
    outline = Color(0xFF4A5378),
    outlineVariant = Palette.SkyLine,
    inverseSurface = Palette.Paper,
    inverseOnSurface = Palette.Ink,
    inversePrimary = Palette.Ultramarine,
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF5C1408),
)

private val DayScheme = lightColorScheme(
    primary = Palette.Ultramarine,
    onPrimary = Color.White,
    primaryContainer = Palette.UltramarineWash,
    onPrimaryContainer = Color(0xFF0B1A5C),
    secondary = Palette.EmberInk,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE3F7),
    onSecondaryContainer = Color(0xFF0B1A5C),
    tertiary = Color(0xFF167A6A),
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.PaperLow,
    onSurfaceVariant = Palette.InkSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Palette.PaperRaised,
    surfaceContainer = Color(0xFFE8EBF2),
    surfaceContainerHigh = Palette.PaperLow,
    surfaceContainerHighest = Color(0xFFD9DEE8),
    outline = Color(0xFF7A8098),
    outlineVariant = Palette.PaperLine,
    inverseSurface = Palette.SkyRaised,
    inverseOnSurface = Palette.Starlight,
    inversePrimary = Palette.Phosphor,
    error = Color(0xFFB3261E),
)

// One family, three optical settings: tight condensed display, a firm title, a calm text cut.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun bricolage(weight: Int, opsz: Float, width: Float) = FontFamily(
    Font(
        R.font.bricolage_grotesque,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.Setting("opsz", opsz),
            FontVariation.width(width),
        ),
    ),
)

object PhosphorType {
    val Display = bricolage(640, 96f, 82f)
    val Headline = bricolage(600, 48f, 88f)
    val Title = bricolage(560, 20f, 96f)
    val Text = bricolage(400, 14f, 100f)
    val TextStrong = bricolage(540, 14f, 100f)
}

// Scale steps by roughly 1.25 (a major third), anchored at 16sp body text.
private val PhosphorTypography = Typography(
    displayLarge = TextStyle(fontFamily = PhosphorType.Display, fontSize = 56.sp, lineHeight = 54.sp, letterSpacing = (-0.035).em),
    displayMedium = TextStyle(fontFamily = PhosphorType.Display, fontSize = 44.sp, lineHeight = 44.sp, letterSpacing = (-0.03).em),
    displaySmall = TextStyle(fontFamily = PhosphorType.Display, fontSize = 36.sp, lineHeight = 38.sp, letterSpacing = (-0.025).em),
    headlineLarge = TextStyle(fontFamily = PhosphorType.Headline, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontFamily = PhosphorType.Headline, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.015).em),
    headlineSmall = TextStyle(fontFamily = PhosphorType.Headline, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = PhosphorType.Title, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.005).em),
    titleMedium = TextStyle(fontFamily = PhosphorType.Title, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = PhosphorType.Title, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = PhosphorType.Text, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = PhosphorType.Text, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = PhosphorType.Text, fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = PhosphorType.TextStrong, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.005.em),
    labelMedium = TextStyle(fontFamily = PhosphorType.TextStrong, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.01.em),
    labelSmall = TextStyle(fontFamily = PhosphorType.TextStrong, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.01.em),
)

/**
 * Shapes follow hierarchy rather than one radius everywhere: phone-shaped plates are the
 * most rounded (they mimic a device), controls are moderate, small marks are tight.
 */
private val PhosphorShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/** Spacing on a 4dp grid, plus motion tokens tuned to feel physical rather than springy. */
@Immutable
data class Spacing(
    val hair: androidx.compose.ui.unit.Dp = 2.dp,
    val xs: androidx.compose.ui.unit.Dp = 4.dp,
    val s: androidx.compose.ui.unit.Dp = 8.dp,
    val m: androidx.compose.ui.unit.Dp = 12.dp,
    val l: androidx.compose.ui.unit.Dp = 16.dp,
    val xl: androidx.compose.ui.unit.Dp = 24.dp,
    val xxl: androidx.compose.ui.unit.Dp = 32.dp,
    val gutter: androidx.compose.ui.unit.Dp = 20.dp,
    val plate: androidx.compose.ui.unit.Dp = 26.dp,
)

object Motion {
    /** Emphasized decelerate: things arrive quickly and settle. */
    val Arrive = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Standard: for state changes in place. */
    val Shift = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val QUICK = 160
    const val CALM = 320
    /** The day/night crossfade is the app's one orchestrated moment. */
    const val MOOD = 520
}

val LocalSpacing = staticCompositionLocalOf { Spacing() }

/** Whether the app chrome itself is drawn as night (light on dark). */
val LocalNight = staticCompositionLocalOf { true }

@Composable
fun PhosphorTheme(night: Boolean, dynamic: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (night) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        night -> NightScheme
        else -> DayScheme
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalNight provides night) {
        MaterialTheme(colorScheme = scheme, typography = PhosphorTypography, shapes = PhosphorShapes, content = content)
    }
}
