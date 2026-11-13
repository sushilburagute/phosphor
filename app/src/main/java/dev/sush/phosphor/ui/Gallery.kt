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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sush.phosphor.R
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

@Composable
fun Gallery(
    saved: Boolean,
    category: String,
    wallpapers: List<WallpaperPreset>,
    favorites: Set<String>,
    active: WallpaperSettings,
    imagePath: String?,
    liveId: String?,
    night: Boolean,
    onNight: (Boolean) -> Unit,
    onCategory: (String) -> Unit,
    onSelect: (WallpaperPreset) -> Unit,
    onFavorite: (String) -> Unit,
    onImport: () -> Unit,
    onExplore: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (saved) "Saved" else "Phosphor", style = MaterialTheme.typography.displayMedium, modifier = Modifier.weight(1f))
                    MoodSwitch(night, onNight)
                }
                Text(
                    if (saved) "The wallpapers you’ve kept. They follow the same day and night switch."
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
                        FilterChip(selected = category == label, onClick = { onCategory(label) }, label = { Text(label) })
                    }
                }
                if (!saved) OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Glyph(R.drawable.ic_import, null, Modifier.size(20.dp))
                    Text("Bring in your own image", modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
        if (wallpapers.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Glyph(if (saved) R.drawable.ic_saved else R.drawable.ic_import, null, Modifier.size(32.dp), MaterialTheme.colorScheme.primary)
                Text(if (saved) "Nothing saved yet" else "Start with your own image", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (saved) "Tap the heart on any wallpaper to keep it here." else "Choose a photo or artwork and Phosphor adds gentle motion and light.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = if (saved) onExplore else onImport) { Text(if (saved) "Browse wallpapers" else "Choose an image") }
            }
        }
        items(wallpapers, key = { it.id }) { preset ->
            val isActive = liveId == preset.id
            Column {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(PLATE_RATIO).clip(MaterialTheme.shapes.extraLarge)
                        .clickable(onClickLabel = "Open ${preset.name}") { onSelect(preset) },
                ) {
                    Crossfade(night, animationSpec = tween(Motion.MOOD), label = "plate") { lit ->
                        WallpaperPreview(
                            preset, active.copy(presetId = preset.id, imagePath = if (preset.kind == WallpaperKind.IMAGE) imagePath else null),
                            Modifier.fillMaxSize(), night = lit,
                        )
                    }
                    Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), shape = CircleShape, color = Color.Black.copy(alpha = .32f)) {
                        val on = preset.id in favorites
                        IconButton(onClick = { onFavorite(preset.id) }, modifier = Modifier.semantics { contentDescription = if (on) "Remove ${preset.name} from saved" else "Save ${preset.name}" }) {
                            Glyph(if (on) R.drawable.ic_saved_filled else R.drawable.ic_saved, null, tint = Color.White)
                        }
                    }
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(preset.name, style = MaterialTheme.typography.titleMedium)
                        Text(if (isActive) "On your phone" else preset.kind.label(), style = MaterialTheme.typography.bodySmall,
                            color = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Swatches(preset.palette(night), 14.dp)
                }
                // Ember horizon: the one place the accent appears, marking the live wallpaper.
                if (isActive) Box(Modifier.padding(top = 6.dp).width(28.dp).height(2.dp).clip(CircleShape).background(Palette.Ember))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("made by sush.dev", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
