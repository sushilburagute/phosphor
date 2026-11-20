package dev.sush.phosphor.platform

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

internal enum class RotationMode(val label: String) { OFF("Off"), DAILY("Daily"), DAY_EVENING("Day and evening") }

internal data class RotationSlot(val look: SavedLook, val startsAt: Long, val endsAt: Long) {
    val stamp get() = "$startsAt/${look.id}/${SettingsStore.encode(look.settings)}"
}

internal data class LookRotation(
    val mode: RotationMode = RotationMode.OFF,
    val lookIds: List<String> = emptyList(),
    val firstDay: Long = 0,
    val dayMinute: Int = 480,
    val eveningMinute: Int = 1200,
) {
    fun resolve(looks: List<SavedLook>, now: ZonedDateTime): RotationSlot? {
        if (mode == RotationMode.OFF) return null
        val byId = looks.associateBy { it.id }
        val today = now.toLocalDate()
        val zone = now.zone
        if (mode == RotationMode.DAILY) {
            val selected = lookIds.mapNotNull(byId::get)
            if (selected.isEmpty()) return null
            val index = Math.floorMod(today.toEpochDay() - firstDay, selected.size.toLong()).toInt()
            return RotationSlot(selected[index], today.atStartOfDay(zone).toInstant().toEpochMilli(),
                today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        }

        // Resolve wall-clock boundaries in the local zone, including skipped/repeated DST hours.
        fun boundary(date: LocalDate, minute: Int): Long {
            val local = date.atTime(LocalTime.of(minute / 60, minute % 60))
            val transition = zone.rules.getTransition(local)
            if (transition != null && transition.isGap) return transition.instant.toEpochMilli()
            return local.atZone(zone).toInstant().toEpochMilli()
        }
        val instant = now.toInstant().toEpochMilli()
        val boundaries = (-1L..1L).flatMap { offset ->
            val date = today.plusDays(offset)
            listOf(boundary(date, dayMinute) to 0, boundary(date, eveningMinute) to 1)
        }.sortedBy { it.first }
        val current = boundaries.last { it.first <= instant }
        val next = boundaries.first { it.first > instant }
        val look = lookIds.getOrNull(current.second)?.let(byId::get)
            ?: lookIds.firstNotNullOfOrNull(byId::get) ?: return null
        return RotationSlot(look, current.first, next.first)
    }

    fun encode(): String = JSONObject().apply {
        put("mode", mode.name)
        put("ids", JSONArray(lookIds))
        put("firstDay", firstDay)
        put("day", dayMinute)
        put("evening", eveningMinute)
    }.toString()

    companion object {
        fun decode(raw: String?): LookRotation {
            val json = try { JSONObject(raw ?: return LookRotation()) }
                catch (_: org.json.JSONException) { return LookRotation() }
            val mode = RotationMode.entries.firstOrNull { it.name == json.optString("mode") } ?: return LookRotation()
            val array = json.optJSONArray("ids") ?: return LookRotation()
            val ids = (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }.distinct()
            val day = json.optInt("day", 480)
            val evening = json.optInt("evening", 1200)
            val validTimes = day in 0..1438 && evening in (day + 1)..1439
            if (mode == RotationMode.DAY_EVENING && (!validTimes || ids.size != 2)) return LookRotation()
            return LookRotation(mode, ids, json.optLong("firstDay"),
                if (validTimes) day else 480, if (validTimes) evening else 1200)
        }
    }
}
