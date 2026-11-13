package dev.sush.phosphor.platform

import dev.sush.phosphor.model.WallpaperSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperPowerPolicyTest {
    private val settings = WallpaperSettings(fps = 60)

    @Test
    fun automaticModeUsesBatteryBoundaryAndStopsAtCriticalLevel() {
        assertFalse(profile(21).energySaving)
        assertTrue(profile(20).energySaving)
        assertEquals(15, profile(20).fps)
        assertEquals(0.45f, profile(6).motionScale)
        assertEquals(0f, profile(5).motionScale)
        assertEquals(0f, profile(0).motionScale)
    }

    @Test
    fun chargingRestoresChosenRateButNeverOverridesSystemSaver() {
        val charged = WallpaperPowerPolicy.resolve(settings, false, 2, true)
        assertEquals(60, charged.fps)
        assertEquals(1f, charged.motionScale)
        val saver = WallpaperPowerPolicy.resolve(settings, true, 2, true)
        assertEquals(15, saver.fps)
        assertTrue(saver.energySaving)
    }

    @Test
    fun automaticModeCanBeDisabledWhileSystemSaverStillWins() {
        val optedOut = settings.copy(lowBatteryMode = false)
        assertEquals(60, WallpaperPowerPolicy.resolve(optedOut, false, 2, false).fps)
        assertEquals(15, WallpaperPowerPolicy.resolve(optedOut, true, 95, false).fps)
    }

    @Test
    fun unknownBatteryDoesNotFreezeWallpaperAndInvalidFpsIsSafe() {
        assertEquals(1f, profile(null).motionScale)
        assertEquals(15, WallpaperPowerPolicy.resolve(settings.copy(fps = 0), false, null, false).fps)
        assertEquals(60, WallpaperPowerPolicy.resolve(settings.copy(fps = 200), false, null, false).fps)
    }

    @Test
    fun lockScreenCapsRenderingAndUnlockRestoresTheSelectedRate() {
        val locked = WallpaperPowerPolicy.resolve(settings, false, 90, false, locked = true)
        assertEquals(15, locked.fps)
        assertEquals(1f, locked.motionScale)
        assertFalse(locked.energySaving)
        assertEquals(60, WallpaperPowerPolicy.resolve(settings, false, 90, false, locked = false).fps)
        assertEquals(15, WallpaperPowerPolicy.resolve(settings, false, 90, true, locked = true).fps)
    }

    @Test
    fun lockScreenNeverOverridesCriticalBatteryHoldOrSystemSaver() {
        assertEquals(0f, WallpaperPowerPolicy.resolve(settings, false, 5, false, locked = true).motionScale)
        val saver = WallpaperPowerPolicy.resolve(settings, true, 90, true, locked = true)
        assertEquals(15, saver.fps)
        assertEquals(0.45f, saver.motionScale)
        assertTrue(saver.energySaving)
    }

    private fun profile(percent: Int?) = WallpaperPowerPolicy.resolve(settings, false, percent, false)
}
