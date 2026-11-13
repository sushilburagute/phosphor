package dev.sush.phosphor.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperRevealTest {
    @Test
    fun lockAndUnlockFinishAtBoundedStates() {
        val reveal = WallpaperReveal()
        reveal.transitionTo(locked = true, animate = true, now = 1000L)
        assertEquals(1f, reveal.value(1000L))
        assertEquals(0.5f, reveal.value(1140L), 0.001f)
        assertEquals(0f, reveal.value(1280L))
        assertFalse(reveal.isRunning(1280L))

        reveal.transitionTo(locked = false, animate = true, now = 2000L)
        assertEquals(0.5f, reveal.value(2300L), 0.001f)
        assertEquals(1f, reveal.value(2600L))
        assertFalse(reveal.isRunning(2600L))
    }

    @Test
    fun repeatedLifecycleCallbacksDoNotRestartReveal() {
        val reveal = WallpaperReveal()
        reveal.transitionTo(locked = true, animate = false, now = 1000L)
        reveal.transitionTo(locked = false, animate = true, now = 2000L)
        reveal.transitionTo(locked = false, animate = true, now = 2300L)
        assertEquals(0.5f, reveal.value(2300L), 0.001f)
        assertEquals(1f, reveal.value(2600L))
    }

    @Test
    fun wakeCanSettleLockedArtworkAndInterruptSmoothlyOnFastUnlock() {
        val reveal = WallpaperReveal()
        reveal.transitionTo(locked = true, animate = false, now = 1000L)
        reveal.transitionTo(locked = true, animate = true, now = 2000L, wake = true)
        assertTrue(reveal.isRunning(2100L))
        val beforeUnlock = reveal.value(2100L)
        reveal.transitionTo(locked = false, animate = true, now = 2100L)
        assertEquals(beforeUnlock, reveal.value(2100L))
        assertEquals(1f, reveal.value(2700L))
    }

    @Test
    fun disabledAnimationSettlesImmediatelyEvenMidTransition() {
        val reveal = WallpaperReveal()
        reveal.transitionTo(locked = true, animate = true, now = 1000L)
        reveal.transitionTo(locked = true, animate = false, now = 1100L)
        assertEquals(0f, reveal.value(1100L))
        assertFalse(reveal.isRunning(1100L))
        reveal.transitionTo(locked = false, animate = false, now = 1200L)
        assertEquals(1f, reveal.value(1200L))
    }
}
