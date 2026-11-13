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
            "Thousands of light trails pour into a white-hot horizon. Someone stands at the edge, watching.",
            WallpaperKind.ORBIT, Scheme(262f, Harmony.SPLIT),
            "Swipe to spin the orbits. Hold to heat the horizon, then let go.",
        ),
        preset(
            "orbit-threshold", "Threshold",
            "A bowl of light rises from a blazing horizon, dot by dot, over a field of drifting specks.",
            WallpaperKind.ORBIT, Scheme(248f, Harmony.SPLIT, accentShift = -12f),
            "Swipe to spin the rings. Hold to light the horizon, then let go.",
        ),
        preset(
            "orbit-ringfall", "Ringfall",
            "A planet’s rim catches fire as its rings sweep down toward the clouds.",
            WallpaperKind.ORBIT, Scheme(285f, Harmony.TRIADIC, accentShift = -10f),
            "Swipe to spin the rings. Hold to raise the sun behind the planet, then let go.",
        ),
        preset(
            "aurora-veil", "Northern veil", "Curtains of jade light fold and drift over a still mountain lake.",
            WallpaperKind.AURORA, Scheme(162f, Harmony.TRIADIC),
            "Swipe through the curtain to fold it. Hold to build a substorm, then let go.",
        ),
        preset(
            "aurora-solstice", "Solstice", "Rose and amber rays crown the sky above a twilight snowfield.",
            WallpaperKind.AURORA, Scheme(355f, Harmony.ANALOGOUS, accentShift = 25f),
            "Swipe through the curtain to fold it. Hold to build a substorm, then let go.",
        ),
        preset(
            "ascii-rain", "Digital rain",
            "Green code falls through three layers of depth. Characters flicker as the light passes over them.",
            WallpaperKind.ASCII, Scheme(146f, Harmony.ANALOGOUS, accentShift = -12f, chroma = 1.3f, nightGround = 0.15f),
            "Swipe to part the rain. Hold to gather the code, then let go to send a ring of it.",
        ),
        preset(
            "ascii-signal", "Signal bloom", "A beam of light, resolved into characters on the diagonal.",
            WallpaperKind.ASCII, Scheme(228f, Harmony.SPLIT),
            "Swipe to write through the signal. Hold to gather it, then let go to scramble.",
        ),
        preset(
            "ascii-terminal", "Afterimage", "Phosphor-green traces rise and fade like afterimages on an old tube.",
            WallpaperKind.ASCII, Scheme(150f, Harmony.ANALOGOUS),
            "Swipe to draw on the phosphor. Hold to burn it in, then let go to degauss.",
        ),
        preset(
            "gradient-daybreak", "Daybreak", "Apricot and raspberry clouds over a buried sun, printed in a halftone that swells with the light.",
            WallpaperKind.GRADIENT, Scheme(50f, Harmony.ANALOGOUS, clockwise = false, accentShift = -15f, chroma = 1.1f, dayLight = 0.74f),
            "Swipe to stir the ink. Hold to focus the sun into a point, then let go.",
        ),
        preset(
            "gradient-tide", "Low tide", "Blue-hour silk streams toward a pool of turquoise.",
            WallpaperKind.GRADIENT, Scheme(192f, Harmony.ANALOGOUS, accentShift = 42f, chroma = 1.05f, nightLight = 0.68f, dayLight = 0.72f),
            "Swipe to comb the silk. Hold to gather the light, then let go.",
        ),
        preset(
            "glass-prism", "Fluted glass", "Light moves behind ribbed glass; every rib bends it and splits its edges into color.",
            WallpaperKind.GLASS, Scheme(55f, Harmony.TRIADIC, clockwise = false, dayLight = 0.74f),
            "Swipe to drag the light behind the glass. Hold to focus it, then let go.",
        ),
        preset(
            "glass-orbit", "Soft refraction", "Weightless glass lenses flip and magnify a printed light, fringed in color.",
            WallpaperKind.GLASS, Scheme(298f, Harmony.TRIADIC, clockwise = false, accentShift = 15f, dayLight = 0.72f),
            "Drag a lens. Hold to focus the nearest one into a burning point.",
        ),
        preset(
            "abstract-ribbon", "Event horizon", "Hundreds of filaments fall into a dark eye above a burning planet limb.",
            WallpaperKind.ABSTRACT, Scheme(256f, Harmony.SPLIT, accentShift = -8f),
            "Swipe to part the filaments. Hold to gather a knot of light, then let go.",
        ),
        preset(
            "abstract-orbit", "Chromatic loop", "A sculptural band twists through space, flipping color with every turn.",
            WallpaperKind.ABSTRACT, Scheme(192f, Harmony.SPLIT, clockwise = false),
            "Swipe to spin the loop. Hold to light it and wind it up, then let go.",
        ),
        preset(
            "texture-dune", "Rose silk", "Folds of rose silk lit by a light you can move, under a fine halftone screen.",
            WallpaperKind.TEXTURE, Scheme(352f, Harmony.ANALOGOUS, dayLight = 0.68f),
            "Hold to light and press the silk. Swipe to crease it.",
        ),
        preset(
            "texture-basalt", "Basalt", "Etched contours over graphite stone, glinting as a low sun circles.",
            WallpaperKind.TEXTURE, Scheme(222f, Harmony.COMPLEMENTARY, chroma = 0.45f),
            "Swipe to etch a glowing fissure. Hold to heat the stone, then let go.",
        ),
        preset(
            "image-custom", "Your own frame", "Bring a photo to life with gentle drift and floating light.",
            WallpaperKind.IMAGE, Scheme(240f, Harmony.SPLIT, chroma = 0.6f),
            "Tap to send a ring of light across your image.",
        ),
    )

    fun find(id: String): WallpaperPreset = presets.firstOrNull { it.id == id } ?: presets.first()
}
