package dev.sush.phosphor.render

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Touch state shared between an input surface and its renderer.
 *
 * Coordinates are normalized to the surface (0..1). Times are real seconds, independent of
 * the motion speed setting, so a tap always gets a visible, finite answer. The owner keeps
 * requesting frames while [isActive] and stops afterwards.
 *
 * Four gestures reach the artwork:
 * - a tap sends a pulse (an expanding wavefront),
 * - a held finger is a presence the art leans toward ([drag]),
 * - a moving finger has a velocity ([flowX], [flowY]) and leaves a fading wake ([TRAIL] points),
 * - a finger held still builds [charge]; lifting it releases a burst pulse sized by the charge.
 */
class TouchField {
    private val x = FloatArray(MAX)
    private val y = FloatArray(MAX)
    private val born = FloatArray(MAX) { -LIFE * 2 }
    private val power = FloatArray(MAX)
    private var next = 0
    private var held = false
    private var lastAdvance = Float.NaN

    private val trailX = FloatArray(TRAIL)
    private val trailY = FloatArray(TRAIL)
    private val trailBorn = FloatArray(TRAIL) { -TRAIL_LIFE * 2 }
    private var trailNext = 0
    private var lastX = 0.5f
    private var lastY = 0.5f
    private var resting = 0f

    var dragX = 0.5f; private set
    var dragY = 0.5f; private set
    /** Eased 0..1 presence of a held finger, so release settles rather than snapping. */
    var drag = 0f; private set
    /** Eased finger velocity in surface units per second. Decays after release, like inertia. */
    var flowX = 0f; private set
    var flowY = 0f; private set
    /** 0..1 energy built by holding a finger still. Drains quickly once the finger moves. */
    var charge = 0f; private set

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

    fun press(nx: Float, ny: Float) {
        held = true
        move(nx, ny)
        lastX = dragX
        lastY = dragY
        resting = 0f
        charge = 0f
    }

    fun move(nx: Float, ny: Float) {
        dragX = nx.coerceIn(0f, 1f)
        dragY = ny.coerceIn(0f, 1f)
    }

    /**
     * Lift the finger. With a real [now], a charged hold bursts as a strong pulse; the
     * burst bypasses the tap de-duplication so it lands even right after the press pulse.
     */
    fun release(now: Float = Float.NaN) {
        if (held && now.isFinite() && charge > 0.15f) {
            x[next] = dragX
            y[next] = dragY
            born[next] = now
            power[next] = (0.5f + charge).coerceAtMost(1.5f)
            next = (next + 1) % MAX
        }
        held = false
        resting = 0f
        charge = 0f
    }

    fun clear() {
        born.fill(-LIFE * 2)
        trailBorn.fill(-TRAIL_LIFE * 2)
        held = false
        drag = 0f
        flowX = 0f
        flowY = 0f
        charge = 0f
        resting = 0f
        lastAdvance = Float.NaN
    }

    /** Ease the drag presence, velocity and charge, and record the wake; call once per frame. */
    fun advance(now: Float) {
        val first = lastAdvance.isNaN()
        val dt = if (first) 0.016f else (now - lastAdvance).coerceIn(0f, 0.1f)
        lastAdvance = now
        val target = if (held) 1f else 0f
        val rate = if (held) 7f else 2.2f
        drag += (target - drag) * (1f - exp(-rate * dt))
        if (!held && drag < 0.002f) drag = 0f

        if (held && dt > 0f && !first) {
            val vx = (dragX - lastX) / dt
            val vy = (dragY - lastY) / dt
            val k = 1f - exp(-14f * dt)
            flowX += (vx - flowX) * k
            flowY += (vy - flowY) * k
            val moved = sqrt((dragX - lastX).let { it * it } + (dragY - lastY).let { it * it })
            if (moved > 0.004f) {
                trailX[trailNext] = dragX
                trailY[trailNext] = dragY
                trailBorn[trailNext] = now
                trailNext = (trailNext + 1) % TRAIL
            }
            val speed = sqrt(flowX * flowX + flowY * flowY)
            // A brief rest is not a hold: charge gathers only after CHARGE_DELAY of stillness.
            resting = if (speed < 0.12f) resting + dt else 0f
            charge = if (resting > CHARGE_DELAY) {
                (charge + dt / CHARGE_TIME).coerceAtMost(1f)
            } else {
                charge * exp(-8f * dt)
            }
        } else if (!held) {
            val decay = exp(-3.2f * dt)
            flowX *= decay
            flowY *= decay
            if (abs(flowX) < 0.002f && abs(flowY) < 0.002f) { flowX = 0f; flowY = 0f }
        }
        lastX = dragX
        lastY = dragY
    }

    fun isActive(now: Float): Boolean = held || drag > 0f || flowX != 0f || flowY != 0f ||
        (0 until MAX).any { now - born[it] in 0f..LIFE } ||
        (0 until TRAIL).any { now - trailBorn[it] in 0f..TRAIL_LIFE }

    /** Copy live pulses, the wake and the gesture state into [frame], in artwork units. */
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

        // Oldest first, so scenes can draw the wake as one continuous stroke.
        var trail = 0
        for (step in 0 until TRAIL) {
            val i = (trailNext + step) % TRAIL
            val age = now - trailBorn[i]
            if (age < 0f || age > TRAIL_LIFE) continue
            val life = 1f - age / TRAIL_LIFE
            frame.trailX[trail] = trailX[i] * 1000f
            frame.trailY[trail] = trailY[i] * frame.h
            frame.trailA[trail] = life * life
            trail++
        }
        frame.trail = trail
        frame.dragX = dragX * 1000f
        frame.dragY = dragY * frame.h
        frame.drag = drag
        frame.flowX = flowX * 1000f
        frame.flowY = flowY * frame.h
        frame.charge = charge
    }

    companion object {
        const val MAX = 6
        const val LIFE = 2.6f
        /** Wake points kept; a fast swipe across the screen fills about half. */
        const val TRAIL = 32
        const val TRAIL_LIFE = 1.4f
        /** Seconds of stillness before a held finger starts to gather charge. */
        const val CHARGE_DELAY = 0.5f
        /** Seconds of gathering from empty to full charge. */
        const val CHARGE_TIME = 1.4f
        /** A rest long enough to reach full charge. */
        const val FULL_HOLD = CHARGE_DELAY + CHARGE_TIME + 0.1f
    }
}
