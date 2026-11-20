package dev.sush.phosphor.render

/**
 * Start at full density and skip the first two draws after a restart or tier change.
 * Two severe stalls, or a six-frame window using nearly the entire budget, reduce density.
 * Recovery requires eight seconds' worth of frames using less than half their budget.
 */
internal class LiveRenderQuality {
    private var tier = 0
    val scale: Float get() = SCALES[tier]

    private var previousStart = 0L
    private var previousDraw = 0L
    private var rate = 0
    private var warmup = 2
    private var windowFrames = 0
    private var costlyFrames = 0
    private var severeStalls = 0
    private var windowNanos = 0L
    private var spareFrames = 0

    fun reset() {
        tier = 0
        restartSampling()
    }

    fun restartSampling() {
        previousStart = 0L
        previousDraw = 0L
        warmup = 2
        spareFrames = 0
        clearWindow()
    }

    fun posted(start: Long, end: Long, fps: Int) {
        if (end <= start) return
        val target = fps.coerceIn(1, 60)
        if (rate != target) {
            rate = target
            restartSampling()
        }
        val interval = if (previousStart == 0L) 0L else start - previousStart
        val budget = 1_000_000_000L / rate
        // A slow preceding draw has already been counted against that frame.
        val gap = interval - (previousDraw - budget).coerceAtLeast(0L)
        previousStart = start
        previousDraw = end - start
        if (warmup > 0) {
            warmup--
            return
        }

        // Hardware canvas submission is asynchronous. Include persistent backpressure visible
        // between draws, while allowing for millisecond scheduling and display refresh cadence.
        val elapsed = maxOf(previousDraw, if (gap > budget * 3 / 2) gap else 0L)
        windowFrames++
        windowNanos += elapsed.coerceAtMost(budget * 8)
        if (elapsed > budget * 9 / 10) costlyFrames++
        if (elapsed > budget * 3) severeStalls++
        spareFrames = if (elapsed < budget / 2 && tier > 0) spareFrames + 1 else 0

        when {
            // Leave some room for the launcher to draw icons and handle a page swipe.
            severeStalls >= 2 || (windowFrames >= 6 && costlyFrames >= 2 && windowNanos > windowFrames * budget * 9 / 10) -> {
                tier = (tier + if (severeStalls >= 2) 2 else 1).coerceAtMost(SCALES.lastIndex)
                restartSampling()
            }
            spareFrames >= rate * 8 -> {
                tier--
                restartSampling()
            }
            windowFrames >= 6 -> clearWindow()
        }
    }

    private fun clearWindow() {
        windowFrames = 0
        costlyFrames = 0
        severeStalls = 0
        windowNanos = 0L
    }

    private companion object {
        val SCALES = floatArrayOf(1f, 0.72f, 0.48f, 0.30f, 0.18f, 0.10f)
    }
}
