package dev.sush.phosphor.platform

import android.content.Intent
import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WallpaperBatteryStateTest {
    @Test
    fun missingOrInvalidBatteryValuesStayUnknown() {
        assertNull(WallpaperPowerPolicy.fromIntent(null).percent)
        assertNull(WallpaperPowerPolicy.fromIntent(Intent(Intent.ACTION_BATTERY_CHANGED)).percent)
        assertNull(WallpaperPowerPolicy.fromIntent(batteryIntent(12, 0)).percent)
    }

    @Test
    fun scaledReadingsAndPluggedStateArePreserved() {
        val intent = batteryIntent(40, 200).putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
        val state = WallpaperPowerPolicy.fromIntent(intent)
        assertEquals(20, state.percent)
        assertTrue(state.charging)
    }

    @Test
    fun anUnpluggedFullBatteryDoesNotMasqueradeAsCharging() {
        val intent = batteryIntent(100, 100).putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_FULL)
        assertFalse(WallpaperPowerPolicy.fromIntent(intent).charging)
        intent.putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
        assertTrue(WallpaperPowerPolicy.fromIntent(intent).charging)
    }

    private fun batteryIntent(level: Int, scale: Int) = Intent(Intent.ACTION_BATTERY_CHANGED)
        .putExtra(BatteryManager.EXTRA_LEVEL, level)
        .putExtra(BatteryManager.EXTRA_SCALE, scale)
}
