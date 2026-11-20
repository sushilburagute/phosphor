package dev.sush.phosphor.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Display
import android.view.Surface
import dev.sush.phosphor.model.WallpaperSettings
import kotlin.math.sqrt

/** Samples a non-waking accelerometer. Only the existing animation clock requests frames. */
internal class WallpaperTilt(context: Context) : SensorEventListener {
    private val sensors by lazy { context.getSystemService(SensorManager::class.java) }
    private var display: Display? = null
    private var listening = false
    private var lastSample = -1L
    private var lastRotation = -1
    private var neutralX = 0f
    private var neutralY = 0f
    private var neutralZ = 0f
    var x = 0f
        private set
    var y = 0f
        private set

    fun start(display: Display?) {
        if (display == null) {
            stop()
            return
        }
        this.display = display
        if (listening) return
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, false) ?: return
        listening = sensors.registerListener(this, sensor, 66_667)
    }

    fun stop() {
        if (listening) sensors.unregisterListener(this)
        listening = false
        display = null
        lastSample = -1L
        x = 0f
        y = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!listening) return
        val rotation = display?.rotation ?: return
        sample(event.values[0], event.values[1], event.values[2], rotation, event.timestamp)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    internal fun sample(rawX: Float, rawY: Float, rawZ: Float, rotation: Int, now: Long) {
        val magnitude = sqrt(rawX * rawX + rawY * rawY + rawZ * rawZ)
        if (magnitude !in 7f..13f) return
        val gx = when (rotation) {
            Surface.ROTATION_90 -> -rawY
            Surface.ROTATION_180 -> -rawX
            Surface.ROTATION_270 -> rawY
            else -> rawX
        } / magnitude
        val gy = when (rotation) {
            Surface.ROTATION_90 -> rawX
            Surface.ROTATION_180 -> -rawY
            Surface.ROTATION_270 -> -rawX
            else -> rawY
        } / magnitude
        val gz = rawZ / magnitude
        if (lastSample < 0 || rotation != lastRotation) {
            neutralX = gx
            neutralY = gy
            neutralZ = gz
            lastRotation = rotation
            lastSample = now
            x = 0f
            y = 0f
            return
        }
        val dt = (now - lastSample).coerceAtLeast(0L) / 1_000_000_000f
        lastSample = now
        val blend = dt / (0.12f + dt)
        // Pitch relative to the resting pose works with the phone flat or held upright.
        val pitch = gy * neutralZ - gz * neutralY
        x += (((neutralX - gx) * 3f).coerceIn(-1f, 1f) - x) * blend
        y += ((pitch * 3f).coerceIn(-1f, 1f) - y) * blend
    }
}

internal fun WallpaperSettings.allowsTilt(profile: WallpaperPowerProfile) =
    tiltParallax && parallax && !reducedMotion && speed > 0f && !profile.energySaving
