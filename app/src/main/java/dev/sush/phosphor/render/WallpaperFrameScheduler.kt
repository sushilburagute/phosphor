package dev.sush.phosphor.render

import android.os.SystemClock
import android.view.Choreographer

/** Coalesces input and animation on display vsync, at the selected average frame rate. */
internal class WallpaperFrameScheduler(private val draw: () -> Unit) : Choreographer.FrameCallback {
    private val choreographer = Choreographer.getInstance()
    private var interval = 0L
    private var lastSlot = -1L
    private var pending = false
    private var awaitingDraw = false

    fun request(fps: Int) {
        if (pending) return
        interval = 1_000_000_000L / fps.coerceIn(1, 60)
        val delay = if (lastSlot < 0) 0L else lastSlot + interval - SystemClock.uptimeMillis() * 1_000_000L
        // Register just before the deadline. A 15 fps wallpaper can sleep between frames
        // even on a high-refresh display.
        pending = true
        choreographer.postFrameCallbackDelayed(this, (delay / 1_000_000L - 5).coerceAtLeast(0))
    }

    override fun doFrame(frameTimeNanos: Long) {
        val target = if (lastSlot < 0) frameTimeNanos else lastSlot + interval
        // Vsync timestamps can differ from their nominal period by a fraction of a ms.
        if (frameTimeNanos < target - 1_000_000L) {
            choreographer.postFrameCallback(this)
            return
        }
        // Keep the fractional deadline across refreshes, but discard missed slots after a stall.
        lastSlot = if (frameTimeNanos - target >= interval) frameTimeNanos else target
        pending = false
        awaitingDraw = true
        draw()
    }

    /** Framework redraws also consume the pending frame and start a fresh interval. */
    fun onFrame() {
        if (!awaitingDraw || pending) lastSlot = SystemClock.uptimeMillis() * 1_000_000L
        awaitingDraw = false
        cancelPending()
    }

    fun stop() {
        cancelPending()
        awaitingDraw = false
        lastSlot = -1L
    }

    private fun cancelPending() {
        if (pending) choreographer.removeFrameCallback(this)
        pending = false
    }
}
