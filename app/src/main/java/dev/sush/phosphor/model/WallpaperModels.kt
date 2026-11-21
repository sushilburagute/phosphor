package dev.sush.phosphor.model

/** Each family is drawn locally; procedural artwork never needs a network connection. */
enum class WallpaperKind { ORBIT, AURORA, ASCII, GRADIENT, GLASS, ABSTRACT, TEXTURE, IMAGE }

/** A palette is a dark ground followed by two accents. The ground keeps launcher labels readable. */
data class WallpaperPreset(
    val id: String,
    val controlLabel: String,
    val name: String,
    val description: String,
    val kind: WallpaperKind,
    val colors: List<Int>,
)

enum class ScenePalette(val label: String, val colors: List<Int>? = null) {
    ORIGINAL("Original"),
    LAGOON("Lagoon", listOf(0xFF041419.toInt(), 0xFF62CFBB.toInt(), 0xFF739FF4.toInt())),
    EMBER("Ember", listOf(0xFF160A13.toInt(), 0xFFF29B62.toInt(), 0xFFD96BA6.toInt())),
    CUSTOM("Custom"),
}

data class WallpaperSettings(
    val presetId: String = "orbit-exposure",
    val speed: Float = 0.5f,
    val intensity: Float = 0.6f,
    val parallax: Boolean = true,
    val fps: Int = 30,
    val darken: Float = 0f,
    val imagePath: String? = null,
    val reducedMotion: Boolean = false,
    val wakeReveal: Boolean = true,
    val lowBatteryMode: Boolean = true,
    val unlockAnimation: Boolean = true,
    val imageFocusX: Float = 0.5f,
    val imageFocusY: Float = 0.5f,
    val tiltParallax: Boolean = false,
    val composition: Float = 0.5f,
    val palette: ScenePalette = ScenePalette.ORIGINAL,
    val musicReactive: Boolean = false,
    val customColors: List<Int> = WallpaperCatalog.find(presetId).colors,
    val customColorCount: Int = 3,
) {
    val colorCount: Int = if (palette == ScenePalette.CUSTOM && customColors.size == 3 &&
        customColorCount in 1..3) customColorCount else 3

    // A one-color look uses the first accent. Two-color looks map shadows to slot 0 and
    // highlights to slot 1. Keep the third slot intact so switching counts is reversible.
    private val tones = when (colorCount) {
        1 -> monochrome(customColors[1])
        2 -> if (customColors[0] == customColors[1]) monochrome(customColors[1])
             else listOf(customColors[0], customColors[1], customColors[1])
        else -> null
    }

    fun paletteSlots(preset: WallpaperPreset): List<Int> = when (palette) {
        ScenePalette.CUSTOM -> customColors.takeIf { it.size == 3 } ?: preset.colors
        else -> palette.colors ?: preset.colors
    }

    fun selectedColors(preset: WallpaperPreset): List<Int> = when (colorCount) {
        1 -> listOf(customColors[1])
        2 -> customColors.take(2)
        else -> paletteSlots(preset)
    }

    fun resolvedColors(preset: WallpaperPreset): List<Int> = tones ?: paletteSlots(preset)
}

private fun monochrome(color: Int): List<Int> {
    val red = (color ushr 16) and 255
    val green = (color ushr 8) and 255
    val blue = color and 255
    val peak = maxOf(red, green, blue)
    // An ink-black selection still needs graphite highlights to show the surface.
    fun highlight(channel: Int) = if (peak == 0) 112 else channel * maxOf(112, peak) / peak
    val shadow = 0xFF000000.toInt() or ((red / 18) shl 16) or ((green / 18) shl 8) or (blue / 18)
    val light = 0xFF000000.toInt() or (highlight(red) shl 16) or (highlight(green) shl 8) or highlight(blue)
    return listOf(shadow, light, light)
}

