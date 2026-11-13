package dev.sush.phosphor.ui

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import dev.sush.phosphor.ui.theme.Motion
import dev.sush.phosphor.ui.theme.Palette
import dev.sush.phosphor.ui.theme.PhosphorType

@Composable
fun Glyph(@DrawableRes id: Int, description: String?, modifier: Modifier = Modifier, tint: Color = androidx.compose.material3.LocalContentColor.current) {
    Icon(painterResource(id), contentDescription = description, modifier = modifier.size(22.dp), tint = tint)
}

/** Window-size classes from Material's breakpoints, driving gutters and grid columns. */
enum class WindowWidth(val gutter: Dp, val columns: Int) {
    Compact(16.dp, 2), Medium(24.dp, 3), Expanded(32.dp, 4), Large(40.dp, 5);

    companion object {
        fun of(width: Dp) = when {
            width < 600.dp -> Compact
            width < 840.dp -> Medium
            width < 1200.dp -> Expanded
            else -> Large
        }
    }
}

/**
 * Width over height of this phone's full screen in portrait, so plates show a wallpaper at
 * exactly the proportions it will be used at: a 20:9 phone gets a 20:9 plate. Clamped so a
 * foldable's near-square inner screen or a tablet still gets a recognizably tall plate.
 */
@Composable
fun rememberScreenRatio(): Float {
    val context = LocalContext.current
    // A new configuration (fold, rotation, resize) is the cue to measure again.
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { screenRatio(context) }
}

internal fun screenRatio(context: Context): Float {
    val bounds = if (Build.VERSION.SDK_INT >= 30) {
        context.getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds?.let { it.width() to it.height() }
    } else null
    val (w, h) = bounds ?: context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
    if (w <= 0 || h <= 0) return 0.45f
    return (minOf(w, h).toFloat() / maxOf(w, h)).coerceIn(0.42f, 0.62f)
}

/** A quiet label above a group, in the accent color, as in Android's own settings. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, bottom = 8.dp).semantics { heading() },
    )
}

/**
 * A grouped list: items are separate rounded tiles with a 2dp gap, large outer corners and
 * small inner ones. Space and shape do the work that dividers and borders used to.
 */
@Composable
fun Group(modifier: Modifier = Modifier, content: GroupScope.() -> Unit) {
    val items = GroupScope().apply(content).items
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { index, item ->
            val outer = 24.dp
            val inner = 6.dp
            val shape = RoundedCornerShape(
                topStart = if (index == 0) outer else inner, topEnd = if (index == 0) outer else inner,
                bottomStart = if (index == items.lastIndex) outer else inner, bottomEnd = if (index == items.lastIndex) outer else inner,
            )
            Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)) { item() }
        }
    }
}

class GroupScope {
    internal val items = mutableListOf<@Composable ColumnScope.() -> Unit>()
    fun item(content: @Composable ColumnScope.() -> Unit) { items += content }
}

private val ItemPadding = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)

@Composable
fun SliderItem(label: String, value: Float, description: String? = null, enabled: Boolean = true, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(end = 12.dp),
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = .4f))
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

/** Label, explanation and switch form one accessible toggle; the whole tile is the target. */
@Composable
fun ToggleItem(title: String, description: String?, checked: Boolean, onChecked: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked)
            .then(ItemPadding),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = .4f))
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A tappable row that opens something: title, optional detail, optional trailing text. */
@Composable
fun LinkItem(title: String, description: String? = null, trailing: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .then(ItemPadding),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A titled item whose control sits below it (appearance, frame rate). */
@Composable
fun ChoiceItem(title: String, description: String? = null, content: @Composable () -> Unit) {
    Column(ItemPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

/**
 * A connected button group: pills that share a track, the selected one filled. Used for
 * appearance, theme and frame rate.
 */
@Composable
fun <T> Segmented(options: List<Triple<T, String, Int?>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(scheme.surfaceContainerHigh).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label, icon) ->
            val on = value == selected
            val background by animateColorAsState(if (on) scheme.primary else Color.Transparent, tween(Motion.QUICK), label = "seg")
            val tint = when { !enabled -> scheme.onSurface.copy(alpha = .38f); on -> scheme.onPrimary; else -> scheme.onSurfaceVariant }
            Row(
                Modifier.weight(1f).fillMaxSize().clip(CircleShape).background(if (enabled) background else Color.Transparent)
                    .selectable(on, enabled = enabled, role = Role.RadioButton) { onSelect(value) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) { Glyph(icon, null, Modifier.size(18.dp), tint); Spacer(Modifier.width(6.dp)) }
                Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
            }
        }
    }
}

/** A small ember dot: the one place the accent appears, meaning "on your phone now". */
@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    Box(modifier.size(7.dp).clip(CircleShape).background(Palette.Ember))
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
