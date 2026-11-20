package dev.sush.phosphor.render

import android.os.Looper
import android.os.SystemClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class WallpaperFrameSchedulerTest {
    private val looper = shadowOf(Looper.getMainLooper())
    private val draws = mutableListOf<Long>()
    private val scheduler = WallpaperFrameScheduler {
        schedulerFrame()
    }

    @Before
    fun configureVsync() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(1))
    }

    private fun schedulerFrame() {
        scheduler.onFrame()
        draws += SystemClock.uptimeMillis()
    }

    @Test
    fun inputBurstsCoalesceWithoutBypassingTheFrameLimitOrPostponingTheFrame() {
        scheduler.request(15)
        looper.idleFor(Duration.ofMillis(1))
        val first = draws.single()
        repeat(200) { scheduler.request(15) }
        looper.idleFor(Duration.ofMillis(65))
        assertEquals(1, draws.size)
        scheduler.request(15)
        looper.idleFor(Duration.ofMillis(3))
        assertEquals(2, draws.size)
        assertTrue(draws.last() - first in 66L..68L)
        looper.idleFor(Duration.ofSeconds(1))
        assertEquals(2, draws.size)
    }

    @Test
    fun sixtyFpsDoesNotAccumulateMillisecondRoundingError() {
        scheduler.request(60)
        looper.idleFor(Duration.ofMillis(1))
        repeat(1000) {
            scheduler.request(60)
            looper.idleFor(Duration.ofMillis(1))
        }
        assertEquals(61, draws.size)
        assertTrue(draws.zipWithNext().all { (a, b) -> b - a in 16L..17L })
    }

    @Test
    fun frameworkRedrawConsumesThePendingCallback() {
        scheduler.request(30)
        scheduler.onFrame()
        looper.idleFor(Duration.ofSeconds(1))
        assertEquals(0, draws.size)
    }

    @Test
    fun hidingCancelsFramesAndResumingDoesNotWaitForTheOldDeadline() {
        scheduler.request(15)
        looper.idleFor(Duration.ofMillis(1))
        scheduler.request(15)
        scheduler.stop()
        looper.idleFor(Duration.ofMillis(10))
        assertEquals(1, draws.size)
        scheduler.request(15)
        looper.idleFor(Duration.ofMillis(1))
        assertEquals(2, draws.size)
        looper.idleFor(Duration.ofSeconds(1))
        assertEquals(2, draws.size)
    }

    @Test
    fun slowRenderingDoesNotAddAnotherFullFrameInterval() {
        scheduler.onFrame()
        looper.idleFor(Duration.ofMillis(90))
        scheduler.request(30)
        looper.idleFor(Duration.ofMillis(1))
        assertEquals(1, draws.size)
    }

    @Test
    fun aLateFrameDoesNotTriggerCatchUpDraws() {
        scheduler.request(30)
        looper.idleFor(Duration.ofMillis(1))
        assertEquals(1, draws.size)
        looper.idleFor(Duration.ofMillis(100))
        scheduler.request(30)
        looper.idleFor(Duration.ofMillis(1))
        assertEquals(2, draws.size)
        scheduler.request(30)
        looper.idleFor(Duration.ofMillis(30))
        assertEquals(2, draws.size)
    }
}
