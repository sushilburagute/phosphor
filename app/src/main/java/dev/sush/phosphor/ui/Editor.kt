package dev.sush.phosphor.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sush.phosphor.R
import dev.sush.phosphor.model.Appearance
import dev.sush.phosphor.model.WallpaperPreset
import dev.sush.phosphor.model.WallpaperSettings
import dev.sush.phosphor.platform.isNight
import dev.sush.phosphor.ui.theme.Motion

@Composable
fun Editor(
    preset: WallpaperPreset,
    settings: WallpaperSettings,
    favorite: Boolean,
    uncertainApply: Boolean,
    systemNight: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onChange: (WallpaperSettings) -> Unit,
    onApply: () -> Unit,
    onConfirm: () -> Unit,
    onExport: () -> Unit,
    onPaletteHelp: () -> Unit,
) {
    var homeScreen by rememberSaveable { mutableStateOf(false) }
    var replay by rememberSaveable { mutableIntStateOf(0) }
    val night = settings.appearance.isNight(systemNight)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Glyph(R.drawable.ic_back, "Back to wallpapers") }
            Text(preset.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() })
            IconButton(onClick = onFavorite) {
                Glyph(if (favorite) R.drawable.ic_saved_filled else R.drawable.ic_saved, if (favorite) "Remove from saved" else "Save wallpaper",
                    tint = if (favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface)
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide = maxWidth >= 840.dp && LocalDensity.current.fontScale < 1.4f
            // The plate is as tall as the window allows, leaving room for the controls row.
            val plateHeight = if (wide) (maxHeight - 96.dp).coerceAtLeast(120.dp) else minOf(560.dp, maxHeight * 0.74f)
            val stage: @Composable (Modifier) -> Unit = { modifier ->
                Stage(preset, settings, night, homeScreen, replay, plateHeight, modifier,
                    onHomeScreen = { homeScreen = it }, onReplay = { replay++ })
            }
            val controls: @Composable (Modifier, Boolean) -> Unit = { modifier, scroll ->
                Controls(preset, settings, night, uncertainApply, onChange, onConfirm, onExport, onPaletteHelp, modifier, scroll)
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    stage(Modifier.weight(0.9f).fillMaxHeight().padding(vertical = 12.dp))
                    controls(Modifier.weight(1.1f).fillMaxHeight(), true)
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    stage(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                    controls(Modifier.fillMaxWidth(), false)
                }
            }
        }
        Surface(tonalElevation = 2.dp, shadowElevation = 6.dp) {
            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Glyph(R.drawable.ic_check, null, Modifier.size(20.dp))
                Text("Set live wallpaper", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

/** The live, touchable preview, with optional home-screen overlay and a wake replay. */
@Composable
private fun Stage(
    preset: WallpaperPreset, settings: WallpaperSettings, night: Boolean, homeScreen: Boolean, replay: Int,
    plateHeight: androidx.compose.ui.unit.Dp, modifier: Modifier, onHomeScreen: (Boolean) -> Unit, onReplay: () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.height(plateHeight).aspectRatio(PLATE_RATIO, matchHeightConstraintsFirst = true)
                .clip(MaterialTheme.shapes.extraLarge),
        ) {
            WallpaperPreview(preset, settings, Modifier.fillMaxSize(), animate = true, night = night, replayReveal = replay)
            androidx.compose.animation.AnimatedVisibility(visible = homeScreen, enter = fadeIn(tween(if (settings.reducedMotion) 0 else Motion.CALM)), exit = fadeOut(tween(if (settings.reducedMotion) 0 else Motion.QUICK))) {
                HomeScreenOverlay(night)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconToggleButton(
                checked = homeScreen, onCheckedChange = onHomeScreen,
                modifier = Modifier.semantics { contentDescription = if (homeScreen) "Hide home screen preview" else "Show home screen preview" },
            ) { Glyph(R.drawable.ic_home_screen, null) }
            FilledTonalIconToggleButton(
                checked = false, onCheckedChange = { onReplay() }, enabled = settings.wakeReveal && !settings.reducedMotion,
                modifier = Modifier.semantics { contentDescription = "Replay the wake animation" },
            ) { Glyph(R.drawable.ic_wake, null) }
            if (settings.parallax && !settings.reducedMotion) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                    Glyph(R.drawable.ic_touch, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(preset.interaction, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp).widthIn(max = 220.dp))
                }
            }
        }
    }
}

