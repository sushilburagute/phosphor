package dev.sush.phosphor.render

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class WallpaperFrameSchedulerTest {
    private val looper = shadowOf(Looper.getMainLooper())
    private val draws = mutableListOf<Long>()
    private val scheduler = WallpaperFrameScheduler(Handler(Looper.getMainLooper())) {
        schedulerFrame()
    }

    private fun schedulerFrame() {
        scheduler.onFrame()
        draws += SystemClock.uptimeMillis()
    }

    @Test
    fun inputBurstsCoalesceWithoutBypassingTheFrameLimitOrPostponingTheFrame() {
        scheduler.request(15)
        looper.idle()
        val first = draws.single()
        repeat(200) { scheduler.request(15) }
        looper.idleFor(Duration.ofMillis(66))
        assertEquals(1, draws.size)
        scheduler.request(15)
        looper.idleFor(Duration.ofMillis(1))
        assertEquals(listOf(first, first + 67), draws)
        looper.idleFor(Duration.ofSeconds(1))
        assertEquals(2, draws.size)
    }

    @Test
    fun sixtyFpsDoesNotExceedTheRequestedRateThroughRounding() {
        scheduler.request(60)
        looper.idle()
        scheduler.request(60)
        looper.idleFor(Duration.ofMillis(16))
        assertEquals(1, draws.size)
        looper.idleFor(Duration.ofMillis(1))
        assertEquals(2, draws.size)
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
        looper.idle()
        scheduler.request(15)
        scheduler.stop()
        looper.idleFor(Duration.ofMillis(10))
        assertEquals(1, draws.size)
        scheduler.request(15)
        looper.idle()
        assertEquals(2, draws.size)
        looper.idleFor(Duration.ofSeconds(1))
        assertEquals(2, draws.size)
    }

    @Test
    fun slowRenderingDoesNotAddAnotherFullFrameInterval() {
        scheduler.onFrame()
        looper.idleFor(Duration.ofMillis(90))
        scheduler.request(30)
        looper.idle()
        assertEquals(1, draws.size)
    }

    @Test
    fun viewInvalidationIsReadyBeforeTheNextThirtyFpsVsync() {
        var invalidations = 0
        val viewFrames = WallpaperFrameScheduler(Handler(Looper.getMainLooper()), invalidateOnVsync = true) {
            invalidations++
        }
        viewFrames.onFrame()
        repeat(100) { viewFrames.request(30) }
        looper.idleFor(Duration.ofMillis(32))
        assertEquals(0, invalidations)
        looper.idleFor(Duration.ofMillis(1))
        assertEquals(1, invalidations)
    }
}
