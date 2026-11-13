package dev.sush.phosphor.platform

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dev.sush.phosphor.model.WallpaperSettings

data class WallpaperBatteryState(val percent: Int? = null, val charging: Boolean = false)

data class WallpaperPowerProfile(val fps: Int, val motionScale: Float, val energySaving: Boolean)

/** One policy for the editor and live engine; no polling or battery permissions are needed. */
object WallpaperPowerPolicy {
    fun readBattery(context: Context): WallpaperBatteryState = fromIntent(
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)),
    )

    fun fromIntent(intent: Intent?): WallpaperBatteryState {
        if (intent == null) return WallpaperBatteryState()
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) {
            (level.toLong() * 100L / scale).coerceIn(0L, 100L).toInt()
        } else null
        val charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0 ||
            intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
        return WallpaperBatteryState(percent, charging)
    }

    fun resolve(
        settings: WallpaperSettings,
        powerSaveMode: Boolean,
        batteryPercent: Int?,
        charging: Boolean,
        locked: Boolean = false,
    ): WallpaperPowerProfile {
        val low = settings.lowBatteryMode && !charging && batteryPercent != null && batteryPercent in 0..20
        val critical = low && batteryPercent <= 5
        val saving = powerSaveMode || low
        return WallpaperPowerProfile(
            fps = if (saving || locked) 15 else settings.fps.coerceIn(15, 60),
            motionScale = when { critical -> 0f; saving -> 0.45f; else -> 1f },
            energySaving = saving,
        )
    }
}