/** Stable IDs are persisted by the app and used by the live wallpaper service. */
object WallpaperCatalog {
    val presets: List<WallpaperPreset> = listOf(
        WallpaperPreset(
            "orbit-exposure", "Horizon height", "Long exposure",
            "Blue and ember star trails circle the celestial pole. Their reflection crosses a lake beneath a mountain pass.",
            WallpaperKind.ORBIT,
            listOf(0xFF010308.toInt(), 0xFF559DFF.toInt(), 0xFFFF5933.toInt()),
        ),
        WallpaperPreset(
            "orbit-threshold", "Bowl height", "Threshold",
            "A bowl of light rises from a blazing horizon, dot by dot, over a field of drifting specks.",
            WallpaperKind.ORBIT,
            listOf(0xFF010207.toInt(), 0xFF73AAFA.toInt(), 0xFFFF6A4B.toInt()),
        ),
        WallpaperPreset(
            "orbit-ringfall", "Ring angle", "Ringfall",
            "A small moon crosses a bright planetary rim as the rings sweep overhead.",
            WallpaperKind.ORBIT,
            listOf(0xFF020409.toInt(), 0xFF488CF0.toInt(), 0xFFFF502C.toInt()),
        ),
        WallpaperPreset(
            "orbit-first-light", "Sun position", "First light",
            "The first rays of the sun catch Earth's thin blue atmosphere as seen from orbit.",
            WallpaperKind.ORBIT,
            listOf(0xFF010307.toInt(), 0xFF459BF5.toInt(), 0xFFFFB16B.toInt()),
        ),
        WallpaperPreset(
            "orbit-nebula", "Dust cloud height", "Veil nebula",
            "Stars shine through blue gas while a nearer cloud of copper-edged dust drifts across them.",
            WallpaperKind.ORBIT,
            listOf(0xFF02040A.toInt(), 0xFF56C2F5.toInt(), 0xFFF0A477.toInt()),
        ),
        WallpaperPreset(
            "aurora-veil", "Curtain width", "Northern veil", "Jade curtains fold above a fjord. Steep rock faces catch their light and reflect in the water.",
            WallpaperKind.AURORA,
            listOf(0xFF031218.toInt(), 0xFF80E6D4.toInt(), 0xFF249AAA.toInt()),
        ),
        WallpaperPreset(
            "aurora-solstice", "Ray spread", "Solstice", "A low sun picks out the ledges of terraced ridges beneath a fan of rose and amber light.",
            WallpaperKind.AURORA,
            listOf(0xFF0D0820.toInt(), 0xFFE74A94.toInt(), 0xFFFF9C43.toInt()),
        ),
        WallpaperPreset(
            "ascii-signal", "Beam position", "Signal bloom", "A beam of light, resolved into characters on the diagonal.",
            WallpaperKind.ASCII,
            listOf(0xFF03070D.toInt(), 0xFF6FC8FF.toInt(), 0xFFFF5E6C.toInt()),
        ),
        WallpaperPreset(
            "gradient-daybreak", "Halftone spacing", "Daybreak", "Apricot and raspberry clouds cross a buried sun, printed in a halftone that swells with the light.",
            WallpaperKind.GRADIENT,
            listOf(0xFF170B1C.toInt(), 0xFFFF7A5C.toInt(), 0xFFD24D9B.toInt()),
        ),
        WallpaperPreset(
            "gradient-tide", "Pool height", "Low tide", "Blue-hour silk streams toward a broad, rolling pool of turquoise.",
            WallpaperKind.GRADIENT,
            listOf(0xFF04101E.toInt(), 0xFF2FC4C0.toInt(), 0xFF4A62E0.toInt()),
        ),
        WallpaperPreset(
            "glass-prism", "Rib spacing", "Fluted glass", "Light moves behind ribbed glass. Each rib bends it and splits its edges into color.",
            WallpaperKind.GLASS,
            listOf(0xFF0D0718.toInt(), 0xFFFFA865.toInt(), 0xFF8740C6.toInt()),
        ),
        WallpaperPreset(
            "abstract-ribbon", "Disc tilt", "Event horizon", "Cobalt filaments and hot copper light gather around a dark eye.",
            WallpaperKind.ABSTRACT,
            listOf(0xFF010307.toInt(), 0xFF548EF3.toInt(), 0xFFFF663B.toInt()),
        ),
        WallpaperPreset(
            "abstract-orbit", "Ribbon width", "Chromatic loop", "A beveled ribbon threads through three crossings as light travels around its polished edge.",
            WallpaperKind.ABSTRACT,
            listOf(0xFF12060F.toInt(), 0xFFF33C3E.toInt(), 0xFFFF92BD.toInt()),
        ),
        WallpaperPreset(
            "texture-basalt", "Light direction", "Basalt", "Raking light catches mineral seams along the stepped walls of a basalt canyon.",
            WallpaperKind.TEXTURE,
            listOf(0xFF080C15.toInt(), 0xFF6D88B9.toInt(), 0xFFCD9679.toInt()),
        ),
        WallpaperPreset(
            "ascii-echo", "Trace height", "Echo trace", "A phosphor sweep leaves a waterfall of glowing character traces.",
            WallpaperKind.ASCII, listOf(0xFF080611.toInt(), 0xFFE187AC.toInt(), 0xFF8785DF.toInt()),
        ),
        WallpaperPreset(
            "glass-spectrum", "Spectrum spread", "Split spectrum", "A beam sweeps through a turning glass prism and opens into a moving fan of color.",
            WallpaperKind.GLASS, listOf(0xFF060918.toInt(), 0xFF75CDE8.toInt(), 0xFFFF9785.toInt()),
        ),
        WallpaperPreset(
            "texture-satin", "Fold depth", "Folded satin", "Soft ridges of mulberry silk catch light along their folded edges.",
            WallpaperKind.TEXTURE, listOf(0xFF100A16.toInt(), 0xFFC383AD.toInt(), 0xFFEDA875.toInt()),
        ),
        WallpaperPreset(
            "texture-pixel-weave", "Pixel spacing", "Pixel weave",
            "Two folded bands of square pixels catch the light. A few glints slip from one cell to the next.",
            WallpaperKind.TEXTURE, listOf(0xFF030405.toInt(), 0xFF92979C.toInt(), 0xFFE6E6E1.toInt()),
        ),
        WallpaperPreset(
            "image-custom", "Floating lights", "Your own frame", "Bring a photo to life with gentle drift and floating light.",
            WallpaperKind.IMAGE,
            listOf(0xFF0E1A26.toInt(), 0xFF97BCDB.toInt(), 0xFFE4B59B.toInt()),
        ),
    )

    fun find(id: String): WallpaperPreset = presets.firstOrNull { it.id == id } ?: presets.first()
}
