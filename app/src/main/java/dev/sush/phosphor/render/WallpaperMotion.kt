package dev.sush.phosphor.render

import kotlin.math.abs
import kotlin.math.exp

/** A bounded interaction snapshot, shared by the actual wallpaper and its editor. */
data class WallpaperInteraction(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val strength: Float = 0f,
    val rippleProgress: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

/**
 * Retargetable, critically damped response. The clock is supplied by the surface so
 * a hidden wallpaper never accumulates time, and touches also work at zero speed.
 */
class WallpaperMotion {
    private var x = 0.5f
    private var y = 0.5f
    private var offsetX = 0f
    private var offsetY = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    private var strength = 0f
    private var ripple = 1f
    private var pressed = false

    val isActive: Boolean
        get() = pressed || strength > 0.001f || ripple < 1f ||
            abs(offsetX) + abs(offsetY) + abs(velocityX) + abs(velocityY) > 0.001f

    /** MotionEvent action values; positions are fractions of the surface, 0..1. */
    fun onTouch(action: Int, x: Float, y: Float) {
        when (action) {
            0, 2 -> {
                this.x = unit(x, 0.5f)
                this.y = unit(y, 0.5f)
                if (action == 0) ripple = 0f
                pressed = true
            }
            1 -> { pressed = false; ripple = 0f }
            3 -> { pressed = false; ripple = 1f }
        }
    }

    fun advance(deltaSeconds: Float): WallpaperInteraction {
        val dt = if (deltaSeconds.isFinite()) deltaSeconds.coerceIn(0f, 0.1f) else 0f
        if (dt == 0f) return WallpaperInteraction(x, y, strength, ripple, offsetX, offsetY)
        val targetX = if (pressed) x * 2f - 1f else 0f
        val targetY = if (pressed) y * 2f - 1f else 0f
        // Analytic critically damped spring stays stable at either 15 or 60 fps.
        val omega = if (pressed) 16f else 9f
        val decay = exp(-omega * dt)
        val dx = offsetX - targetX
        val dy = offsetY - targetY
        val jx = velocityX + omega * dx
        val jy = velocityY + omega * dy
        offsetX = targetX + (dx + jx * dt) * decay
        offsetY = targetY + (dy + jy * dt) * decay
        velocityX = (velocityX - omega * jx * dt) * decay
        velocityY = (velocityY - omega * jy * dt) * decay
        val targetStrength = if (pressed) 1f else 0f
        strength += (targetStrength - strength) * (1f - exp(-dt * if (pressed) 18f else 5f))
        if (!pressed && strength < 0.001f) strength = 0f
        if (!pressed && abs(offsetX) + abs(offsetY) + abs(velocityX) + abs(velocityY) < 0.001f) {
            offsetX = 0f; offsetY = 0f; velocityX = 0f; velocityY = 0f
        }
        ripple = (ripple + dt / 1.15f).coerceAtMost(1f)
        return WallpaperInteraction(x, y, strength, ripple, offsetX, offsetY)
    }

    fun reset() {
        x = 0.5f; y = 0.5f
        offsetX = 0f; offsetY = 0f; velocityX = 0f; velocityY = 0f
        strength = 0f; ripple = 1f; pressed = false
    }

    private fun unit(value: Float, fallback: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else fallback
}
