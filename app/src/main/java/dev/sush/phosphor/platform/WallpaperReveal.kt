package dev.sush.phosphor.platform

/** A bounded lock treatment, driven only while a visible interactive surface can show it. */
internal class WallpaperReveal {
    private var locked = false
    private var start = 1f
    private var target = 1f
    private var startedAt = 0L
    private var duration = 0L

    fun transitionTo(locked: Boolean, animate: Boolean, now: Long, wake: Boolean = false) {
        val next = if (locked) 0f else 1f
        if (this.locked == locked && !(wake && locked)) {
            if (!animate) snap(next)
            return
        }
        start = if (wake && locked) 1f else value(now)
        target = next
        this.locked = locked
        startedAt = now
        duration = if (!animate) 0L else if (locked) 280L else 600L
    }

    fun value(now: Long): Float {
        if (duration == 0L) return target
        val progress = ((now - startedAt).toFloat() / duration).coerceIn(0f, 1f)
        val eased = progress * progress * (3f - 2f * progress)
        return start + (target - start) * eased
    }

    fun isRunning(now: Long): Boolean = duration > 0L && now - startedAt < duration

    private fun snap(value: Float) { start = value; target = value; duration = 0L }
}
