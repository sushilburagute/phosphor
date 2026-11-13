package dev.sush.phosphor.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperMotionTest {
    @Test
    fun releaseEasesBackAndEventuallyStopsRequestingFrames() {
        val motion = WallpaperMotion()
        motion.onTouch(0, 0.95f, 0.1f)
        repeat(30) { motion.advance(1f / 60f) }
        val held = motion.advance(0f)
        motion.onTouch(1, 0.95f, 0.1f)
        val released = motion.advance(1f / 60f)
        assertTrue(released.offsetX > 0f)
        assertTrue(released.offsetX < held.offsetX)
        assertTrue(released.strength > 0f)
        assertTrue(motion.isActive)
        repeat(240) { motion.advance(1f / 60f) }
        assertFalse(motion.isActive)
        assertEquals(0f, motion.advance(0f).offsetX, 0f)
    }

    @Test
    fun aQuickTapProducesFeedbackEvenWithoutAnAmbientAnimationClock() {
        val motion = WallpaperMotion()
        motion.onTouch(0, 0.25f, 0.75f)
        motion.onTouch(1, 0.25f, 0.75f)
        val tap = motion.advance(0f)
        assertEquals(0.25f, tap.x, 0f)
        assertEquals(0.75f, tap.y, 0f)
        assertEquals(0f, tap.rippleProgress, 0f)
        assertTrue(motion.isActive)
    }

    @Test
    fun lowFrameRatePreservesTheSpringResponse() {
        fun sample(fps: Int): WallpaperInteraction {
            val motion = WallpaperMotion()
            motion.onTouch(0, 0.9f, 0.1f)
            repeat(fps / 3) { motion.advance(1f / fps) }
            return motion.advance(0f)
        }
        val fast = sample(60)
        val slow = sample(15)
        assertEquals(fast.offsetX, slow.offsetX, 0.0001f)
        assertEquals(fast.offsetY, slow.offsetY, 0.0001f)
        assertEquals(fast.strength, slow.strength, 0.0001f)
    }

    @Test
    fun interruptedGestureRetargetsWithoutSnappingAndResetClearsEverything() {
        val motion = WallpaperMotion()
        motion.onTouch(0, 1f, 1f)
        repeat(20) { motion.advance(1f / 60f) }
        val before = motion.advance(0f)
        motion.onTouch(2, 0f, 0f)
        assertEquals(before.offsetX, motion.advance(0f).offsetX, 0f)
        motion.reset()
        assertFalse(motion.isActive)
        assertEquals(WallpaperInteraction(), motion.advance(0f))
    }

    @Test
    fun invalidInputAndLongGapsNeverPoisonTheState() {
        val motion = WallpaperMotion()
        motion.onTouch(0, Float.NaN, Float.POSITIVE_INFINITY)
        val state = motion.advance(Float.NaN)
        assertTrue(state.x.isFinite() && state.y.isFinite())
        assertTrue(state.offsetX.isFinite() && state.offsetY.isFinite())
        motion.onTouch(1, 0f, 0f)
        assertTrue(motion.advance(10_000f).rippleProgress < 1f)
    }
}
