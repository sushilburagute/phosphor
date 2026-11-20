package dev.sush.phosphor.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.sush.phosphor.platform.LookRotation
import dev.sush.phosphor.platform.RotationMode
import dev.sush.phosphor.platform.SavedLook
import java.time.LocalDate
import java.util.Calendar

@Composable
internal fun RotationDialog(initial: LookRotation, looks: List<SavedLook>, live: Boolean,
    onDismiss: () -> Unit, onSave: (LookRotation) -> Unit) {
    val ids = looks.map { it.id }
    val available = initial.lookIds.filter { it in ids }
    var mode by rememberSaveable { mutableStateOf(initial.mode) }
    var selected by rememberSaveable {
        mutableStateOf<List<String>>(if (initial.mode == RotationMode.DAILY) available else ids)
    }
    var dayId by rememberSaveable { mutableStateOf(available.firstOrNull() ?: ids.firstOrNull()) }
    var eveningId by rememberSaveable { mutableStateOf(available.getOrNull(1) ?: ids.firstOrNull { it != dayId }) }
    var day by rememberSaveable { mutableIntStateOf(initial.dayMinute) }
    var evening by rememberSaveable { mutableIntStateOf(initial.eveningMinute) }
    val daily = selected.filter { it in ids }
    val valid = when (mode) {
        RotationMode.OFF -> true
        RotationMode.DAILY -> daily.size >= 2
        RotationMode.DAY_EVENING -> dayId in ids && eveningId in ids && dayId != eveningId && day < evening
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Rotate saved looks") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Segmented(RotationMode.entries.map { Triple(it, it.label, null) }, mode, { mode = it })
                if (mode != RotationMode.OFF) {
                    if (!live) Text("Set Phosphor as your live wallpaper. A manually applied look stays until the next scheduled change.")
                    if (looks.size < 2) Text("Save at least two looks to create a rotation.")
                }
                when (mode) {
                    RotationMode.OFF -> Text("Keep the current wallpaper until you choose another.")
                    RotationMode.DAILY -> {
                        Text("Choose at least two looks. They rotate in selection order at local midnight.")
                        looks.forEach { look ->
                            val checked = look.id in daily
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox) {
                                selected = if (checked) daily - look.id else daily + look.id
                            }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked, onCheckedChange = null)
                                Text(if (checked) "${daily.indexOf(look.id) + 1}. ${look.name}" else look.name,
                                    modifier = Modifier.weight(1f).padding(start = 12.dp))
                            }
                        }
                    }
                    RotationMode.DAY_EVENING -> {
                        LookChoice("Day look", looks, dayId) { dayId = it }
                        TimeChoice("Day starts", day) { day = it }
                        LookChoice("Evening look", looks, eveningId) { eveningId = it }
                        TimeChoice("Evening starts", evening) { evening = it }
                        if (dayId != null && dayId == eveningId) Text("Choose a different look for the evening.")
                        if (day >= evening) Text("Choose an evening time later than the day start.")
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = {
            val chosen = when (mode) {
                RotationMode.DAILY -> daily
                RotationMode.DAY_EVENING -> listOfNotNull(dayId, eveningId)
                RotationMode.OFF -> emptyList()
            }
            val firstDay = if (mode == initial.mode && chosen == initial.lookIds) initial.firstDay else LocalDate.now().toEpochDay()
            onSave(LookRotation(mode, chosen, firstDay, day, evening))
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LookChoice(label: String, looks: List<SavedLook>, id: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = looks.isNotEmpty()) {
            Text("$label · ${looks.firstOrNull { it.id == id }?.name ?: "Choose a look"}")
        }
        DropdownMenu(expanded, { expanded = false }) {
            looks.forEach { look ->
                DropdownMenuItem(text = { Text(look.name) }, onClick = { onSelect(look.id); expanded = false })
            }
        }
    }
}

@Composable
private fun TimeChoice(label: String, minute: Int, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    var picking by rememberSaveable { mutableStateOf(false) }
    val time = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minute / 60)
        set(Calendar.MINUTE, minute % 60)
    }
    TextButton(onClick = { picking = true }) {
        Text("$label · ${DateFormat.getTimeFormat(context).format(time.time)}")
    }
    if (picking) DisposableEffect(context) {
        val dialog = TimePickerDialog(context, { _, hour, min -> onChange(hour * 60 + min) },
            minute / 60, minute % 60, DateFormat.is24HourFormat(context))
        dialog.setOnDismissListener { picking = false }
        dialog.show()
        onDispose { dialog.setOnDismissListener(null); dialog.dismiss() }
    }
}
