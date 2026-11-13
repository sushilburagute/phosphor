package dev.sush.phosphor.render

import android.os.Handler
import android.os.SystemClock

/** Coalesces input and animation requests into one callback at the chosen frame rate. */
internal class WallpaperFrameScheduler(
    private val handler: Handler,
    private val invalidateOnVsync: Boolean = false,
    private val draw: () -> Unit,
) {
    private var pending = false
    private var lastFrameAt: Long? = null
    private val frame = Runnable {
        pending = false
        draw()
    }

    fun request(fps: Int) {
        if (pending) return
        val rate = fps.coerceIn(1, 60)
        // Surface draws round up to enforce the cap. A View invalidates just before
        // its intended vsync; rounding up there can miss it and turn 30 fps into 20.
        val interval = if (invalidateOnVsync) 1000L / rate else (1000L + rate - 1L) / rate
        val now = SystemClock.uptimeMillis()
        val next = lastFrameAt?.plus(interval) ?: now
        pending = true
        handler.postAtTime(frame, maxOf(now, next))
    }

    /** Also call for draws requested by Android, which must consume the pending frame. */
    fun onFrame() {
        cancelPending()
        lastFrameAt = SystemClock.uptimeMillis()
    }

    fun stop() {
        cancelPending()
        lastFrameAt = null
    }

    private fun cancelPending() {
        handler.removeCallbacks(frame)
        pending = false
    }
}
