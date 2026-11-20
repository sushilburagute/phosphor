package dev.sush.phosphor.platform

/** Eases launcher offsets on the wallpaper's existing frame clock. */
internal class WallpaperPageMotion {
    var x = 0f
        private set
    var y = 0f
        private set
    private var fromX = 0f
    private var fromY = 0f
    private var targetX = 0f
    private var targetY = 0f
    private var start = -1L
    private var initialized = false
    val moving: Boolean get() = start >= 0L

    fun moveTo(nextX: Float, nextY: Float, now: Long, animate: Boolean): Boolean {
        if (initialized && nextX == targetX && nextY == targetY) return false
        advance(now)
        fromX = x
        fromY = y
        targetX = nextX
        targetY = nextY
        if (initialized && animate) start = now else finish()
        initialized = true
        return true
    }

    fun advance(now: Long) {
        if (!moving) return
        val progress = ((now - start) / 150f).coerceIn(0f, 1f)
        if (progress == 1f) {
            finish()
            return
        }
        val remaining = 1f - progress
        val blend = 1f - remaining * remaining * remaining
        x = fromX + (targetX - fromX) * blend
        y = fromY + (targetY - fromY) * blend
    }

    fun finish() {
        x = targetX
        y = targetY
        start = -1L
    }
}
