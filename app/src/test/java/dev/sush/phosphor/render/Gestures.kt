package dev.sush.phosphor.render

import kotlin.math.sin

/**
 * Scripted gestures for renders and tests. Each returns a [TouchField] already stepped at
 * 60 Hz up to [Gesture.now]; pass that as the renderer's realSeconds.
 */
internal object Gestures {
    class Gesture(val touch: TouchField, val now: Float)

    private const val START = 10f
    private const val STEP = 1f / 60f

    /** A single tap mid-screen, 0.5 s into its ripple. */
    fun tap(): Gesture {
        val touch = TouchField().apply { tap(0.5f, 0.45f, START) }
        return Gesture(touch, START + 0.5f)
    }

    /** A finger sweeping up and across in a gentle arc, still on the glass at the end. */
    fun swipe(): Gesture {
        val touch = TouchField()
        touch.press(0.18f, 0.72f)
        var now = START
        touch.advance(now)
        val steps = 27
        for (i in 1..steps) {
            val u = i / steps.toFloat()
            now += STEP
            touch.move(0.18f + u * 0.64f, 0.72f - u * 0.4f + sin(u * 3.14f) * -0.06f)
            touch.advance(now)
        }
        return Gesture(touch, now)
    }

    /** A finger resting at one point for [seconds]. */
    fun hold(seconds: Float, x: Float = 0.5f, y: Float = 0.45f): Gesture {
        val touch = TouchField()
        touch.press(x, y)
        var now = START
        touch.advance(now)
        repeat((seconds / STEP).toInt()) {
            now += STEP
            touch.advance(now)
        }
        return Gesture(touch, now)
    }

    /** A full charge released, caught [after] seconds into the burst. */
    fun burst(after: Float = 0.35f): Gesture {
        val held = hold(TouchField.FULL_HOLD)
        val touch = held.touch
        var now = held.now
        touch.release(now)
        repeat((after / STEP).toInt()) {
            now += STEP
            touch.advance(now)
        }
        return Gesture(touch, now)
    }

    fun named(name: String): Gesture? = when (name) {
        "tap" -> tap()
        "swipe" -> swipe()
        "hold" -> hold(TouchField.FULL_HOLD)
        "burst" -> burst()
        else -> null
    }
}
