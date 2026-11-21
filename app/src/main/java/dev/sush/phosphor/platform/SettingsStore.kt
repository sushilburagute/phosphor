package dev.sush.phosphor.platform

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.sush.phosphor.model.ScenePalette
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperSettings
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime

/** Drafts are deliberately separate from the wallpaper currently on the user's home screen. */
class SettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("phosphor", Context.MODE_PRIVATE)
    private val savedLooks = SavedLooks(context.applicationContext.getSharedPreferences("phosphor_ui", Context.MODE_PRIVATE))
    private val listeners = mutableMapOf<() -> Unit, SharedPreferences.OnSharedPreferenceChangeListener>()

    fun load(): WallpaperSettings = read("active")

    fun save(settings: WallpaperSettings) {
        // Mark the current slot as seen even when the picker covers the live engine at a boundary.
        val slot = currentSlot()
        preferences.edit {
            if (slot != null) putString("rotationSlot", slot.stamp)
            putString("active", encode(settings).toString())
        }
    }

    internal fun rotation() = LookRotation.decode(preferences.getString("rotation", null))

    internal fun saveRotation(schedule: LookRotation) {
        if (schedule == rotation()) return
        preferences.edit {
            remove("rotationSlot")
            putString("rotation", schedule.encode())
        }
    }

    internal fun currentSlot(now: ZonedDateTime = ZonedDateTime.now()): RotationSlot? {
        val schedule = rotation()
        if (schedule.mode == RotationMode.OFF) return null
        val looks = savedLooks.load(load()).filter { look ->
            WallpaperCatalog.presets.any { it.id == look.settings.presetId }
        }
        return schedule.resolve(looks, now)
    }

    /** A manual apply survives until the next slot, even across engine recreation. */
    internal fun applySlot(slot: RotationSlot) {
        val stamp = slot.stamp
        if (preferences.getString("rotationSlot", null) == stamp) return
        preferences.edit {
            putString("rotationSlot", stamp)
            putString("active", encode(slot.look.settings).toString())
        }
    }

    internal fun observeRotation(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.registerOnSharedPreferenceChangeListener(listener)

    internal fun stopObservingRotation(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.unregisterOnSharedPreferenceChangeListener(listener)

    fun loadPreview(): WallpaperSettings = if (preferences.contains("preview")) read("preview") else load()

    fun savePreview(settings: WallpaperSettings) = write("preview", settings)

    fun commitPreview() = save(loadPreview())

    fun addListener(listener: () -> Unit) = addListener(listener, preview = null)

    /** A preview follows the active wallpaper until the first draft is stored. Null observes both keys. */
    fun addListener(listener: () -> Unit, preview: Boolean?) {
        if (listeners.containsKey(listener)) return
        val adapter = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            val relevant = when (key) {
                "active" -> preview != true || !preferences.contains("preview")
                "preview" -> preview != false
                else -> false
            }
            if (relevant) listener()
        }
        listeners[listener] = adapter
        preferences.registerOnSharedPreferenceChangeListener(adapter)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)?.let(preferences::unregisterOnSharedPreferenceChangeListener)
    }

    private fun read(key: String): WallpaperSettings {
        val raw = preferences.getString(key, null) ?: return WallpaperSettings()
        return try {
            val settings = decode(JSONObject(raw))
            // Retired presets fall back for the live wallpaper and draft. Saved looks keep
            // their original IDs so they cannot be shown as a different wallpaper.
            settings.copy(presetId = WallpaperCatalog.find(settings.presetId).id)
        } catch (_: org.json.JSONException) {
            WallpaperSettings()
        }
    }

    private fun write(key: String, settings: WallpaperSettings) {
        preferences.edit().putString(key, encode(settings).toString()).apply()
    }

    companion object {
        fun decode(json: JSONObject): WallpaperSettings {
            val defaults = WallpaperSettings(presetId = json.optString("presetId", WallpaperCatalog.presets.first().id))
            return defaults.copy(
                musicReactive = json.optBoolean("musicReactive", defaults.musicReactive),
                composition = number(json, "composition", defaults.composition),
                palette = ScenePalette.entries.firstOrNull { it.name == json.optString("palette") } ?: defaults.palette,
                customColors = decodeColors(json.optJSONArray("customColors")) ?: defaults.customColors,
                speed = number(json, "speed", defaults.speed),
                intensity = number(json, "intensity", defaults.intensity),
                parallax = json.optBoolean("parallax", defaults.parallax),
                tiltParallax = json.optBoolean("tiltParallax", defaults.tiltParallax),
                fps = json.optInt("fps", defaults.fps).coerceIn(15, 60),
                darken = number(json, "darken", defaults.darken),
                imagePath = json.optString("imagePath").takeIf { it.isNotBlank() && it != "null" },
                imageFocusX = number(json, "imageFocusX", defaults.imageFocusX),
                imageFocusY = number(json, "imageFocusY", defaults.imageFocusY),
                reducedMotion = json.optBoolean("reducedMotion", defaults.reducedMotion),
                wakeReveal = json.optBoolean("wakeReveal", json.optBoolean("unlockAnimation", defaults.wakeReveal)),
                lowBatteryMode = json.optBoolean("lowBatteryMode", defaults.lowBatteryMode),
                unlockAnimation = json.optBoolean("unlockAnimation", json.optBoolean("wakeReveal", defaults.unlockAnimation)),
            )
        }

        fun encode(settings: WallpaperSettings): JSONObject = JSONObject().apply {
            put("presetId", settings.presetId)
            if (settings.musicReactive) put("musicReactive", true)
            put("composition", bounded(settings.composition, 0.5f))
            put("palette", settings.palette.name)
            put("customColors", JSONArray(settings.customColors))
            put("speed", bounded(settings.speed, 0.5f))
            put("intensity", bounded(settings.intensity, 0.6f))
            put("parallax", settings.parallax)
            put("tiltParallax", settings.tiltParallax)
            put("fps", settings.fps.coerceIn(15, 60))
            put("darken", bounded(settings.darken, 0f))
            put("imagePath", settings.imagePath ?: JSONObject.NULL)
            put("imageFocusX", bounded(settings.imageFocusX, 0.5f))
            put("imageFocusY", bounded(settings.imageFocusY, 0.5f))
            put("reducedMotion", settings.reducedMotion)
            put("wakeReveal", settings.wakeReveal)
            put("lowBatteryMode", settings.lowBatteryMode)
            put("unlockAnimation", settings.unlockAnimation)
        }

        private fun decodeColors(colors: JSONArray?): List<Int>? {
            if (colors == null || colors.length() != 3) return null
            return List(3) { index ->
                val color = colors.opt(index) as? Int ?: return null
                // Colors are stored as signed, opaque ARGB integers.
                if (color ushr 24 != 255) return null
                color
            }
        }

        private fun bounded(value: Float, fallback: Float): Float =
            if (value.isFinite()) value.coerceIn(0f, 1f) else fallback

        private fun number(json: JSONObject, key: String, fallback: Float): Float =
            bounded(json.optDouble(key, fallback.toDouble()).toFloat(), fallback)
    }
}
