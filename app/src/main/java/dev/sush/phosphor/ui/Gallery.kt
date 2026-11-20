package dev.sush.phosphor.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sush.phosphor.R
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperKind
import dev.sush.phosphor.model.WallpaperPreset
import dev.sush.phosphor.model.WallpaperSettings
import dev.sush.phosphor.ui.theme.Motion
import dev.sush.phosphor.ui.theme.Palette

val Collections = listOf("All", "Orbit", "Aurora", "ASCII", "Gradients", "Glass", "Abstract", "Textures", "Your images")

fun WallpaperKind.label(): String = when (this) {
    WallpaperKind.ORBIT -> "Orbit"
    WallpaperKind.AURORA -> "Aurora"
    WallpaperKind.ASCII -> "ASCII"
    WallpaperKind.GRADIENT -> "Gradients"
    WallpaperKind.GLASS -> "Glass"
    WallpaperKind.ABSTRACT -> "Abstract"
    WallpaperKind.TEXTURE -> "Textures"
    WallpaperKind.IMAGE -> "Your images"
}

/** Phone-shaped plates: wallpapers are shown at the proportions they will be used at. */
const val PLATE_RATIO = 0.5f

internal data class GalleryWallpaper(
    val preset: WallpaperPreset,
    val settings: WallpaperSettings,
    val look: SavedLook?,
) {
    val title: String get() = look?.name ?: preset.name
}

private val featured = listOf("orbit-exposure", "ascii-signal", "glass-orbit")

internal fun galleryWallpapers(
    saved: Boolean, looks: List<SavedLook>, active: WallpaperSettings, imagePath: String?,
): List<GalleryWallpaper> {
    if (saved) return looks.mapNotNull { look ->
        val preset = WallpaperCatalog.presets.firstOrNull { it.id == look.settings.presetId } ?: return@mapNotNull null
        GalleryWallpaper(preset, look.settings, look)
    }
    return WallpaperCatalog.presets
        .filter { it.kind != WallpaperKind.IMAGE || imagePath != null }
        .sortedBy { featured.indexOf(it.id).let { rank -> if (rank < 0) featured.size else rank } }
        .map { preset -> GalleryWallpaper(preset, active.forPreset(preset, imagePath), null) }
}

@Composable
internal fun Gallery(
    saved: Boolean,
    category: String,
    wallpapers: List<GalleryWallpaper>,
    looks: List<SavedLook>,
    liveSettings: WallpaperSettings?,
    night: Boolean,
    onNight: (Boolean) -> Unit,
    onCategory: (String) -> Unit,
    onSelect: (GalleryWallpaper, Boolean) -> Unit,
    onFavorite: (GalleryWallpaper) -> Unit,
    onImport: () -> Unit,
    onExplore: () -> Unit,
) {
    LazyVerticalGrid(
        modifier = Modifier.widthIn(max = 1200.dp).fillMaxSize(),
        columns = GridCells.Adaptive(160.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (saved) "Saved" else "Phosphor", style = MaterialTheme.typography.displayMedium, modifier = Modifier.weight(1f).semantics { heading() })
                    MoodSwitch(night, onNight)
                }
                Text(
                    if (saved) "Your saved wallpapers and their settings."
                    else "Wallpapers drawn live on your phone. At night they glow; by day they print in ink.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 24.dp),
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Collections) { label ->
                        FilterChip(selected = category == label, onClick = { onCategory(label) }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                if (!saved) OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Glyph(R.drawable.ic_import, null, Modifier.size(20.dp))
                    Text("Bring in your own image", modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
        if (wallpapers.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Glyph(if (saved) R.drawable.ic_saved else R.drawable.ic_import, null, Modifier.size(32.dp), MaterialTheme.colorScheme.primary)
                val filtered = saved && looks.isNotEmpty() && category != "All"
                Text(when {
                    filtered -> "No saved looks in this collection"
                    saved -> "Nothing saved yet"
                    else -> "Start with your own image"
                }, style = MaterialTheme.typography.headlineSmall)
                Text(
                    when {
                        filtered -> "Your other saved looks are in All."
                        saved -> "Use the heart on a wallpaper or save a look from the editor."
                        else -> "Choose a photo or artwork and Phosphor adds gentle motion and light."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = when {
                    filtered -> { { onCategory("All") } }
                    saved -> onExplore
                    else -> onImport
                }) { Text(if (filtered) "Show all saved" else if (saved) "Browse wallpapers" else "Choose an image") }
            }
        }
        items(wallpapers, key = { it.look?.id ?: it.preset.id }) { entry ->
            val preset = entry.preset
            val isActive = liveSettings == entry.settings
            Column {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(PLATE_RATIO).clip(MaterialTheme.shapes.extraLarge)
                        .clickable(role = Role.Button, onClickLabel = "Open ${entry.title}") { onSelect(entry, night) },
                ) {
                    Crossfade(night, animationSpec = tween(if (entry.settings.reducedMotion) 0 else Motion.MOOD), label = "plate") { lit ->
                        WallpaperPreview(preset, entry.settings, Modifier.fillMaxSize(), night = lit)
                    }
                    Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), shape = CircleShape, color = Color.Black.copy(alpha = .6f)) {
                        val on = entry.look != null || looks.any { it.settings == entry.settings }
                        IconButton(onClick = { onFavorite(entry) }, modifier = Modifier.semantics { contentDescription = if (on) "Remove ${entry.title} from saved" else "Save ${entry.title}" }) {
                            Glyph(if (on) R.drawable.ic_saved_filled else R.drawable.ic_saved, null, tint = Color.White)
                        }
                    }
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium)
                        Text(if (isActive) "On your phone" else if (entry.title != preset.name) preset.name else preset.kind.label(), style = MaterialTheme.typography.bodySmall,
                            color = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Swatches(preset.palette(night), 14.dp)
                }
                // Ember horizon: the one place the accent appears, marking the live wallpaper.
                if (isActive) Box(Modifier.padding(top = 6.dp).width(28.dp).height(2.dp).clip(CircleShape).background(Palette.Ember))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            AppAttribution(Modifier.fillMaxWidth().padding(top = 12.dp))
        }
    }
}
