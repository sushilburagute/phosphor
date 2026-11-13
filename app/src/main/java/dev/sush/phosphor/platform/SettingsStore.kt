package dev.sush.phosphor.platform

import android.content.Context
import android.content.SharedPreferences
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

    fun addListener(listener: () -> Unit) {
        if (listeners.containsKey(listener)) return
        val adapter = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "active" || key == "preview") listener()
        }
        listeners[listener] = adapter
        preferences.registerOnSharedPreferenceChangeListener(adapter)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)?.let(preferences::unregisterOnSharedPreferenceChangeListener)
    }

    private fun read(key: String): WallpaperSettings {
        val defaults = WallpaperSettings()
        return try {
            val json = JSONObject(preferences.getString(key, null) ?: return defaults)
            defaults.copy(
                presetId = json.optString("presetId", defaults.presetId),
                speed = number(json, "speed", defaults.speed, 0f, 1f),
                intensity = number(json, "intensity", defaults.intensity, 0f, 1f),
                parallax = json.optBoolean("parallax", defaults.parallax),
                fps = json.optInt("fps", defaults.fps).coerceIn(15, 60),
                darken = number(json, "darken", defaults.darken, 0f, 1f),
                imagePath = json.optString("imagePath").takeIf { it.isNotBlank() && it != "null" },
                reducedMotion = json.optBoolean("reducedMotion", defaults.reducedMotion),
            )
        } catch (_: org.json.JSONException) {
            defaults
        }
    }

    private fun write(key: String, settings: WallpaperSettings) {
        val json = JSONObject().apply {
            put("presetId", settings.presetId)
            put("speed", bounded(settings.speed, 0.5f))
            put("intensity", bounded(settings.intensity, 0.6f))
            put("parallax", settings.parallax)
            put("fps", settings.fps.coerceIn(15, 60))
            put("darken", bounded(settings.darken, 0f))
            put("imagePath", settings.imagePath ?: JSONObject.NULL)
            put("reducedMotion", settings.reducedMotion)
        }
        preferences.edit().putString(key, json.toString()).apply()
    }

    private fun bounded(value: Float, fallback: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else fallback

    private fun number(json: JSONObject, key: String, fallback: Float, min: Float, max: Float): Float {
        val value = json.optDouble(key, fallback.toDouble()).toFloat()
        return if (value.isFinite()) value.coerceIn(min, max) else fallback
    }
}
