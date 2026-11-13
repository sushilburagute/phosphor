package dev.sush.phosphor.model

/** Each family is drawn locally; procedural artwork never needs a network connection. */
enum class WallpaperKind { ORBIT, AURORA, ASCII, GRADIENT, GLASS, ABSTRACT, TEXTURE, IMAGE }

/**
 * How the artwork chooses between its two treatments.
 *
 * Night renders emitted light on a dark ground, so launchers keep white labels. Day renders
 * the same scene in daylight color on pale paper, so launchers can switch to dark labels.
 */
enum class Appearance { AUTO, NIGHT, DAY }

/**
 * A palette is always ground, then two accents. Night accents glow; day accents are inks.
 * Ground luminance is what keeps launcher labels readable, so keep night grounds deep and
 * day grounds pale when adding presets. The legibility test enforces this.
 */
data class WallpaperPreset(
    val id: String,
    val name: String,
    val description: String,
    val kind: WallpaperKind,
    val colors: List<Int>,
    val dayColors: List<Int> = colors,
    val interaction: String = "Tap to send a ripple through the artwork.",
) {
    fun palette(night: Boolean): List<Int> = if (night) colors else dayColors
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
    val appearance: Appearance = Appearance.AUTO,
    val wakeReveal: Boolean = true,
    val lowBatteryMode: Boolean = true,
    val unlockAnimation: Boolean = true,
)

/** Stable IDs are persisted by the app and used by the live wallpaper service. */
object WallpaperCatalog {
    private fun preset(
        id: String, name: String, description: String, kind: WallpaperKind, scheme: Scheme, interaction: String,
    ) = WallpaperPreset(id, name, description, kind, scheme.night(), scheme.day(), interaction)

    val presets: List<WallpaperPreset> = listOf(
        preset(
            "orbit-exposure", "Long exposure",
            "Thousands of light trails circle a glowing horizon. Someone stands at the edge, watching.",
            WallpaperKind.ORBIT, Scheme(262f, Harmony.SPLIT),
            "Tap to bend the orbits. Drag to pull their center.",
        ),
        preset(
            "orbit-threshold", "Threshold",
            "Rings of light gather above a horizon, dot by dot, over a quiet field.",
            WallpaperKind.ORBIT, Scheme(248f, Harmony.SPLIT, accentShift = -12f),
            "Tap to send a pulse along the rings.",
        ),
        preset(
            "orbit-ringfall", "Ringfall",
            "A planet’s rim catches fire as its rings sweep overhead.",
            WallpaperKind.ORBIT, Scheme(285f, Harmony.TRIADIC, accentShift = -10f),
            "Tap to flare the rim. Drag to tilt the rings.",
        ),
        preset(
            "aurora-veil", "Northern veil", "Curtains of jade light hang from a slow, folding wave.",
            WallpaperKind.AURORA, Scheme(162f, Harmony.TRIADIC),
            "Tap to flare the curtain. Drag to fold it.",
        ),
        preset(
            "aurora-solstice", "Solstice", "Rose and amber rays rise over a dark ridge.",
            WallpaperKind.AURORA, Scheme(355f, Harmony.ANALOGOUS, accentShift = 25f),
            "Tap to flare the curtain. Drag to fold it.",
        ),
        preset(
            "ascii-rain", "Digital rain",
            "Green code falls through three layers of depth. Characters flicker as the light passes over them.",
            WallpaperKind.ASCII, Scheme(146f, Harmony.ANALOGOUS, accentShift = -12f, chroma = 1.3f, nightGround = 0.15f),
            "Tap to send a ring of code. Drag to part the rain.",
        ),
        preset(
            "ascii-signal", "Signal bloom", "A beam of light, resolved into characters on the diagonal.",
            WallpaperKind.ASCII, Scheme(228f, Harmony.SPLIT),
            "Tap to scramble the signal. Drag to bend the beam.",
        ),
        preset(
            "ascii-terminal", "Afterimage", "Phosphor-green glyphs trace a wave and fade behind a slow scanline.",
            WallpaperKind.ASCII, Scheme(150f, Harmony.ANALOGOUS),
            "Tap to scramble the signal. Drag to bend the wave.",
        ),
        preset(
            "gradient-daybreak", "Daybreak", "Apricot and raspberry melt together under a fine halftone.",
            WallpaperKind.GRADIENT, Scheme(50f, Harmony.ANALOGOUS, clockwise = false, accentShift = -15f, chroma = 1.1f),
            "Drag to stir the color. Tap to drop a ripple.",
        ),
        preset(
            "gradient-tide", "Low tide", "Blue hour melts into a pool of turquoise.",
            WallpaperKind.GRADIENT, Scheme(192f, Harmony.ANALOGOUS, accentShift = 42f, chroma = 1.05f, nightLight = 0.68f),
            "Drag to stir the color. Tap to drop a ripple.",
        ),
        preset(
            "glass-prism", "Fluted glass", "Light moves behind a pane of ribbed glass and splits into strips.",
            WallpaperKind.GLASS, Scheme(55f, Harmony.TRIADIC, clockwise = false),
            "Drag to move the light behind the glass.",
        ),
        preset(
            "glass-orbit", "Soft refraction", "Weightless glass lenses flip and magnify the light behind them.",
            WallpaperKind.GLASS, Scheme(298f, Harmony.TRIADIC, clockwise = false, accentShift = 15f),
            "Drag a lens. Tap to make them wobble.",
        ),
        preset(
            "abstract-ribbon", "Event horizon", "Cobalt filaments and a hot copper line curve through space.",
            WallpaperKind.ABSTRACT, Scheme(256f, Harmony.SPLIT, accentShift = -8f),
            "Tap or drag to part the filaments.",
        ),
        preset(
            "abstract-orbit", "Chromatic loop", "Sculptural bands turn and split into color at their edges.",
            WallpaperKind.ABSTRACT, Scheme(192f, Harmony.SPLIT, clockwise = false),
            "Tap to send a pulse around the loop.",
        ),
        preset(
            "texture-dune", "Rose silk", "Folds of rose silk with a peach crest, under a fine halftone screen.",
            WallpaperKind.TEXTURE, Scheme(352f, Harmony.ANALOGOUS),
            "Tap to ripple the silk.",
        ),
        preset(
            "texture-basalt", "Basalt", "Etched contour lines over graphite stone, catching mineral light.",
            WallpaperKind.TEXTURE, Scheme(222f, Harmony.COMPLEMENTARY, chroma = 0.45f),
            "Drag to press into the contours.",
        ),
        preset(
            "image-custom", "Your own frame", "Bring a photo to life with gentle drift and floating light.",
            WallpaperKind.IMAGE, Scheme(240f, Harmony.SPLIT, chroma = 0.6f),
            "Tap to send a ring of light across your image.",
        ),
    )

    fun find(id: String): WallpaperPreset = presets.firstOrNull { it.id == id } ?: presets.first()
}
