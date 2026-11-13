package dev.sush.phosphor.platform

import dev.sush.phosphor.model.Appearance
import dev.sush.phosphor.model.WallpaperSettings
import dev.sush.phosphor.render.TouchField
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

    @Test
    fun duplicateLauncherTapsAreMergedAndPulsesExpire() {
        val touch = TouchField()
        touch.tap(0.5f, 0.5f, 1f)
        touch.tap(0.51f, 0.5f, 1.1f)  // the same gesture reported twice
        val frame = dev.sush.phosphor.render.Frame().apply { h = 2000f }
        touch.fill(frame, 1.2f)
        assertEquals(1, frame.pulses)
        assertTrue(touch.isActive(1.2f))
        assertFalse(touch.isActive(1f + TouchField.LIFE + 0.01f))
    }

    @Test
    fun releasedDragSettlesToRest() {
        val touch = TouchField()
        touch.press(0.2f, 0.3f)
        repeat(30) { touch.advance(it * 0.016f) }
        assertTrue(touch.drag > 0.9f)
        touch.release()
        repeat(400) { touch.advance(0.5f + it * 0.016f) }
        assertEquals(0f, touch.drag)
        assertFalse(touch.isActive(20f))
    }
}
