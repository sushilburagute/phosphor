package dev.sush.phosphor.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRenderQualityTest {
    private val quality = LiveRenderQuality()
    private val budget = 1_000_000_000L / 30
    private var now = budget

    private fun frame(draw: Long, dispatchDelay: Long = 0L) {
        quality.posted(now, now + draw, 30)
        now += maxOf(budget, draw) + dispatchDelay
    }

    @Test
    fun startupAndAnIsolatedSlowFrameKeepFullDetail() {
        repeat(2) { frame(budget * 5) }
        repeat(15) { frame(budget / 2) }
        frame(budget * 5)
        repeat(15) { frame(budget / 2) }
        assertEquals(1f, quality.scale)
    }

    @Test
    fun sustainedMissesReduceDensityAndRecoveryNeedsSustainedHeadroom() {
        repeat(8) { frame(budget * 2) }
        val lower = quality.scale
        assertTrue(lower < 1f)
        repeat(220) { frame(budget / 4) }
        assertEquals(lower, quality.scale)
        repeat(40) { frame(budget / 4) }
        assertEquals(1f, quality.scale)
    }

    @Test
    fun repeatedBackpressureReducesDetailEvenWithFastCanvasSubmission() {
        repeat(80) { frame(budget / 4, dispatchDelay = budget * 4) }
        assertEquals(0.1f, quality.scale)
        quality.restartSampling()
        now += 90_000_000_000L
        repeat(3) { frame(budget / 4) }
        assertEquals(0.1f, quality.scale)
        quality.reset()
        assertEquals(1f, quality.scale)
    }
}
