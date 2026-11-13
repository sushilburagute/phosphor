package dev.sush.phosphor.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sush.phosphor.R
import dev.sush.phosphor.model.WallpaperKind
import dev.sush.phosphor.model.WallpaperPreset
import dev.sush.phosphor.model.WallpaperSettings
import dev.sush.phosphor.ui.theme.Motion

val Collections = listOf("All", "Orbit", "Aurora", "ASCII", "Gradients", "Glass", "Abstract", "Textures")

fun WallpaperKind.label(): String = when (this) {
    WallpaperKind.ORBIT -> "Orbit"
    WallpaperKind.AURORA -> "Aurora"
    WallpaperKind.ASCII -> "ASCII"
    WallpaperKind.GRADIENT -> "Gradients"
    WallpaperKind.GLASS -> "Glass"
    WallpaperKind.ABSTRACT -> "Abstract"
    WallpaperKind.TEXTURE -> "Textures"
    WallpaperKind.IMAGE -> "Your image"
}

/** Plates round like a phone's own corners: a fixed share of their width. */
val PlateShape = RoundedCornerShape(percent = 11)

/**
 * The home screen of the app: a quiet large title, one row of category pills and the
 * wallpapers themselves, shown at this phone's real proportions. Nothing else competes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Gallery(
    category: String,
    wallpapers: List<WallpaperPreset>,
    active: WallpaperSettings,
    imagePath: String?,
    liveId: String?,
    night: Boolean,
    onNight: (Boolean) -> Unit,
    onCategory: (String) -> Unit,
    onSelect: (WallpaperPreset) -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scheme = MaterialTheme.colorScheme
    val ratio = rememberScreenRatio()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = scheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text("Phosphor") },
                actions = {
                    IconButton(onClick = { onNight(!night) }, modifier = Modifier.semantics {
                        contentDescription = if (night) "Showing night versions. Show day versions" else "Showing day versions. Show night versions"
                    }) {
                        Crossfade(night, animationSpec = tween(if (active.reducedMotion) 0 else Motion.MOOD), label = "mood") { lit ->
                            Glyph(if (lit) R.drawable.ic_moon else R.drawable.ic_sun, null)
                        }
                    }
                    IconButton(onClick = onSettings) { Glyph(R.drawable.ic_settings, "Settings") }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = scheme.background, scrolledContainerColor = scheme.surfaceContainerLow),
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val size = WindowWidth.of(maxWidth)
            // Larger text needs wider plates, so drop a column rather than squeeze the names.
            val columns = if (LocalDensity.current.fontScale >= 1.5f) (size.columns - 1).coerceAtLeast(2) else size.columns
            val gutter = size.gutter
            LazyVerticalGrid(
                modifier = Modifier.widthIn(max = 1400.dp).fillMaxSize(),
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(start = gutter, end = gutter, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 40.dp),
                horizontalArrangement = Arrangement.spacedBy(if (size == WindowWidth.Compact) 12.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(
                            "Live wallpapers drawn on your phone. They glow at night and turn to color by day.",
                            style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, end = 24.dp).widthIn(max = 520.dp),
                        )
                        CategoryPills(category, onCategory)
                    }
                }
                items(wallpapers, key = { it.id }) { preset ->
                    Plate(preset, active, imagePath, ratio, night, live = liveId == preset.id, onSelect)
                }
                if (category == "All") item(key = "import") { ImportPlate(ratio, onImport) }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("made by sush.dev", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun CategoryPills(selected: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Collections) { label ->
            val on = selected == label
            Box(
                Modifier.height(40.dp).clip(CircleShape)
                    .background(if (on) scheme.onSurface else scheme.surfaceContainer)
                    .selectable(on, role = Role.Tab) { onSelect(label) }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) scheme.surface else scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Plate(
    preset: WallpaperPreset, active: WallpaperSettings, imagePath: String?, ratio: Float, night: Boolean, live: Boolean,
    onSelect: (WallpaperPreset) -> Unit,
) {
    Column {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio).clip(PlateShape)
                .clickable(role = Role.Button, onClickLabel = "Open ${preset.name}") { onSelect(preset) },
        ) {
            Crossfade(night, animationSpec = tween(if (active.reducedMotion) 0 else Motion.MOOD), label = "plate") { lit ->
                WallpaperPreview(
                    preset, active.copy(presetId = preset.id, imagePath = if (preset.kind == WallpaperKind.IMAGE) imagePath else null),
                    Modifier.fillMaxSize(), night = lit,
                )
            }
        }
        Row(Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(preset.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (live) {
                LiveDot(Modifier.padding(start = 8.dp))
                Text("Live", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 5.dp))
            }
        }
    }
}

/** The last plate invites your own image, in the same shape as the rest. */
@Composable
private fun ImportPlate(ratio: Float, onImport: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio).clip(PlateShape).background(scheme.surfaceContainer)
                .clickable(role = Role.Button, onClickLabel = "Choose an image") { onImport() },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
                Glyph(R.drawable.ic_import, null, tint = scheme.onPrimaryContainer)
            }
        }
        Text("Your image", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
    }
}
