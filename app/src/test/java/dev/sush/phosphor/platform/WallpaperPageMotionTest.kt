package dev.sush.phosphor.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperPageMotionTest {
    @Test
    fun firstOffsetIsImmediateAndASwipeSettlesEvenAtZeroAmbientSpeed() {
        val page = WallpaperPageMotion()
        page.moveTo(1f, -1f, 0L, animate = true)
        assertEquals(1f, page.x, 0f)
        assertFalse(page.moving)
        page.moveTo(-1f, 1f, 10L, animate = true)
        page.advance(85L)
        assertTrue(page.x > -1f && page.x < 0f)
        assertEquals(-page.x, page.y, 0.0001f)
        assertFalse(page.moveTo(-1f, 1f, 120L, animate = true))
        page.advance(160L)
        assertEquals(-1f, page.x, 0f)
        assertFalse(page.moving)
    }

    @Test
    fun reversingBetweenFramesStartsFromTheCurrentPositionAndCanBeStopped() {
        val page = WallpaperPageMotion()
        page.moveTo(0f, 0f, 0L, animate = false)
        page.moveTo(1f, 0f, 10L, animate = true)
        page.moveTo(-1f, 0f, 85L, animate = true)
        assertEquals(0.875f, page.x, 0.0001f)
        page.advance(95L)
        assertTrue(page.x < 0.875f)
        page.finish()
        assertEquals(-1f, page.x, 0f)
        assertFalse(page.moving)
        page.moveTo(0.5f, 1f, 300L, animate = false)
        assertEquals(0.5f, page.x, 0f)
        assertFalse(page.moving)
    }
}
