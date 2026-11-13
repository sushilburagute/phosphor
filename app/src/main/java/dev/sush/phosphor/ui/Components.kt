package dev.sush.phosphor.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sush.phosphor.R
import dev.sush.phosphor.ui.theme.Motion
import dev.sush.phosphor.ui.theme.Palette
import dev.sush.phosphor.ui.theme.PhosphorType

@Composable
fun Glyph(@DrawableRes id: Int, description: String?, modifier: Modifier = Modifier, tint: Color = androidx.compose.material3.LocalContentColor.current) {
    Icon(painterResource(id), contentDescription = description, modifier = modifier.size(22.dp), tint = tint)
}

/**
 * The app's one bold control: switch every wallpaper between its night (light) and day (ink)
 * treatment. A sun and a moon sit either side of a horizon; the lit side fills.
 */
@Composable
fun MoodSwitch(night: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val knob by animateDpAsState(if (night) 52.dp else 4.dp, tween(Motion.MOOD, easing = Motion.Shift), label = "mood")
    Box(
        modifier
            .size(width = 104.dp, height = 52.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, scheme.outlineVariant, CircleShape)
            .selectableGroup(),
    ) {
        Box(
            Modifier.offset { androidx.compose.ui.unit.IntOffset(knob.roundToPx(), 4.dp.roundToPx()) }.size(48.dp, 44.dp).clip(CircleShape)
                .background(if (night) Palette.Sky else Palette.Paper)
                .border(1.dp, if (night) Palette.SkyLine else Palette.PaperLine, CircleShape),
        )
        // The horizon: a short ember line under whichever side is lit.
        Box(
            Modifier.offset { androidx.compose.ui.unit.IntOffset((knob + 14.dp).roundToPx(), 37.dp.roundToPx()) }.size(20.dp, 2.dp).clip(CircleShape).background(Palette.Ember),
        )
        Row(Modifier.fillMaxSize()) {
            MoodHalf(R.drawable.ic_sun, "Day: ink on paper", !night, if (!night) Palette.Ink else scheme.onSurfaceVariant) { onChange(false) }
            MoodHalf(R.drawable.ic_moon, "Night: emitted light", night, if (night) Palette.Starlight else scheme.onSurfaceVariant) { onChange(true) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MoodHalf(@DrawableRes icon: Int, label: String, selected: Boolean, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).fillMaxHeight()
            .selectable(selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, null, tint = animateColorAsState(tint, tween(Motion.MOOD), label = "tint").value) }
}

/** A labelled group of related settings, separated by space rather than boxes. */
@Composable
fun Section(title: String, @DrawableRes icon: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Glyph(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        }
        content()
    }
}

@Composable
fun SliderRow(label: String, value: Float, description: String? = null, enabled: Boolean = true, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
            Text(
                "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        Slider(
            value = value, onValueChange = onChange, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label; stateDescription = "${(value * 100).toInt()} percent" },
        )
    }
}

/** Label, explanation and switch form one accessible, 48dp-plus toggle. */
@Composable
fun ToggleRow(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.small)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A connected row of options, used for appearance and frame rate. */
@Composable
fun <T> Segmented(options: List<Triple<T, String, Int?>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(14.dp)).selectableGroup(),
    ) {
        options.forEachIndexed { index, (value, label, icon) ->
            val on = value == selected
            val background by animateColorAsState(if (on) scheme.primaryContainer else Color.Transparent, tween(Motion.QUICK), label = "seg")
            Row(
                Modifier.weight(1f).fillMaxHeight().background(background)
                    .selectable(on, enabled = enabled, role = Role.RadioButton) { onSelect(value) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = when { !enabled -> scheme.onSurface.copy(alpha = .38f); on -> scheme.onPrimaryContainer; else -> scheme.onSurfaceVariant }
                if (icon != null) { Glyph(icon, null, Modifier.size(18.dp), tint); Spacer(Modifier.width(6.dp)) }
                Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
            }
            if (index < options.lastIndex) Box(Modifier.width(1.dp).fillMaxHeight().background(scheme.outlineVariant))
        }
    }
}

/**
 * A stand-in home screen over a preview: clock, an icon grid with labels and a dock, in the
 * label color the launcher will choose for this treatment. It shows readability honestly.
 */
@Composable
fun HomeScreenOverlay(night: Boolean, modifier: Modifier = Modifier) {
    val ink = if (night) Color.White else Color(0xFF1B1B1F)
    val tile = if (night) Color(0x33FFFFFF) else Color(0x2E1B1B1F)
    val names = listOf("Camera", "Maps", "Notes", "Music", "Photos", "Mail", "Clock", "Files", "Weather", "Calendar", "News", "Fitness")
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Scale with the plate so the proportions match a 360dp-wide phone.
        val scale = maxWidth.value / 360f
        val tall = maxHeight
        fun d(v: Float) = (v * scale).dp
        fun t(v: Float) = (v * scale).sp
        Column(Modifier.fillMaxSize().padding(horizontal = d(22f))) {
            Spacer(Modifier.height(tall * 0.075f))
            Text("12:45", color = ink, fontFamily = PhosphorType.Display, fontSize = t(64f), lineHeight = t(64f))
            Text("Thursday, 13 November", color = ink, fontFamily = PhosphorType.Text, fontSize = t(14f))
            Spacer(Modifier.height(tall * 0.14f))
            for (row in 0 until 3) {
                Row(Modifier.fillMaxWidth().padding(bottom = d(18f)), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (col in 0 until 4) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(d(70f))) {
                            Box(Modifier.size(d(52f)).clip(RoundedCornerShape(d(16f))).background(tile))
                            Text(names[row * 4 + col], color = ink, fontFamily = PhosphorType.Text, fontSize = t(11.5f),
                                maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = d(5f)))
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(bottom = tall * 0.075f), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(4) { Box(Modifier.size(d(52f)).clip(RoundedCornerShape(d(16f))).background(tile)) }
            }
        }
    }
}

/** Palette dots; the ground swatch is outlined so pale grounds stay visible on paper. */
@Composable
fun Swatches(colors: List<Int>, size: Dp = 16.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
        colors.take(3).forEach { color ->
            Box(Modifier.size(size).clip(CircleShape).background(Color(color)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        }
    }
}
