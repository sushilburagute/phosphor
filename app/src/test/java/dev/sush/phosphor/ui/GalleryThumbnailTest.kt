package dev.sush.phosphor.ui

import androidx.compose.ui.unit.IntSize
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GalleryThumbnailTest {
    @Test
    fun limitsPixelCostAndPreservesThePlateShape() {
        assertEquals(IntSize(320, 640), thumbnailSize(540, 1080))
        assertEquals(IntSize(180, 360), thumbnailSize(180, 360))
    }

    @Test
    fun galleryRequestsShareFinishedFramesAndKeepDifferentLooksDistinct() = runBlocking {
        val request = ThumbnailRequest(WallpaperCatalog.presets.first(), WallpaperSettings(), IntSize(90, 180))
        val one = async { GalleryThumbnails.load(request) }
        val two = async { GalleryThumbnails.load(request) }
        val first = one.await()
        assertSame(first, two.await())
        assertSame(first, GalleryThumbnails.cached(request.copy()))
        for (different in listOf(
            request.copy(settings = request.settings.copy(darken = 0.8f)),
            request.copy(settings = request.settings.copy(reducedMotion = true)),
            request.copy(preset = WallpaperCatalog.find("ascii-signal")),
            request.copy(size = IntSize(180, 90)),
        )) {
            val frame = GalleryThumbnails.load(different)
            assertEquals(different.size, IntSize(frame.width, frame.height))
            assertNotSame(first, frame)
        }
        assertFalse(first.isRecycled)
    }
}