@Composable
private fun Controls(
    preset: WallpaperPreset, settings: WallpaperSettings, night: Boolean, uncertainApply: Boolean,
    onChange: (WallpaperSettings) -> Unit, onConfirm: () -> Unit, onExport: () -> Unit, onPaletteHelp: () -> Unit,
    modifier: Modifier, scroll: Boolean,
) {
    Column(
        (if (scroll) modifier.verticalScroll(rememberScrollState()) else modifier).padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        if (uncertainApply) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Did you choose Phosphor in the picker? Save these settings to finish. If not, your current wallpaper stays as it is.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onConfirm, contentPadding = PaddingValues(0.dp)) { Text("Save wallpaper settings") }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(preset.description, style = MaterialTheme.typography.bodyLarge)
            FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatches(preset.colors, 18.dp)
                Swatches(preset.dayColors, 18.dp)
                TextButton(onClick = onPaletteHelp) { Text("Match your phone’s colors") }
            }
        }

        Section("Day and night", if (night) R.drawable.ic_moon else R.drawable.ic_sun) {
            Segmented(
                listOf(
                    Triple(Appearance.AUTO, "Auto", R.drawable.ic_auto),
                    Triple(Appearance.DAY, "Day", R.drawable.ic_sun),
                    Triple(Appearance.NIGHT, "Night", R.drawable.ic_moon),
                ),
                settings.appearance, { onChange(settings.copy(appearance = it)) },
            )
            Text(
                when (settings.appearance) {
                    Appearance.AUTO -> "Follows your phone’s dark theme. Glows at night, prints in ink by day."
                    Appearance.DAY -> "Always ink on paper. Your launcher switches to dark labels."
                    Appearance.NIGHT -> "Always emitted light. Your launcher keeps light labels."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SliderRow("Calm the artwork", settings.darken, "Fades toward the background so icons and widgets stand out more.") { onChange(settings.copy(darken = it)) }
        }

        Section("Motion", R.drawable.ic_motion) {
            SliderRow("Speed", settings.speed, enabled = !settings.reducedMotion) { onChange(settings.copy(speed = it)) }
            SliderRow("Intensity", settings.intensity, "How far the artwork moves and how much detail it shows.") { onChange(settings.copy(intensity = it)) }
            ToggleRow("Respond to touch", "Taps, drags and home-screen swipes move the artwork.", settings.parallax, { onChange(settings.copy(parallax = it)) }, !settings.reducedMotion)
            ToggleRow("Still mode", "Keep the artwork, stop all animation.", settings.reducedMotion, { onChange(settings.copy(reducedMotion = it)) })
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Frame rate", style = MaterialTheme.typography.titleMedium)
                Segmented(listOf(15, 30, 60).map { Triple(it, "$it fps", null) }, settings.fps, { onChange(settings.copy(fps = it)) }, enabled = !settings.reducedMotion)
                Text("30 fps balances smoothness and battery. The lock screen uses up to 15 fps. Animation pauses when hidden or the screen is off, and touches follow the same battery limits.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Section("Battery and wake", R.drawable.ic_battery) {
            ToggleRow("Light up on wake", "The artwork rises from dark when you turn on the screen, and blooms when you unlock.", settings.wakeReveal, { onChange(settings.copy(wakeReveal = it)) }, !settings.reducedMotion)
            ToggleRow("Lock and unlock animation", "Gently settle on lock and reveal on unlock. Availability depends on your phone.", settings.unlockAnimation, { onChange(settings.copy(unlockAnimation = it)) }, !settings.reducedMotion)
            ToggleRow("Save battery when low", "Below 20% and unplugged, motion slows to 15 fps. At 5%, it holds still. Battery Saver is always respected.", settings.lowBatteryMode, { onChange(settings.copy(lowBatteryMode = it)) })
        }

        OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Glyph(R.drawable.ic_export, null, Modifier.size(20.dp))
            Text("Save a still image", modifier = Modifier.padding(start = 10.dp))
        }
        Text("made by sush.dev", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(4.dp))
    }
}
