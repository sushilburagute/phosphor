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
) {
    fun resolvedColors(preset: WallpaperPreset): List<Int> = when (palette) {
        ScenePalette.CUSTOM -> customColors.takeIf { it.size == 3 } ?: preset.colors
        else -> palette.colors ?: preset.colors
    }
}

/** Stable IDs are persisted by the app and used by the live wallpaper service. */
object WallpaperCatalog {
    val presets: List<WallpaperPreset> = listOf(
        WallpaperPreset(
            "orbit-exposure", "Horizon height", "Long exposure",
            "Blue and ember rings sweep toward a white-hot horizon. Someone stands at the edge, watching.",
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
            "Blue light shines through a cleft in drifting clouds of dust, picking out their copper edges.",
            WallpaperKind.ORBIT,
            listOf(0xFF02040A.toInt(), 0xFF56C2F5.toInt(), 0xFFF0A477.toInt()),
        ),
        WallpaperPreset(
            "aurora-veil", "Curtain width", "Northern veil", "Curtains of jade light fold and drift over a still mountain lake.",
            WallpaperKind.AURORA,
            listOf(0xFF031218.toInt(), 0xFF80E6D4.toInt(), 0xFF249AAA.toInt()),
        ),
        WallpaperPreset(
            "aurora-solstice", "Ray spread", "Solstice", "Rose and amber shafts fan out from a low sun behind layered ridges.",
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
            "abstract-orbit", "Ribbon width", "Chromatic loop", "A sculptural band twists through space, flipping color with every turn.",
            WallpaperKind.ABSTRACT,
            listOf(0xFF12060F.toInt(), 0xFFF33C3E.toInt(), 0xFFFF92BD.toInt()),
        ),
        WallpaperPreset(
            "texture-basalt", "Light direction", "Basalt", "Etched contours over graphite stone glint as a low sun circles.",
            WallpaperKind.TEXTURE,
            listOf(0xFF080C15.toInt(), 0xFF6D88B9.toInt(), 0xFFCD9679.toInt()),
        ),
        WallpaperPreset(
            "ascii-echo", "Trace height", "Echo trace", "A phosphor sweep leaves a waterfall of glowing character traces.",
            WallpaperKind.ASCII, listOf(0xFF080611.toInt(), 0xFFE187AC.toInt(), 0xFF8785DF.toInt()),
        ),
        WallpaperPreset(
            "glass-spectrum", "Spectrum spread", "Split spectrum", "A turning triangular prism opens a narrow beam into a fan of color.",
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
