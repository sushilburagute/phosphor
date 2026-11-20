package dev.sush.phosphor.platform

import android.view.Surface
import dev.sush.phosphor.model.WallpaperSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WallpaperTiltTest {
    private val tilt = WallpaperTilt(RuntimeEnvironment.getApplication())

    @Test
    fun tiltIsRelativeToRestingAngleAndIgnoresShakes() {
        tilt.sample(0f, 9.8f, 0f, Surface.ROTATION_0, 0L)
        tilt.sample(3f, 9f, 2f, Surface.ROTATION_0, 70_000_000L)
        val x = tilt.x
        val y = tilt.y
        assertTrue(x < 0f && x > -1f)
        assertTrue(y < 0f && y > -1f)
        tilt.sample(-20f, 0f, 0f, Surface.ROTATION_0, 140_000_000L)
        assertEquals(x, tilt.x)
        assertEquals(y, tilt.y)
        tilt.stop()
        tilt.sample(3f, 9f, 2f, Surface.ROTATION_0, 210_000_000L)
        assertEquals(0f, tilt.x)
        assertEquals(0f, tilt.y)
    }

    @Test
    fun displayRotationRecalibratesAndMapsTiltToTheNewScreenAxes() {
        tilt.sample(0f, 0f, 9.8f, Surface.ROTATION_0, 0L)
        tilt.sample(3f, 0f, 9.3f, Surface.ROTATION_0, 70_000_000L)
        assertTrue(tilt.x < 0f)
        tilt.sample(0f, 0f, 9.8f, Surface.ROTATION_90, 140_000_000L)
        assertEquals(0f, tilt.x)
        tilt.sample(3f, 0f, 9.3f, Surface.ROTATION_90, 210_000_000L)
        assertEquals(0f, tilt.x)
        assertTrue(tilt.y > 0f)
    }

    @Test
    fun tiltRequiresOptInAndActiveMotionOutsidePowerSaving() {
        val normal = WallpaperPowerProfile(30, 1f, false)
        val enabled = WallpaperSettings(tiltParallax = true)
        assertTrue(enabled.allowsTilt(normal))
        assertFalse(WallpaperSettings().allowsTilt(normal))
        assertFalse(enabled.copy(speed = 0f).allowsTilt(normal))
        assertFalse(enabled.copy(parallax = false).allowsTilt(normal))
        assertFalse(enabled.copy(reducedMotion = true).allowsTilt(normal))
        assertFalse(enabled.allowsTilt(WallpaperPowerProfile(15, 0.45f, true)))
    }
}
