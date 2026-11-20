package dev.sush.phosphor.model

/** Each family is drawn locally; procedural artwork never needs a network connection. */
enum class WallpaperKind { ORBIT, AURORA, ASCII, GRADIENT, GLASS, ABSTRACT, TEXTURE, IMAGE }

/** A palette is a dark ground followed by two accents. The ground keeps launcher labels readable. */
data class WallpaperPreset(
    val id: String,
    val name: String,
    val description: String,
    val kind: WallpaperKind,
    val colors: List<Int>,
)

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
)

/** Stable IDs are persisted by the app and used by the live wallpaper service. */
object WallpaperCatalog {
    val presets: List<WallpaperPreset> = listOf(
        WallpaperPreset(
            "orbit-exposure", "Long exposure",
            "Blue and ember rings sweep toward a white-hot horizon. Someone stands at the edge, watching.",
            WallpaperKind.ORBIT,
            listOf(0xFF010308.toInt(), 0xFF559DFF.toInt(), 0xFFFF5933.toInt()),
        ),
        WallpaperPreset(
            "orbit-threshold", "Threshold",
            "A bowl of light rises from a blazing horizon, dot by dot, over a field of drifting specks.",
            WallpaperKind.ORBIT,
            listOf(0xFF010207.toInt(), 0xFF73AAFA.toInt(), 0xFFFF6A4B.toInt()),
        ),
        WallpaperPreset(
            "orbit-ringfall", "Ringfall",
            "A small moon crosses a bright planetary rim as the rings sweep overhead.",
            WallpaperKind.ORBIT,
            listOf(0xFF020409.toInt(), 0xFF488CF0.toInt(), 0xFFFF502C.toInt()),
        ),
        WallpaperPreset(
            "orbit-first-light", "First light",
            "The first rays of the sun catch Earth's thin blue atmosphere as seen from orbit.",
            WallpaperKind.ORBIT,
            listOf(0xFF010307.toInt(), 0xFF459BF5.toInt(), 0xFFFFB16B.toInt()),
        ),
        WallpaperPreset(
            "orbit-nebula", "Veil nebula",
            "Blue light shines through a cleft in drifting clouds of dust, picking out their copper edges.",
            WallpaperKind.ORBIT,
            listOf(0xFF02040A.toInt(), 0xFF56C2F5.toInt(), 0xFFF0A477.toInt()),
        ),
        WallpaperPreset(
            "aurora-veil", "Northern veil", "Curtains of jade light fold and drift over a still mountain lake.",
            WallpaperKind.AURORA,
            listOf(0xFF031218.toInt(), 0xFF80E6D4.toInt(), 0xFF249AAA.toInt()),
        ),
        WallpaperPreset(
            "aurora-solstice", "Solstice", "Rose and amber shafts fan out from a low sun behind layered ridges.",
            WallpaperKind.AURORA,
            listOf(0xFF0D0820.toInt(), 0xFFE74A94.toInt(), 0xFFFF9C43.toInt()),
        ),
        WallpaperPreset(
            "ascii-signal", "Signal bloom", "A beam of light, resolved into characters on the diagonal.",
            WallpaperKind.ASCII,
            listOf(0xFF03070D.toInt(), 0xFF6FC8FF.toInt(), 0xFFFF5E6C.toInt()),
        ),
        WallpaperPreset(
            "gradient-daybreak", "Daybreak", "Apricot and raspberry clouds cross a buried sun, printed in a halftone that swells with the light.",
            WallpaperKind.GRADIENT,
            listOf(0xFF170B1C.toInt(), 0xFFFF7A5C.toInt(), 0xFFD24D9B.toInt()),
        ),
        WallpaperPreset(
            "gradient-tide", "Low tide", "Blue-hour silk streams toward a broad, rolling pool of turquoise.",
            WallpaperKind.GRADIENT,
            listOf(0xFF04101E.toInt(), 0xFF2FC4C0.toInt(), 0xFF4A62E0.toInt()),
        ),
        WallpaperPreset(
            "glass-prism", "Fluted glass", "Light moves behind ribbed glass. Each rib bends it and splits its edges into color.",
            WallpaperKind.GLASS,
            listOf(0xFF0D0718.toInt(), 0xFFFFA865.toInt(), 0xFF8740C6.toInt()),
        ),
        WallpaperPreset(
            "abstract-ribbon", "Event horizon", "Cobalt filaments and hot copper light gather around a dark eye.",
            WallpaperKind.ABSTRACT,
            listOf(0xFF010307.toInt(), 0xFF548EF3.toInt(), 0xFFFF663B.toInt()),
        ),
        WallpaperPreset(
            "abstract-orbit", "Chromatic loop", "A sculptural band twists through space, flipping color with every turn.",
            WallpaperKind.ABSTRACT,
            listOf(0xFF12060F.toInt(), 0xFFF33C3E.toInt(), 0xFFFF92BD.toInt()),
        ),
        WallpaperPreset(
            "texture-basalt", "Basalt", "Etched contours over graphite stone glint as a low sun circles.",
            WallpaperKind.TEXTURE,
            listOf(0xFF080C15.toInt(), 0xFF6D88B9.toInt(), 0xFFCD9679.toInt()),
        ),
        WallpaperPreset(
            "image-custom", "Your own frame", "Bring a photo to life with gentle drift and floating light.",
            WallpaperKind.IMAGE,
            listOf(0xFF0E1A26.toInt(), 0xFF97BCDB.toInt(), 0xFFE4B59B.toInt()),
        ),
    )

    fun find(id: String): WallpaperPreset = presets.firstOrNull { it.id == id } ?: presets.first()
}
