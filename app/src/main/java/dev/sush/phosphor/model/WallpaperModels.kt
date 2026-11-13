package dev.sush.phosphor.model

/** Each family is drawn locally; procedural artwork never needs a network connection. */
enum class WallpaperKind { ORBIT, AURORA, ASCII, GRADIENT, GLASS, ABSTRACT, TEXTURE, IMAGE }

/**
 * How the artwork chooses between its two treatments.
 *
 * Night renders emitted light on a dark ground, so launchers keep white labels. Day renders
 * the same scene as overprinted ink on paper, so launchers can switch to dark labels.
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
    val presets: List<WallpaperPreset> = listOf(
        WallpaperPreset(
            "orbit-exposure", "Long exposure",
            "Thousands of light trails pour into a white-hot horizon. Someone stands at the edge, watching.",
            WallpaperKind.ORBIT,
            listOf(0xFF03060F.toInt(), 0xFF5C8DFF.toInt(), 0xFFFF6A3A.toInt()),
            listOf(0xFFEDF0F5.toInt(), 0xFF2742B8.toInt(), 0xFFE8502A.toInt()),
            "Swipe to spin the orbits. Hold to heat the horizon, then let go.",
        ),
        WallpaperPreset(
            "orbit-threshold", "Threshold",
            "A bowl of light rises from a blazing horizon, dot by dot, over a field of drifting specks.",
            WallpaperKind.ORBIT,
            listOf(0xFF02040B.toInt(), 0xFF7FA4FF.toInt(), 0xFFFF5A47.toInt()),
            listOf(0xFFF0F1F4.toInt(), 0xFF1F3C9E.toInt(), 0xFFD9432F.toInt()),
            "Swipe to spin the rings. Hold to light the horizon, then let go.",
        ),
        WallpaperPreset(
            "orbit-ringfall", "Ringfall",
            "A planet’s rim catches fire as its rings sweep down toward the clouds.",
            WallpaperKind.ORBIT,
            listOf(0xFF04050C.toInt(), 0xFF6E9BFF.toInt(), 0xFFFF4E3B.toInt()),
            listOf(0xFFF1EFF0.toInt(), 0xFF233A9A.toInt(), 0xFFD6402F.toInt()),
            "Swipe to spin the rings. Hold to raise the sun behind the planet, then let go.",
        ),
        WallpaperPreset(
            "aurora-veil", "Northern veil", "Curtains of jade light fold and drift over a still mountain lake.",
            WallpaperKind.AURORA,
            listOf(0xFF040D14.toInt(), 0xFF5CF2B4.toInt(), 0xFF8E7BFF.toInt()),
            listOf(0xFFEEF3F1.toInt(), 0xFF0E8A6A.toInt(), 0xFF5B49C8.toInt()),
            "Swipe through the curtain to fold it. Hold to build a substorm, then let go.",
        ),
        WallpaperPreset(
            "aurora-solstice", "Solstice", "Rose and amber rays crown the sky above a twilight snowfield.",
            WallpaperKind.AURORA,
            listOf(0xFF100711.toInt(), 0xFFFF6FB0.toInt(), 0xFFFFB86B.toInt()),
            listOf(0xFFF6EFF0.toInt(), 0xFFC22E74.toInt(), 0xFFD9822B.toInt()),
            "Swipe through the curtain to fold it. Hold to build a substorm, then let go.",
        ),
        WallpaperPreset(
            "ascii-signal", "Signal bloom", "A beam of light, resolved into characters on the diagonal.",
            WallpaperKind.ASCII,
            listOf(0xFF03070D.toInt(), 0xFF6FC8FF.toInt(), 0xFFFF5E6C.toInt()),
            listOf(0xFFEFF1F3.toInt(), 0xFF1D5FA8.toInt(), 0xFFD23A4A.toInt()),
            "Swipe to write through the signal. Hold to gather it, then let go to scramble.",
        ),
        WallpaperPreset(
            "ascii-terminal", "Afterimage", "Phosphor-green traces rise and fade like afterimages on an old tube.",
            WallpaperKind.ASCII,
            listOf(0xFF020A08.toInt(), 0xFF8CFFB0.toInt(), 0xFF3FB8A8.toInt()),
            listOf(0xFFEDF2EE.toInt(), 0xFF1A7A4A.toInt(), 0xFF197A70.toInt()),
            "Swipe to draw on the phosphor. Hold to burn it in, then let go to degauss.",
        ),
        WallpaperPreset(
            "gradient-daybreak", "Daybreak", "Apricot and raspberry clouds over a buried sun, printed in a halftone that swells with the light.",
            WallpaperKind.GRADIENT,
            listOf(0xFF170B1C.toInt(), 0xFFFF7A5C.toInt(), 0xFFD24D9B.toInt()),
            listOf(0xFFFBEFE6.toInt(), 0xFFF59A7A.toInt(), 0xFFF2C46B.toInt()),
            "Swipe to stir the ink. Hold to focus the sun into a point, then let go.",
        ),
        WallpaperPreset(
            "gradient-tide", "Low tide", "Blue-hour silk streams toward a pool of turquoise.",
            WallpaperKind.GRADIENT,
            listOf(0xFF04101E.toInt(), 0xFF2FC4C0.toInt(), 0xFF4A62E0.toInt()),
            listOf(0xFFEAF3F4.toInt(), 0xFF7FD3CB.toInt(), 0xFF9EAEF2.toInt()),
            "Swipe to comb the silk. Hold to gather the light, then let go.",
        ),
        WallpaperPreset(
            "glass-prism", "Fluted glass", "Light moves behind ribbed glass; every rib bends it and splits its edges into color.",
            WallpaperKind.GLASS,
            listOf(0xFF0D0716.toInt(), 0xFFFF8A4C.toInt(), 0xFF7A3CD6.toInt()),
            listOf(0xFFF7EEE8.toInt(), 0xFFF5A35E.toInt(), 0xFFB27BE8.toInt()),
            "Swipe to drag the light behind the glass. Hold to focus it, then let go.",
        ),
        WallpaperPreset(
            "glass-orbit", "Soft refraction", "Weightless glass lenses flip and magnify a printed light, fringed in color.",
            WallpaperKind.GLASS,
            listOf(0xFF0B0A1A.toInt(), 0xFFB79CFF.toInt(), 0xFF62D6D0.toInt()),
            listOf(0xFFF0EEF6.toInt(), 0xFFB59BEF.toInt(), 0xFF7FD1CB.toInt()),
            "Drag a lens. Hold to focus the nearest one into a burning point.",
        ),
        WallpaperPreset(
            "abstract-ribbon", "Event horizon", "Hundreds of filaments fall into a dark eye above a burning planet limb.",
            WallpaperKind.ABSTRACT,
            listOf(0xFF030812.toInt(), 0xFF4F8EE0.toInt(), 0xFFFF7E45.toInt()),
            listOf(0xFFEEF1F5.toInt(), 0xFF2A5BA8.toInt(), 0xFFE0602A.toInt()),
            "Swipe to part the filaments. Hold to gather a knot of light, then let go.",
        ),
        WallpaperPreset(
            "abstract-orbit", "Chromatic loop", "A sculptural band twists through space, flipping color with every turn.",
            WallpaperKind.ABSTRACT,
            listOf(0xFF06121A.toInt(), 0xFF5FD8C6.toInt(), 0xFFFF9466.toInt()),
            listOf(0xFFEFF2F1.toInt(), 0xFF1E8C80.toInt(), 0xFFD9683A.toInt()),
            "Swipe to spin the loop. Hold to light it and wind it up, then let go.",
        ),
        WallpaperPreset(
            "texture-dune", "Rose silk", "Folds of rose silk lit by a light you can move, under a fine halftone screen.",
            WallpaperKind.TEXTURE,
            listOf(0xFF140810.toInt(), 0xFFE07AAE.toInt(), 0xFFFFD6EA.toInt()),
            listOf(0xFFF8EEF2.toInt(), 0xFFE7A2C4.toInt(), 0xFFB2507F.toInt()),
            "Hold to light and press the silk. Swipe to crease it.",
        ),
        WallpaperPreset(
            "texture-basalt", "Basalt", "Etched contours over graphite stone, glinting as a low sun circles.",
            WallpaperKind.TEXTURE,
            listOf(0xFF0B1013.toInt(), 0xFF7FA0A2.toInt(), 0xFFD8D6BC.toInt()),
            listOf(0xFFF0F0EB.toInt(), 0xFF5F7A7C.toInt(), 0xFF3A4446.toInt()),
            "Swipe to etch a glowing fissure. Hold to heat the stone, then let go.",
        ),
        WallpaperPreset(
            "image-custom", "Your own frame", "Bring a photo to life with gentle drift and floating light.",
            WallpaperKind.IMAGE,
            listOf(0xFF0E1A26.toInt(), 0xFF97BCDB.toInt(), 0xFFE4B59B.toInt()),
            listOf(0xFFF1F3F6.toInt(), 0xFF6F8FB0.toInt(), 0xFFC98E6E.toInt()),
            "Tap to send a ring of light across your image.",
        ),
    )

    fun find(id: String): WallpaperPreset = presets.firstOrNull { it.id == id } ?: presets.first()
}
