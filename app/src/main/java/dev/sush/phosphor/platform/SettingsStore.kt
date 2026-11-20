package dev.sush.phosphor.platform

import android.content.Context
import android.content.SharedPreferences
import dev.sush.phosphor.model.Appearance
import dev.sush.phosphor.model.WallpaperSettings
import org.json.JSONObject

/** Drafts are deliberately separate from the wallpaper currently on the user's home screen. */
class SettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("phosphor", Context.MODE_PRIVATE)
    private val listeners = mutableMapOf<() -> Unit, SharedPreferences.OnSharedPreferenceChangeListener>()

    fun load(): WallpaperSettings = read("active")

    fun save(settings: WallpaperSettings) = write("active", settings)

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
            decode(JSONObject(raw))
        } catch (_: org.json.JSONException) {
            WallpaperSettings()
        }
    }

    private fun write(key: String, settings: WallpaperSettings) {
        preferences.edit().putString(key, encode(settings).toString()).apply()
    }

    companion object {
        fun decode(json: JSONObject): WallpaperSettings {
            val defaults = WallpaperSettings()
            return defaults.copy(
                presetId = json.optString("presetId", defaults.presetId),
                speed = number(json, "speed", defaults.speed),
                intensity = number(json, "intensity", defaults.intensity),
                parallax = json.optBoolean("parallax", defaults.parallax),
                fps = json.optInt("fps", defaults.fps).coerceIn(15, 60),
                darken = number(json, "darken", defaults.darken),
                imagePath = json.optString("imagePath").takeIf { it.isNotBlank() && it != "null" },
                imageFocusX = number(json, "imageFocusX", defaults.imageFocusX),
                imageFocusY = number(json, "imageFocusY", defaults.imageFocusY),
                reducedMotion = json.optBoolean("reducedMotion", defaults.reducedMotion),
                appearance = Appearance.entries.firstOrNull { it.name == json.optString("appearance") } ?: defaults.appearance,
                wakeReveal = json.optBoolean("wakeReveal", json.optBoolean("unlockAnimation", defaults.wakeReveal)),
                lowBatteryMode = json.optBoolean("lowBatteryMode", defaults.lowBatteryMode),
                unlockAnimation = json.optBoolean("unlockAnimation", json.optBoolean("wakeReveal", defaults.unlockAnimation)),
            )
        }

        fun encode(settings: WallpaperSettings): JSONObject = JSONObject().apply {
            put("presetId", settings.presetId)
            put("speed", bounded(settings.speed, 0.5f))
            put("intensity", bounded(settings.intensity, 0.6f))
            put("parallax", settings.parallax)
            put("fps", settings.fps.coerceIn(15, 60))
            put("darken", bounded(settings.darken, 0f))
            put("imagePath", settings.imagePath ?: JSONObject.NULL)
            put("imageFocusX", bounded(settings.imageFocusX, 0.5f))
            put("imageFocusY", bounded(settings.imageFocusY, 0.5f))
            put("reducedMotion", settings.reducedMotion)
            put("appearance", settings.appearance.name)
            put("wakeReveal", settings.wakeReveal)
            put("lowBatteryMode", settings.lowBatteryMode)
            put("unlockAnimation", settings.unlockAnimation)
        }

        private fun bounded(value: Float, fallback: Float): Float =
            if (value.isFinite()) value.coerceIn(0f, 1f) else fallback

        private fun number(json: JSONObject, key: String, fallback: Float): Float =
            bounded(json.optDouble(key, fallback.toDouble()).toFloat(), fallback)
    }
}
