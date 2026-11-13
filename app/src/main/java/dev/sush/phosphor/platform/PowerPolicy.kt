package dev.sush.phosphor.platform

import android.content.Intent
import android.os.BatteryManager
import dev.sush.phosphor.model.Appearance
import dev.sush.phosphor.model.WallpaperSettings

/** What the device can currently afford. Charging includes any external power source. */
data class PowerState(val level: Int = 100, val charging: Boolean = true, val batterySaver: Boolean = false) {
    companion object {
        fun from(battery: Intent?, batterySaver: Boolean): PowerState {
            if (battery == null) return PowerState(batterySaver = batterySaver)
            val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percent = if (level >= 0 && scale > 0) level * 100 / scale else 100
            val plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            return PowerState(percent.coerceIn(0, 100), plugged, batterySaver)
        }
    }
}

/** Frame budget for one surface. [motion] scales animation speed; [still] holds one frame. */
data class FrameBudget(val fps: Int, val motion: Float, val still: Boolean)

object PowerPolicy {
    const val LOW_BATTERY = 20
    const val CRITICAL_BATTERY = 5

    fun budget(settings: WallpaperSettings, power: PowerState): FrameBudget {
        var fps = settings.fps.coerceIn(15, 60)
        var motion = 1f
        var still = settings.reducedMotion
        // System Battery Saver is always respected, whatever the app setting says.
        if (power.batterySaver) { fps = minOf(fps, 15); motion = 0.6f }
        if (settings.lowBatteryMode && !power.charging) {
            when {
                power.level <= CRITICAL_BATTERY -> still = true
                power.level <= LOW_BATTERY -> { fps = minOf(fps, 15); motion = minOf(motion, 0.5f) }
            }
        }
        return FrameBudget(fps, motion, still)
    }
}

/** Resolve the artwork's treatment against the system's night mode. */
fun Appearance.isNight(systemNight: Boolean): Boolean = when (this) {
    Appearance.NIGHT -> true
    Appearance.DAY -> false
    Appearance.AUTO -> systemNight
}
