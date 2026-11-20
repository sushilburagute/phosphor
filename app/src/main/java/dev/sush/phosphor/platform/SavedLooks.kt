package dev.sush.phosphor.platform

import android.content.SharedPreferences
import dev.sush.phosphor.model.ScenePalette
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperKind
import dev.sush.phosphor.model.WallpaperPreset
import dev.sush.phosphor.model.WallpaperSettings
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

internal data class SavedLook(val id: String, val name: String, val settings: WallpaperSettings)

internal fun WallpaperSettings.forPreset(preset: WallpaperPreset, importedPath: String?): WallpaperSettings {
    val path = importedPath.takeIf { preset.kind == WallpaperKind.IMAGE }
    return copy(
        presetId = preset.id,
        imagePath = path,
        musicReactive = musicReactive && presetId == preset.id && preset.id == "ascii-echo",
        composition = if (presetId == preset.id) composition else 0.5f,
        palette = if (presetId == preset.id) palette else ScenePalette.ORIGINAL,
        imageFocusX = if (path != null && path == imagePath) imageFocusX else 0.5f,
        imageFocusY = if (path != null && path == imagePath) imageFocusY else 0.5f,
    )
}

/** Older installations bookmarked preset IDs. Capture those bookmarks before the next import or edit. */
internal class SavedLooks(private val preferences: SharedPreferences) {
    fun load(active: WallpaperSettings): List<SavedLook> {
        val raw = preferences.getString("saved_looks", null)
        if (raw != null) {
            val records = try { JSONArray(raw) } catch (_: JSONException) { return emptyList() }
            return buildList {
                for (i in 0 until records.length()) {
                    val item = records.optJSONObject(i) ?: continue
                    val settings = item.optJSONObject("settings") ?: continue
                    val id = item.optString("id").takeIf { it.isNotEmpty() } ?: continue
                    add(SavedLook(id, item.optString("name"), SettingsStore.decode(settings)))
                }
            }
        }

        val favorites = preferences.getStringSet("favorites", emptySet()).orEmpty()
        val image = preferences.getString("image_path", null) ?: active.imagePath
        val migrated = WallpaperCatalog.presets.filter { it.id in favorites }.map { preset ->
            SavedLook(UUID.randomUUID().toString(), preset.name, active.forPreset(preset, image))
        }
        replace(migrated)
        return migrated
    }

    fun save(look: SavedLook, active: WallpaperSettings): List<SavedLook> {
        val current = load(active)
        val next = if (current.any { it.id == look.id }) {
            current.map { if (it.id == look.id) look else it }
        } else listOf(look) + current
        replace(next)
        return next
    }

    fun remove(id: String, active: WallpaperSettings): List<SavedLook> {
        val next = load(active).filterNot { it.id == id }
        replace(next)
        return next
    }

    fun retainedImagePaths(active: WallpaperSettings, preview: WallpaperSettings, latest: String?): Set<String> =
        load(active).mapNotNullTo(mutableSetOf()) { it.settings.imagePath } +
            setOfNotNull(active.imagePath, preview.imagePath, latest)

    private fun replace(looks: List<SavedLook>) {
        val records = JSONArray()
        for (look in looks) {
            records.put(JSONObject().apply {
                put("id", look.id)
                put("name", look.name)
                put("settings", SettingsStore.encode(look.settings))
            })
        }
        preferences.edit().putString("saved_looks", records.toString()).remove("favorites").apply()
    }
}
