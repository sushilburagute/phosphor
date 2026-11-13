package dev.sush.phosphor.model

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How a palette's two accents relate on the color wheel. Offsets are OKLCH hue degrees, so a
 * step means the same perceived change everywhere on the wheel, unlike HSV.
 */
enum class Harmony(val offset: Float) {
    /** One hue at two lightnesses: calm, tonal. */
    MONOCHROME(0f),
    /** Neighbors on the wheel: warm or cool families that glow together. */
    ANALOGOUS(40f),
    /** A third of the way round: lively without clashing. */
    TRIADIC(120f),
    /** Beside the complement: the contrast of complementary, softened. */
    SPLIT(150f),
    /** Opposites: the strongest temperature contrast, warm light against cool. */
    COMPLEMENTARY(180f),
}

/**
 * A wallpaper palette described perceptually: a key hue, a harmony that places the accent,
 * and the hue the ground leans toward. Night and day are two lightness treatments of the same
 * hues, so a scene keeps its identity when it switches.
 *
 * The rules, in OKLCH:
 * - 60/30/10. The ground carries most of the frame at low chroma; the key accent is the main
 *   light or ink; the harmony accent is the small, hot spark.
 * - Night grounds are deep but tinted toward the key hue (never neutral black), so shadows feel
 *   colored. Their lightness stays low enough for white launcher labels.
 * - Day grounds are sunlit paper tinted toward the accent, so the key ink sits on its own
 *   temperature contrast. Inks are mid lightness and high chroma, so lines read crisply without
 *   going grey; color fields use [ColorTheory.pigment].
 */
data class Scheme(
    val hue: Float,
    val harmony: Harmony,
    val clockwise: Boolean = true,
    val accentShift: Float = 0f,
    val chroma: Float = 1f,
    val nightGround: Float = 0.17f,
    val nightLight: Float = 0.74f,
    val dayGround: Float = 0.955f,
    /** Key ink lightness by day. Scenes that paint whole fields with it want a pastel (~0.72). */
    val dayLight: Float = 0.47f,
) {
    val accentHue: Float get() = (hue + (if (clockwise) harmony.offset else -harmony.offset) + accentShift + 360f) % 360f

    /** Night leans toward the key (a blue night), day paper toward the accent (warm paper, cool ink). */
    private val nightGroundHue get() = hue
    private val dayGroundHue get() = accentHue

    /** Ground, key light, accent light. */
    fun night(): List<Int> = listOf(
        ColorTheory.oklch(nightGround, 0.035f * chroma.coerceAtMost(1.2f), nightGroundHue),
        ColorTheory.oklch(nightLight, 0.15f * chroma, hue),
        ColorTheory.oklch(nightLight - 0.04f, 0.17f * chroma, accentHue),
    )

    /** Paper, key ink, accent ink. */
    fun day(): List<Int> = listOf(
        ColorTheory.oklch(dayGround, 0.028f * chroma.coerceAtMost(1.2f), dayGroundHue),
        ColorTheory.oklch(dayLight, 0.16f * chroma, hue),
        ColorTheory.oklch(minOf(dayLight + 0.16f, 0.8f), 0.19f * chroma, accentHue),
    )
}

/** OKLab/OKLCH conversions with chroma-reducing gamut mapping. Pure Kotlin, no Android types. */
object ColorTheory {
    /** An opaque sRGB color for OKLCH [l] 0..1, chroma [c], hue [h] in degrees. */
    fun oklch(l: Float, c: Float, h: Float): Int {
        val rad = Math.toRadians(h.toDouble())
        val a = cos(rad).toFloat()
        val b = sin(rad).toFloat()
        // Reduce chroma until the color fits sRGB; hue and lightness are what the eye holds onto.
        if (inGamut(l, c * a, c * b)) return pack(l, c * a, c * b)
        var low = 0f
        var high = c
        repeat(18) {
            val mid = (low + high) / 2f
            if (inGamut(l, mid * a, mid * b)) low = mid else high = mid
        }
        return pack(l, low * a, low * b)
    }

    /** [l, c, h] for an sRGB color. */
    fun toOklch(color: Int): FloatArray {
        val r = linear(((color shr 16) and 255) / 255f)
        val g = linear(((color shr 8) and 255) / 255f)
        val bl = linear((color and 255) / 255f)
        val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * bl)
        val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * bl)
        val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * bl)
        val lightness = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
        val a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
        val b = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
        val hue = Math.toDegrees(atan2(b, a).toDouble()).toFloat().let { if (it < 0f) it + 360f else it }
        return floatArrayOf(lightness, sqrt(a * a + b * b), hue)
    }

    /**
     * The same hue raised to [lightness] with as much chroma as sRGB allows, up to [maxChroma].
     * Day scenes paint their color fields with these: luminous pastels rather than thin ink.
     */
    fun pigment(color: Int, lightness: Float, maxChroma: Float = 0.16f): Int {
        val (_, c, h) = toOklch(color).let { Triple(it[0], it[1], it[2]) }
        return oklch(lightness, maxOf(c, maxChroma), h)
    }

    /** Move [color] to [lightness], keeping hue and (gamut permitting) chroma. */
    fun withLightness(color: Int, lightness: Float, chromaScale: Float = 1f): Int {
        val lch = toOklch(color)
        return oklch(lightness, lch[1] * chromaScale, lch[2])
    }

    private fun lab(l: Float, a: Float, b: Float): FloatArray {
        val l1 = (l + 0.3963377774f * a + 0.2158037573f * b).pow(3)
        val m1 = (l - 0.1055613458f * a - 0.0638541728f * b).pow(3)
        val s1 = (l - 0.0894841775f * a - 1.2914855480f * b).pow(3)
        return floatArrayOf(
            4.0767416621f * l1 - 3.3077115913f * m1 + 0.2309699292f * s1,
            -1.2684380046f * l1 + 2.6097574011f * m1 - 0.3413193965f * s1,
            -0.0041960863f * l1 - 0.7034186147f * m1 + 1.7076147010f * s1,
        )
    }

    private fun inGamut(l: Float, a: Float, b: Float): Boolean =
        lab(l, a, b).all { it in -0.0005f..1.0005f }

    private fun pack(l: Float, a: Float, b: Float): Int {
        val rgb = lab(l, a, b)
        fun channel(v: Float) = (gamma(v.coerceIn(0f, 1f)) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(rgb[0]) shl 16) or (channel(rgb[1]) shl 8) or channel(rgb[2])
    }

    private fun linear(c: Float) = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    private fun gamma(c: Float) = if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f
}
