package dev.sush.phosphor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sush.phosphor.R
import dev.sush.phosphor.platform.SavedLook
import dev.sush.phosphor.platform.forPreset
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperKind
import dev.sush.phosphor.model.WallpaperPreset
import dev.sush.phosphor.model.WallpaperSettings

private fun WallpaperKind.label(): String = when (this) {
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

private val featured = listOf("orbit-exposure", "ascii-signal", "glass-prism")

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Gallery(
    saved: Boolean,
    category: String,
    wallpapers: List<GalleryWallpaper>,
    looks: List<SavedLook>,
    liveSettings: WallpaperSettings?,
    onCategory: (String) -> Unit,
    onSelect: (GalleryWallpaper) -> Unit,
    onFavorite: (GalleryWallpaper) -> Unit,
    onImport: () -> Unit,
    onExplore: () -> Unit,
    onRotation: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val kinds = WallpaperKind.entries.filter { kind -> wallpapers.any { it.preset.kind == kind } }
    val selectedKind = kinds.firstOrNull { kinds.size > 1 && it.label() == category }
    val displayed = if (selectedKind == null) wallpapers else wallpapers.filter { it.preset.kind == selectedKind }

    Column(Modifier.widthIn(max = 1200.dp).fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    if (saved) "Saved" else "Wallpapers",
                    modifier = Modifier.semantics { heading() },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            },
            actions = {
                if (saved) {
                    TextButton(onClick = onRotation) { Text("Rotation") }
                } else {
                    if (fontScale > 1.5f) {
                        IconButton(onClick = onImport) { Glyph(R.drawable.ic_import, "Import photo") }
                    } else {
                        TextButton(onClick = onImport, modifier = Modifier.padding(end = 4.dp).heightIn(min = 48.dp)) {
                            Text("Import photo", maxLines = 1)
                        }
                    }
                }
            },
        )
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Adaptive(136.dp * fontScale.coerceIn(1f, 1.5f)),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (kinds.size > 1) item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All") + kinds.map { it.label() }, key = { it }) { label ->
                        FilterChip(
                            selected = label == (selectedKind?.label() ?: "All"),
                            onClick = { onCategory(label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            if (displayed.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Nothing saved yet", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onExplore, modifier = Modifier.heightIn(min = 48.dp)) { Text("Browse wallpapers") }
                }
            }
            items(displayed, key = { it.look?.id ?: it.preset.id }) { entry ->
                WallpaperTile(
                    entry,
                    active = liveSettings == entry.settings,
                    saved = entry.look != null || looks.any { it.settings == entry.settings },
                    onOpen = { onSelect(entry) },
                    onSave = { onFavorite(entry) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                AppAttribution(Modifier.fillMaxWidth().padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun WallpaperTile(
    entry: GalleryWallpaper,
    active: Boolean,
    saved: Boolean,
    onOpen: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        Modifier.clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = "Open ${entry.title}", onClick = onOpen),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(PLATE_RATIO).clip(MaterialTheme.shapes.medium)) {
            WallpaperPreview(entry.preset, entry.settings, Modifier.fillMaxSize())
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                if (active) Text(
                    "On your phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            IconButton(onClick = onSave) {
                Glyph(
                    if (saved) R.drawable.ic_saved_filled else R.drawable.ic_saved,
                    if (saved) "Remove ${entry.title} from saved" else "Save ${entry.title}",
                )
            }
        }
    }
}
