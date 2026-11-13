package dev.sush.phosphor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Pure color math: no Android types, so this runs on the plain JVM. */
class ColorTheoryTest {
    @Test
    fun oklchRoundTripsThroughSrgb() {
        for (color in listOf(0xFF2E47C7.toInt(), 0xFFFF6A3A.toInt(), 0xFF5CF2B4.toInt(), 0xFF808080.toInt())) {
            val (l, c, h) = ColorTheory.toOklch(color).let { Triple(it[0], it[1], it[2]) }
            assertEquals(Integer.toHexString(color), color, ColorTheory.oklch(l, c, h))
        }
    }

    @Test
    fun outOfGamutChromaIsReducedWithoutShiftingLightness() {
        val color = ColorTheory.oklch(0.9f, 0.4f, 145f)
        val lch = ColorTheory.toOklch(color)
        assertEquals(0.9f, lch[0], 0.01f)
        assertTrue("chroma should be clipped into sRGB", lch[1] < 0.4f)
        assertEquals(145f, lch[2], 4f)
    }

    @Test
    fun harmoniesPlaceTheAccentWhereTheyClaim() {
        for (harmony in Harmony.entries) {
            val scheme = Scheme(250f, harmony)
            val expected = (250f + harmony.offset) % 360f
            val accent = ColorTheory.toOklch(scheme.night()[2])[2]
            if (harmony != Harmony.MONOCHROME) assertTrue("$harmony accent at $accent, wanted $expected", hueDistance(accent, expected) < 6f)
        }
        assertEquals(10f, Scheme(50f, Harmony.ANALOGOUS, clockwise = false).accentHue, 0.001f)
    }

    /** Night grounds stay deep enough for white labels and day paper pale enough for dark ones. */
    @Test
    fun everyPresetKeepsItsGroundsReadable() {
        for (preset in WallpaperCatalog.presets) {
            assertTrue("${preset.id} night ground too light", ColorTheory.toOklch(preset.colors[0])[0] < 0.3f)
            assertTrue("${preset.id} day ground too dark", ColorTheory.toOklch(preset.dayColors[0])[0] > 0.9f)
            // Accents must stand apart from their ground in both treatments.
            for (accent in 1..2) {
                assertTrue(ColorTheory.toOklch(preset.colors[accent])[0] - ColorTheory.toOklch(preset.colors[0])[0] > 0.4f)
                assertTrue(ColorTheory.toOklch(preset.dayColors[0])[0] - ColorTheory.toOklch(preset.dayColors[accent])[0] > 0.15f)
            }
        }
    }

    @Test
    fun pigmentLiftsInkToALuminousPastelOfTheSameHue() {
        val ink = ColorTheory.oklch(0.47f, 0.16f, 262f)
        val pigment = ColorTheory.toOklch(ColorTheory.pigment(ink, 0.8f))
        assertEquals(0.8f, pigment[0], 0.01f)
        assertTrue(pigment[1] > 0.08f)
        assertTrue(hueDistance(pigment[2], 262f) < 5f)
    }

    private fun hueDistance(a: Float, b: Float): Float = abs(((a - b + 540f) % 360f) - 180f)
}
