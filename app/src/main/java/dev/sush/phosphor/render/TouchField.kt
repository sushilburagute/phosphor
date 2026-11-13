package dev.sush.phosphor.render

import kotlin.math.abs

/**
 * Touch state shared between an input surface and its renderer.
 *
 * Coordinates are normalized to the surface (0..1). Times are real seconds, independent of
 * the motion speed setting, so a tap always gets a visible, finite answer. The owner keeps
 * requesting frames while [isActive] and stops afterwards.
 */
class TouchField {
    private val x = FloatArray(MAX)
    private val y = FloatArray(MAX)
    private val born = FloatArray(MAX) { -LIFE * 2 }
    private val power = FloatArray(MAX)
    private var next = 0
    private var held = false
    private var lastAdvance = Float.NaN

    var dragX = 0.5f; private set
    var dragY = 0.5f; private set
    /** Eased 0..1 presence of a held finger, so release settles rather than snapping. */
    var drag = 0f; private set

    fun tap(nx: Float, ny: Float, now: Float, strength: Float = 1f) {
        // Launchers may deliver both a raw touch and a tap command for the same gesture.
        val last = (next + MAX - 1) % MAX
        if (now - born[last] < 0.25f && abs(nx - x[last]) < 0.04f && abs(ny - y[last]) < 0.04f) return
        x[next] = nx.coerceIn(0f, 1f)
        y[next] = ny.coerceIn(0f, 1f)
        born[next] = now
        power[next] = strength.coerceIn(0f, 1.5f)
        next = (next + 1) % MAX
    }

    fun press(nx: Float, ny: Float) { held = true; move(nx, ny) }

    fun move(nx: Float, ny: Float) {
        dragX = nx.coerceIn(0f, 1f)
        dragY = ny.coerceIn(0f, 1f)
    }

    fun release() { held = false }

    fun clear() {
        born.fill(-LIFE * 2)
        held = false
        drag = 0f
    }

    /** Ease the drag presence; call once per frame. */
    fun advance(now: Float) {
        val dt = if (lastAdvance.isNaN()) 0.016f else (now - lastAdvance).coerceIn(0f, 0.1f)
        lastAdvance = now
        val target = if (held) 1f else 0f
        val rate = if (held) 7f else 2.2f
        drag += (target - drag) * (1f - kotlin.math.exp(-rate * dt))
        if (!held && drag < 0.002f) drag = 0f
    }

    fun isActive(now: Float): Boolean = held || drag > 0f || (0 until MAX).any { now - born[it] in 0f..LIFE }

    /** Copy live pulses into [frame], converting to artwork units. */
    fun fill(frame: Frame, now: Float) {
        var count = 0
        for (i in 0 until MAX) {
            val age = now - born[i]
            if (age < 0f || age > LIFE) continue
            val life = 1f - age / LIFE
            // Fast start, long settle: the ring decelerates as it travels.
            val travel = 1f - (1f - (age / LIFE)).let { it * it * it }
            frame.pulseX[count] = x[i] * 1000f
            frame.pulseY[count] = y[i] * frame.h
            frame.pulseR[count] = travel * 1150f
            frame.pulseA[count] = life * life * power[i]
            count++
        }
        frame.pulses = count
        frame.dragX = dragX * 1000f
        frame.dragY = dragY * frame.h
        frame.drag = drag
    }

    companion object {
        const val MAX = 6
        const val LIFE = 2.6f
    }
}
