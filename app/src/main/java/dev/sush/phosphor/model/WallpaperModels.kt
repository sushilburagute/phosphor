package dev.sush.phosphor.model

/** Each family is drawn locally; procedural artwork never needs a network connection. */
enum class WallpaperKind { AURORA, ASCII, GRADIENT, GLASS, ABSTRACT, TEXTURE, IMAGE }

data class WallpaperPreset(
    val id: String,
    val name: String,
    val description: String,
    val kind: WallpaperKind,
    val colors: List<Int>,
)

data class WallpaperSettings(
    val presetId: String = "aurora-veil",
    val speed: Float = 0.5f,
    val intensity: Float = 0.6f,
    val parallax: Boolean = true,
    val fps: Int = 30,
    val darken: Float = 0f,
    val imagePath: String? = null,
    val reducedMotion: Boolean = false,
    val lowBatteryMode: Boolean = true,
    val unlockAnimation: Boolean = true,
)

/** Stable IDs are persisted by the app and used by the live wallpaper service. */
object WallpaperCatalog {
    val presets: List<WallpaperPreset> = listOf(
        WallpaperPreset(
            "aurora-veil", "Northern veil", "Slow curtains of jade, turquoise and violet light.",
            WallpaperKind.AURORA, listOf(0xFF071D27.toInt(), 0xFF69E6BF.toInt(), 0xFF8279E5.toInt()),
        ),
        WallpaperPreset(
            "aurora-solstice", "Solstice", "Rose and amber ribbons drift through a midnight sky.",
            WallpaperKind.AURORA, listOf(0xFF27152F.toInt(), 0xFFF28BA9.toInt(), 0xFFF5BE81.toInt()),
        ),
        WallpaperPreset(
            "ascii-signal", "Signal bloom", "A luminous stream of characters, flowing on the diagonal.",
            WallpaperKind.ASCII, listOf(0xFF081423.toInt(), 0xFF88DDFF.toInt(), 0xFFFF807B.toInt()),
        ),
        WallpaperPreset(
            "ascii-terminal", "Afterimage", "Phosphor-green glyphs flicker across a soft digital wave.",
            WallpaperKind.ASCII, listOf(0xFF071A16.toInt(), 0xFFA6EEBA.toInt(), 0xFF4A9D9D.toInt()),
        ),
        WallpaperPreset(
            "gradient-daybreak", "Daybreak", "Apricot, raspberry and lavender blend like morning light.",
            WallpaperKind.GRADIENT, listOf(0xFF5D54A5.toInt(), 0xFFF4A18B.toInt(), 0xFFF6D6A0.toInt()),
        ),
        WallpaperPreset(
            "gradient-tide", "Low tide", "Blue hour melts into a luminous pool of turquoise.",
            WallpaperKind.GRADIENT, listOf(0xFF12345A.toInt(), 0xFF57D9D0.toInt(), 0xFFB5BEF7.toInt()),
        ),
        WallpaperPreset(
            "glass-prism", "Prism study", "Translucent, iridescent ribs bend a ribbon of light.",
            WallpaperKind.GLASS, listOf(0xFF141D35.toInt(), 0xFFA7BBEF.toInt(), 0xFFF1AF9D.toInt()),
        ),
        WallpaperPreset(
            "glass-orbit", "Soft refraction", "Weightless glass lenses catch pink and blue reflections.",
            WallpaperKind.GLASS, listOf(0xFF25203E.toInt(), 0xFFC3ACEB.toInt(), 0xFF91DAD7.toInt()),
        ),
        WallpaperPreset(
            "abstract-ribbon", "Event horizon", "Cobalt filaments and a hot copper line curve through space.",
            WallpaperKind.ABSTRACT, listOf(0xFF071528.toInt(), 0xFF609AD4.toInt(), 0xFFF78B56.toInt()),
        ),
        WallpaperPreset(
            "abstract-orbit", "Chromatic loop", "Sculptural bands turn in a quiet field of ink.",
            WallpaperKind.ABSTRACT, listOf(0xFF162A31.toInt(), 0xFF72CDC1.toInt(), 0xFFE5A07B.toInt()),
        ),
        WallpaperPreset(
            "texture-dune", "Rose terrain", "Soft folds, halftone shadows and a touch of paper grain.",
            WallpaperKind.TEXTURE, listOf(0xFF42283F.toInt(), 0xFFE4A9C4.toInt(), 0xFFF5DCE4.toInt()),
        ),
        WallpaperPreset(
            "texture-basalt", "Basalt", "Graphite contours, mineral light and fine etched texture.",
            WallpaperKind.TEXTURE, listOf(0xFF202C31.toInt(), 0xFF829A9B.toInt(), 0xFFCFD0B9.toInt()),
        ),
        WallpaperPreset(
            "image-custom", "Your own frame", "Bring a photo to life with gentle drift and floating light.",
            WallpaperKind.IMAGE, listOf(0xFF172B3C.toInt(), 0xFF97BCDB.toInt(), 0xFFE4B59B.toInt()),
        ),
    )

    fun find(id: String): WallpaperPreset = presets.firstOrNull { it.id == id } ?: presets.first()
}
