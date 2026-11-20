package dev.sush.phosphor.platform

import dev.sush.phosphor.model.Appearance
import dev.sush.phosphor.model.WallpaperSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerPolicyTest {
    private val settings = WallpaperSettings(fps = 60)

    @Test
    fun pluggedInKeepsTheChosenBudgetEvenWhenLow() {
        assertEquals(FrameBudget(60, 1f, false), PowerPolicy.budget(settings, PowerState(level = 3, charging = true)))
    }

    @Test
    fun lowBatteryUnpluggedCapsFrameRateAndSlowsMotion() {
        val budget = PowerPolicy.budget(settings, PowerState(level = 20, charging = false))
        assertEquals(15, budget.fps)
        assertEquals(0.5f, budget.motion)
        assertFalse(budget.still)
    }

    @Test
    fun criticalBatteryHoldsStill() {
        assertTrue(PowerPolicy.budget(settings, PowerState(level = 5, charging = false)).still)
    }

    @Test
    fun optingOutOfLowBatteryModeKeepsMotionButNotAgainstBatterySaver() {
        val optedOut = settings.copy(lowBatteryMode = false)
        assertEquals(FrameBudget(60, 1f, false), PowerPolicy.budget(optedOut, PowerState(level = 4, charging = false)))
        assertEquals(15, PowerPolicy.budget(optedOut, PowerState(level = 90, charging = false, batterySaver = true)).fps)
    }

    @Test
    fun stillModeIsAlwaysStill() {
        assertTrue(PowerPolicy.budget(settings.copy(reducedMotion = true), PowerState()).still)
    }

    @Test
    fun appearanceResolvesAgainstTheSystem() {
        assertTrue(Appearance.AUTO.isNight(systemNight = true))
        assertFalse(Appearance.AUTO.isNight(systemNight = false))
        assertTrue(Appearance.NIGHT.isNight(systemNight = false))
        assertFalse(Appearance.DAY.isNight(systemNight = true))
    }
}
