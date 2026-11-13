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
 * The chrome is a gallery wall: near-neutral surfaces with only a breath of the brand blue, so
 * the wallpapers carry all the color. Night is graphite, day is soft paper. One accent family
 * (phosphor blue / ultramarine) marks actions and selection; ember means only "live on your
 * phone".
 */
object Palette {
    // Night: graphite, with a faint cool cast.
    val Sky = Color(0xFF0D0E12)
    val SkyLow = Color(0xFF131419)
    val SkyRaised = Color(0xFF1A1B21)
    val SkyHigh = Color(0xFF22242B)
    val SkyHighest = Color(0xFF2B2D35)
    val SkyLine = Color(0xFF2E3039)
    val Starlight = Color(0xFFEEEEF2)
    val Haze = Color(0xFFA4A6B2)
    val Phosphor = Color(0xFFB3C3FF)
    val PhosphorDeep = Color(0xFF2B3B85)

    // Day: soft paper, white cards.
    val Paper = Color(0xFFF4F4F7)
    val PaperRaised = Color(0xFFFFFFFF)
    val PaperLow = Color(0xFFEAEAEF)
    val PaperHigh = Color(0xFFE2E2E9)
    val PaperLine = Color(0xFFDADBE2)
    val Ink = Color(0xFF121318)
    val InkSoft = Color(0xFF5B5D69)
    val Ultramarine = Color(0xFF2E47C7)
    val UltramarineWash = Color(0xFFDFE5FF)

    // Shared accent: the horizon line, "live on your phone".
    val Ember = Color(0xFFFF6A3A)
    val EmberInk = Color(0xFFD5482A)
}

private val NightScheme = darkColorScheme(
    primary = Palette.Phosphor,
    onPrimary = Color(0xFF0A1850),
    primaryContainer = Palette.PhosphorDeep,
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Palette.Ember,
    onSecondary = Color(0xFF3A0B00),
    // Selection containers stay in the blue family; ember is reserved for "live".
    secondaryContainer = Color(0xFF263160),
    onSecondaryContainer = Color(0xFFDDE3FF),
    tertiary = Color(0xFF8CE6D2),
    background = Palette.Sky,
    onBackground = Palette.Starlight,
    surface = Palette.Sky,
    onSurface = Palette.Starlight,
    surfaceVariant = Palette.SkyHigh,
    onSurfaceVariant = Palette.Haze,
    surfaceContainerLowest = Color(0xFF08090C),
    surfaceContainerLow = Palette.SkyLow,
    surfaceContainer = Palette.SkyRaised,
    surfaceContainerHigh = Palette.SkyHigh,
    surfaceContainerHighest = Palette.SkyHighest,
    outline = Color(0xFF5C5F6B),
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
    onPrimaryContainer = Color(0xFF0C1A5E),
    secondary = Palette.EmberInk,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E6F8),
    onSecondaryContainer = Color(0xFF0C1A5E),
    tertiary = Color(0xFF167A6A),
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.PaperLow,
    onSurfaceVariant = Palette.InkSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = Palette.PaperRaised,
    surfaceContainerHigh = Palette.PaperLow,
    surfaceContainerHighest = Palette.PaperHigh,
    outline = Color(0xFF878995),
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
    val Display = bricolage(600, 96f, 88f)
    val Headline = bricolage(580, 48f, 92f)
    val Title = bricolage(540, 20f, 98f)
    val Text = bricolage(400, 14f, 100f)
    val TextStrong = bricolage(520, 14f, 100f)
}

// Scale steps by roughly 1.25 (a major third), anchored at 16sp body text. Large type is set
// tight; small type gets air (1.5 line height) so dense settings still breathe.
private val PhosphorTypography = Typography(
    displayLarge = TextStyle(fontFamily = PhosphorType.Display, fontSize = 56.sp, lineHeight = 58.sp, letterSpacing = (-0.035).em),
    displayMedium = TextStyle(fontFamily = PhosphorType.Display, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.03).em),
    displaySmall = TextStyle(fontFamily = PhosphorType.Display, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.025).em),
    headlineLarge = TextStyle(fontFamily = PhosphorType.Display, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.025).em),
    // The large top bar's expanded title.
    headlineMedium = TextStyle(fontFamily = PhosphorType.Display, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.025).em),
    headlineSmall = TextStyle(fontFamily = PhosphorType.Headline, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = PhosphorType.Headline, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em),
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
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
