package dev.sush.phosphor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sush.phosphor.R
import dev.sush.phosphor.model.WallpaperKind
import dev.sush.phosphor.model.WallpaperPreset
import dev.sush.phosphor.model.WallpaperSettings
import dev.sush.phosphor.ui.theme.Motion

@Composable
fun Editor(
    preset: WallpaperPreset,
    settings: WallpaperSettings,
    uncertainApply: Boolean,
    title: String,
    onBack: () -> Unit,
    onSaveLook: () -> Unit,
    onChange: (WallpaperSettings) -> Unit,
    onApply: () -> Unit,
    onConfirm: () -> Unit,
    onExport: () -> Unit,
    onPaletteHelp: () -> Unit,
) {
    var homeScreen by rememberSaveable { mutableStateOf(false) }
    var replay by rememberSaveable { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    fun back() { if (expanded) expanded = false else onBack() }
    BackHandler(expanded) { expanded = false }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::back) { Glyph(R.drawable.ic_back, if (expanded) "Back to tuning" else "Back to wallpapers") }
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() })
            TextButton(onClick = onSaveLook) { Text("Save look") }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide = maxWidth >= 840.dp || (maxWidth >= 540.dp && maxWidth > maxHeight * 1.4f)
            val plateHeight = when {
                wide -> (maxHeight - 86.dp).coerceAtLeast(1.dp)
                expanded -> (maxHeight - 66.dp).coerceAtLeast(1.dp)
                else -> minOf(260.dp, maxHeight * if (LocalDensity.current.fontScale > 1.4f) 0.26f else 0.36f)
            }
            val stage: @Composable (Modifier) -> Unit = { modifier ->
                Stage(preset, settings, homeScreen, replay, plateHeight, modifier,
                    onHomeScreen = { homeScreen = it }, onReplay = { replay++ },
                    expanded = expanded, onExpand = if (wide) null else ({ expanded = !expanded }))
            }
            val controls: @Composable (Modifier) -> Unit = { modifier ->
                Controls(preset, settings, uncertainApply, onChange,
                    onConfirm, onExport, onPaletteHelp, modifier)
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    stage(Modifier.weight(0.9f).fillMaxHeight().padding(vertical = 12.dp))
                    controls(Modifier.weight(1.1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    stage(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                    if (!expanded) controls(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
        Surface(tonalElevation = 2.dp, shadowElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Button(
                    onClick = onApply, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Glyph(R.drawable.ic_check, null, Modifier.size(20.dp))
                    Text("Set live wallpaper", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

/** The preview stays visible while the settings below it scroll. */
@Composable
private fun Stage(
    preset: WallpaperPreset, settings: WallpaperSettings, homeScreen: Boolean, replay: Int,
    plateHeight: Dp, modifier: Modifier, onHomeScreen: (Boolean) -> Unit, onReplay: () -> Unit,
    expanded: Boolean, onExpand: (() -> Unit)?,
) {
    val sizeAction = if (expanded) "Back to tuning" else "Expand preview"
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier.height(plateHeight).aspectRatio(PLATE_RATIO, matchHeightConstraintsFirst = true)
                .clip(MaterialTheme.shapes.large).then(
                    if (onExpand == null) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = sizeAction, onClick = onExpand)
                ),
        ) {
            WallpaperPreview(preset, settings, Modifier.fillMaxSize(), animate = true, replayReveal = replay)
            androidx.compose.animation.AnimatedVisibility(visible = homeScreen, enter = fadeIn(tween(if (settings.reducedMotion) 0 else Motion.CALM)), exit = fadeOut(tween(if (settings.reducedMotion) 0 else Motion.QUICK))) {
                HomeScreenOverlay()
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconToggleButton(
                checked = homeScreen, onCheckedChange = onHomeScreen,
                modifier = Modifier.semantics { contentDescription = if (homeScreen) "Hide home screen preview" else "Show home screen preview" },
            ) { Glyph(R.drawable.ic_home_screen, null) }
            IconButton(
                onClick = onReplay, enabled = settings.wakeReveal && !settings.reducedMotion,
                modifier = Modifier.semantics { contentDescription = "Replay the wake animation" },
            ) { Glyph(R.drawable.ic_wake, null) }
            if (onExpand != null) TextButton(onClick = onExpand) { Text(sizeAction) }
        }
    }
}

@Composable
private fun Controls(
    preset: WallpaperPreset, settings: WallpaperSettings, uncertainApply: Boolean,
    onChange: (WallpaperSettings) -> Unit, onConfirm: () -> Unit,
    onExport: () -> Unit, onPaletteHelp: () -> Unit, modifier: Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 28.dp),
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
                TextButton(onClick = onPaletteHelp) { Text("Match your phone’s colors") }
            }
        }

        if (preset.kind == WallpaperKind.IMAGE) {
            PhotoFraming(settings, onChange)
        }

        Section("Wallpaper appearance", R.drawable.ic_moon) {
            SliderRow("Calm the artwork", settings.darken, "Darkens the wallpaper so icons and widgets stand out.") { onChange(settings.copy(darken = it)) }
        }

        Section("Motion", R.drawable.ic_motion) {
            SliderRow("Speed", settings.speed, enabled = !settings.reducedMotion) { onChange(settings.copy(speed = it)) }
            SliderRow("Intensity", settings.intensity, "How far the artwork moves and how much detail it shows.") { onChange(settings.copy(intensity = it)) }
            ToggleRow("Home-screen parallax", "Layers shift at different speeds as your launcher moves between pages.", settings.parallax, { onChange(settings.copy(parallax = it)) }, !settings.reducedMotion)
            ToggleRow("Still mode", "Keep the artwork, stop all animation.", settings.reducedMotion, { onChange(settings.copy(reducedMotion = it)) })
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Frame rate", style = MaterialTheme.typography.titleMedium)
                Segmented(listOf(15, 30, 60).map { Triple(it, "$it fps", null) }, settings.fps, { onChange(settings.copy(fps = it)) }, enabled = !settings.reducedMotion)
                Text("The lock screen uses up to 15 fps. Animation pauses when the wallpaper is hidden or the screen is off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Section("Battery and wake", R.drawable.ic_battery) {
            ToggleRow("Light up on wake", "Gradually reveal the scene when you turn on the screen.", settings.wakeReveal, { onChange(settings.copy(wakeReveal = it)) }, !settings.reducedMotion)
            ToggleRow("Lock and unlock animation", "Gently settle on lock and reveal on unlock. Availability depends on your phone.", settings.unlockAnimation, { onChange(settings.copy(unlockAnimation = it)) }, !settings.reducedMotion)
            ToggleRow("Save battery when low", "Below 20% and unplugged, motion slows to 15 fps. At 5%, it holds still. Battery Saver is always respected.", settings.lowBatteryMode, { onChange(settings.copy(lowBatteryMode = it)) })
        }

        OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Glyph(R.drawable.ic_export, null, Modifier.size(20.dp))
            Text("Save a still image", modifier = Modifier.padding(start = 10.dp))
        }
        AppAttribution(Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(4.dp))
    }
}
