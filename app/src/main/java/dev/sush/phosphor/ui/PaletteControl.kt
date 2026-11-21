package dev.sush.phosphor.ui

import android.graphics.Color as Rgb
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.sush.phosphor.model.ScenePalette
import dev.sush.phosphor.model.WallpaperKind
import dev.sush.phosphor.model.WallpaperPreset
import dev.sush.phosphor.model.WallpaperSettings
import kotlin.math.roundToInt

private val sceneRoles = listOf("Background", "First accent", "Second accent")
private val photoRoles = listOf("Shadows", "Highlights", "Floating lights")
private val rgbHex = Regex("[0-9a-fA-F]{6}")

private fun hexColor(color: Int) = "#%06X".format(java.util.Locale.ROOT, color and 0xFFFFFF)

@Composable
internal fun PaletteControl(
    preset: WallpaperPreset, settings: WallpaperSettings, onChange: (WallpaperSettings) -> Unit,
) {
    val colors = settings.resolvedColors(preset)
    val photo = preset.kind == WallpaperKind.IMAGE
    val roles = if (photo) photoRoles else sceneRoles
    var editing by rememberSaveable(preset.id) { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Color palette", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScenePalette.entries.forEach { palette ->
                FilterChip(
                    selected = settings.palette == palette,
                    onClick = { onChange(settings.copy(palette = palette)) },
                    label = { Text(palette.label) },
                )
            }
        }
        if (photo && settings.palette == ScenePalette.ORIGINAL) {
            Text("Choose a color to tint your photo.", style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            roles.forEachIndexed { index, role ->
                OutlinedButton(
                    onClick = { editing = index },
                    modifier = Modifier.semantics { stateDescription = hexColor(colors[index]) },
                ) {
                    Swatches(listOf(colors[index]), 18.dp)
                    Text(role, Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    editing?.let { index ->
        ColorDialog(roles[index], colors[index], onDismiss = { editing = null }) { color ->
            val custom = colors.toMutableList().apply { set(index, color) }
            onChange(settings.copy(palette = ScenePalette.CUSTOM, customColors = custom))
            editing = null
        }
    }
}

@Composable
private fun ColorDialog(role: String, initial: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    var color by rememberSaveable { mutableIntStateOf(initial) }
    var hex by rememberSaveable { mutableStateOf(hexColor(initial)) }
    val digits = hex.trim().removePrefix("#")
    val valid = rgbHex.matches(digits)
    val red = Rgb.red(color)
    val green = Rgb.green(color)
    val blue = Rgb.blue(color)
    fun fromSlider(value: Int) {
        color = value
        hex = hexColor(value)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(role) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hex,
                    onValueChange = { value ->
                        hex = value
                        val rgb = value.trim().removePrefix("#")
                        if (rgbHex.matches(rgb)) color = Rgb.BLACK or rgb.toInt(16)
                    },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Hex color") },
                    trailingIcon = { Swatches(listOf(color), 32.dp) },
                    isError = !valid,
                    supportingText = if (valid) null else ({ Text("Enter six hex digits, like #3478AC.") }),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                )
                ColorChannel("Red", red) { fromSlider(Rgb.rgb(it, green, blue)) }
                ColorChannel("Green", green) { fromSlider(Rgb.rgb(red, it, blue)) }
                ColorChannel("Blue", blue) { fromSlider(Rgb.rgb(red, green, it)) }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(color) }, enabled = valid) { Text("Use color") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ColorChannel(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value.toString(), style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value.toFloat(), onValueChange = { onChange(it.roundToInt()) }, valueRange = 0f..255f,
            modifier = Modifier.semantics { contentDescription = label; stateDescription = value.toString() },
        )
    }
}
