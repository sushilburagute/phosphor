package dev.sush.phosphor.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sush.phosphor.R
import dev.sush.phosphor.model.Appearance
import dev.sush.phosphor.model.WallpaperPreset
import dev.sush.phosphor.model.WallpaperSettings
import dev.sush.phosphor.platform.isNight
import dev.sush.phosphor.ui.theme.Motion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Editor(
    preset: WallpaperPreset,
    settings: WallpaperSettings,
    uncertainApply: Boolean,
    systemNight: Boolean,
    onBack: () -> Unit,
    onChange: (WallpaperSettings) -> Unit,
    onApply: () -> Unit,
    onConfirm: () -> Unit,
    onExport: () -> Unit,
) {
    var homeScreen by rememberSaveable { mutableStateOf(false) }
    var replay by rememberSaveable { mutableIntStateOf(0) }
    val night = settings.appearance.isNight(systemNight)
    val ratio = rememberScreenRatio()
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(preset.name) },
                navigationIcon = { IconButton(onClick = onBack) { Glyph(R.drawable.ic_back, "Back to wallpapers") } },
                actions = { IconButton(onClick = onExport) { Glyph(R.drawable.ic_export, "Save a still image") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.background),
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            val size = WindowWidth.of(maxWidth)
            val wide = maxWidth >= 840.dp && LocalDensity.current.fontScale < 1.4f
            val applyBar = 96.dp
            val height = maxHeight
            val stage: @Composable (Modifier, Dp) -> Unit = { modifier, plateHeight ->
                Stage(preset, settings, night, homeScreen, replay, ratio, plateHeight, modifier,
                    onHomeScreen = { homeScreen = it }, onReplay = { replay++ })
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = size.gutter), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    stage(Modifier.weight(0.9f).fillMaxHeight().padding(vertical = 16.dp), height - 120.dp)
                    Column(Modifier.weight(1.1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = applyBar)) {
                        Controls(preset, settings, uncertainApply, onChange, onConfirm)
                    }
                }
            } else {
                // The plate takes most of the first screen; controls follow on scroll.
                val plateHeight = minOf(maxHeight * 0.68f, (maxWidth - size.gutter * 4) / ratio)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = size.gutter).padding(bottom = applyBar)) {
                    stage(Modifier.fillMaxWidth().padding(top = 8.dp), plateHeight)
                    Controls(preset, settings, uncertainApply, onChange, onConfirm)
                }
            }
            ApplyBar(onApply, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** A floating pill over a short fade, so the action never covers content abruptly. */
@Composable
private fun ApplyBar(onApply: () -> Unit, modifier: Modifier) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to background.copy(alpha = 0f), 0.35f to background.copy(alpha = 0.92f), 1f to background))
            .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = onApply, shape = CircleShape,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text("Set wallpaper", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The live, touchable preview, with optional home-screen overlay and a wake replay. */
@Composable
private fun Stage(
    preset: WallpaperPreset, settings: WallpaperSettings, night: Boolean, homeScreen: Boolean, replay: Int,
    ratio: Float, plateHeight: Dp, modifier: Modifier, onHomeScreen: (Boolean) -> Unit, onReplay: () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.height(plateHeight).aspectRatio(ratio, matchHeightConstraintsFirst = true).clip(PlateShape)) {
            WallpaperPreview(preset, settings, Modifier.fillMaxSize(), animate = true, night = night, replayReveal = replay)
            androidx.compose.animation.AnimatedVisibility(
                visible = homeScreen,
                enter = fadeIn(tween(if (settings.reducedMotion) 0 else Motion.CALM)),
                exit = fadeOut(tween(if (settings.reducedMotion) 0 else Motion.QUICK)),
            ) { HomeScreenOverlay(night) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconToggleButton(
                checked = homeScreen, onCheckedChange = onHomeScreen,
                modifier = Modifier.semantics { contentDescription = if (homeScreen) "Hide home screen preview" else "Show home screen preview" },
            ) { Glyph(R.drawable.ic_home_screen, null, Modifier.size(20.dp)) }
            FilledTonalIconToggleButton(
                checked = false, onCheckedChange = { onReplay() }, enabled = settings.wakeReveal && !settings.reducedMotion,
                modifier = Modifier.semantics { contentDescription = "Replay the wake animation" },
            ) { Glyph(R.drawable.ic_wake, null, Modifier.size(20.dp)) }
        }
        if (settings.parallax && !settings.reducedMotion) {
            Text(preset.interaction, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        }
    }
}

@Composable
private fun Controls(
    preset: WallpaperPreset, settings: WallpaperSettings, uncertainApply: Boolean,
    onChange: (WallpaperSettings) -> Unit, onConfirm: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(top = 28.dp)) {
        if (uncertainApply) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.padding(bottom = 28.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Did you choose Phosphor in the picker? Save these settings to finish. If not, your current wallpaper stays as it is.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onConfirm, contentPadding = PaddingValues(0.dp)) { Text("Save wallpaper settings") }
                }
            }
        }
        Text(preset.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        Spacer(Modifier.height(32.dp))

        SectionLabel("Look")
        Group {
            item {
                ChoiceItem(
                    "Day and night",
                    when (settings.appearance) {
                        Appearance.AUTO -> "Follows your phone’s dark theme."
                        Appearance.DAY -> "Always light. Your launcher switches to dark labels."
                        Appearance.NIGHT -> "Always dark. Your launcher keeps light labels."
                    },
                ) {
                    Segmented(
                        listOf(Triple(Appearance.AUTO, "Auto", null), Triple(Appearance.DAY, "Day", null), Triple(Appearance.NIGHT, "Night", null)),
                        settings.appearance, { onChange(settings.copy(appearance = it)) },
                    )
                }
            }
            item { SliderItem("Calm", settings.darken, "Softens the artwork so icons and widgets stand out.") { onChange(settings.copy(darken = it)) } }
        }
        Spacer(Modifier.height(28.dp))

        SectionLabel("Motion")
        Group {
            item { SliderItem("Speed", settings.speed, enabled = !settings.reducedMotion) { onChange(settings.copy(speed = it)) } }
            item { SliderItem("Intensity", settings.intensity, "How far the artwork moves and how much detail it shows.") { onChange(settings.copy(intensity = it)) } }
            item { ToggleItem("Respond to touch", "Taps, drags and home-screen swipes move the artwork.", settings.parallax, { onChange(settings.copy(parallax = it)) }, !settings.reducedMotion) }
            item { ToggleItem("Still", "Keep the artwork, stop all animation.", settings.reducedMotion, { onChange(settings.copy(reducedMotion = it)) }) }
            item {
                ChoiceItem("Frame rate", "30 fps balances smoothness and battery.") {
                    Segmented(listOf(15, 30, 60).map { Triple(it, "$it", null) }, settings.fps, { onChange(settings.copy(fps = it)) }, enabled = !settings.reducedMotion)
                }
            }
        }
        Spacer(Modifier.height(28.dp))

        SectionLabel("Battery and wake")
        Group {
            item { ToggleItem("Light up on wake", "Rises from dark when the screen turns on, and blooms on unlock.", settings.wakeReveal, { onChange(settings.copy(wakeReveal = it)) }, !settings.reducedMotion) }
            item { ToggleItem("Lock and unlock", "Settles on lock and reveals on unlock, where your phone allows.", settings.unlockAnimation, { onChange(settings.copy(unlockAnimation = it)) }, !settings.reducedMotion) }
            item { ToggleItem("Save battery when low", "Below 20% motion slows to 15 fps; at 5% it holds still. Battery Saver is always respected.", settings.lowBatteryMode, { onChange(settings.copy(lowBatteryMode = it)) }) }
        }
        Spacer(Modifier.height(32.dp))
        Text("made by sush.dev", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
    }
}
