package dev.sush.phosphor.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.BatteryManager
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.widget.FrameLayout
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class WallpaperPreviewTest {
    @Test
    fun galleryDrawsReuseABitmapUntilTheArtworkOrSizeChanges() = withPreview { view, _ ->
        val canvas = BitmapRecordingCanvas()
        val preset = WallpaperCatalog.presets.first()
        view.update(preset, WallpaperSettings(), false, cacheStill = true)
        view.layout(0, 0, 180, 360)
        view.draw(canvas)
        val first = checkNotNull(canvas.drawnBitmap)
        view.draw(canvas)
        assertSame(first, canvas.drawnBitmap)

        view.update(preset, WallpaperSettings(darken = 0.8f), false, cacheStill = true)
        view.draw(canvas)
        val changed = checkNotNull(canvas.drawnBitmap)
        assertNotSame(first, changed)
        assertFalse(first.isRecycled)

        view.update(preset, WallpaperSettings(darken = 0.8f), false, night = false, cacheStill = true)
        view.draw(canvas)
        val day = checkNotNull(canvas.drawnBitmap)
        assertNotSame(changed, day)

        view.layout(0, 0, 360, 180)
        view.draw(canvas)
        assertNotSame(day, canvas.drawnBitmap)
        assertEquals(360, canvas.drawnBitmap!!.width)
        assertEquals(180, canvas.drawnBitmap!!.height)
    }

    @Test
    fun hidingAnAncestorStopsPendingFramesAndObserversAndShowingItResumes() = withPreview { view, parent ->
        draw(view)
        assertTrue(hasBatteryReceiver(view))
        parent.visibility = View.INVISIBLE
        idle()
        view.invalidations = 0
        idle()
        assertTrue(view.invalidations == 0)
        assertFalse(hasBatteryReceiver(view))

        parent.visibility = View.VISIBLE
        idle()
        assertTrue(view.invalidations > 0)
        assertTrue(hasBatteryReceiver(view))
    }

    @Test
    fun screenOffCancelsPendingFramesEvenIfTheWindowRemainsVisible() = withPreview { view, _ ->
        draw(view)
        val power = view.context.getSystemService(PowerManager::class.java)
        shadowOf(power).turnScreenOn(false)
        shadowOf(Looper.getMainLooper()).idle()
        view.invalidations = 0
        idle()
        assertTrue(view.invalidations == 0)

        shadowOf(power).turnScreenOn(true)
        idle()
        assertTrue(view.invalidations > 0)
    }

    @Test
    fun batteryBroadcastsOnlyRedrawWhenTheEffectivePolicyChanges() = withPreview { view, _ ->
        view.update(WallpaperCatalog.presets.first(), WallpaperSettings(speed = 0f), true)
        idle()
        draw(view)
        view.invalidations = 0
        battery(view, 80)
        battery(view, 79)
        assertTrue(view.invalidations == 0)
        battery(view, 20)
        assertTrue(view.invalidations > 0)
        draw(view)
        view.invalidations = 0
        battery(view, 19)
        assertTrue(view.invalidations == 0)
        battery(view, 5)
        assertTrue(view.invalidations > 0)
    }

    @Test
    fun pausedAndStillPreviewsDoNotScheduleAnotherFrame() = withPreview { view, _ ->
        for ((settings, playing) in listOf(WallpaperSettings() to false, WallpaperSettings(reducedMotion = true) to true)) {
            view.update(WallpaperCatalog.presets.first(), settings, playing)
            idle()
            draw(view)
            view.invalidations = 0
            idle()
            assertTrue(view.invalidations == 0)
        }
    }

    @Test
    fun detachingDropsObserversAndPendingFrames() = withPreview { view, parent ->
        draw(view)
        parent.removeView(view)
        view.invalidations = 0
        idle()
        assertTrue(view.invalidations == 0)
        assertFalse(hasBatteryReceiver(view))
    }

    private fun withPreview(check: (CountingPreviewView, FrameLayout) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).create().start().resume()
        val activity = controller.get()
        val parent = FrameLayout(activity)
        val view = CountingPreviewView(activity)
        try {
            view.update(WallpaperCatalog.presets.first(), WallpaperSettings(), true)
            parent.addView(view, FrameLayout.LayoutParams(180, 360))
            activity.setContentView(parent)
            controller.visible().windowFocusChanged(true)
            // Robolectric's API 35 window remains GONE despite ActivityController.visible().
            // Supply the platform visibility event before exercising the real View lifecycle.
            ReflectionHelpers.setField(ReflectionHelpers.getField<Any>(view, "mAttachInfo"), "mWindowVisibility", View.VISIBLE)
            view.onVisibilityAggregated(true)
            idle()
            assertTrue("Preview must be attached", view.isAttachedToWindow)
            assertTrue("Preview must be shown (window=${view.windowVisibility})", view.isShown)
            assertEquals("Preview window must be visible", View.VISIBLE, view.windowVisibility)
            check(view, parent)
        } finally {
            parent.removeAllViews()
            view.dispose()
            controller.pause().stop().destroy()
        }
    }

    private fun draw(view: PreviewView) {
        view.layout(0, 0, 180, 360)
        val bitmap = Bitmap.createBitmap(180, 360, Bitmap.Config.ARGB_8888)
        try { view.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
    }

    private fun hasBatteryReceiver(view: View): Boolean =
        shadowOf(view.context.applicationContext as Application).registeredReceivers.any {
            it.intentFilter.hasAction(Intent.ACTION_BATTERY_CHANGED)
        }

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))

    private fun battery(view: View, percent: Int) {
        view.context.sendBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, percent)
            .putExtra(BatteryManager.EXTRA_SCALE, 100)
            .putExtra(BatteryManager.EXTRA_PLUGGED, 0))
        idle()
    }

    private class CountingPreviewView(context: Context) : PreviewView(context) {
        var invalidations = 0

        override fun invalidate() {
            invalidations++
            super.invalidate()
        }
    }

    private class BitmapRecordingCanvas : Canvas() {
        var drawnBitmap: Bitmap? = null

        override fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {
            this.drawnBitmap = bitmap
        }
    }
}
