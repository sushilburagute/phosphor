package dev.sush.phosphor.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import dev.sush.phosphor.model.WallpaperSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageFilesTest {
    @Test
    fun validImageIsCopiedPrivatelyAndDecodedAtABoundedSize() {
        val context = RuntimeEnvironment.getApplication()
        val source = File(context.cacheDir, "large-image.png")
        val original = Bitmap.createBitmap(3000, 1000, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.MAGENTA)
        source.outputStream().use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
        original.recycle()

        // A bounds-only decode returns null by design; that must not count as an error.
        val path = importImage(context, Uri.fromFile(source))
        assertTrue(File(path).canonicalPath.startsWith(File(context.filesDir, "wallpapers").canonicalPath + File.separator))
        assertTrue(source.exists())
        val imported = requireNotNull(BitmapFactory.decodeFile(path))
        assertTrue(imported.width <= 2560 && imported.height <= 2560)
        assertEquals(Color.MAGENTA, imported.getPixel(imported.width / 2, imported.height / 2))
        imported.recycle()
    }

    @Test(expected = IOException::class)
    fun nonImageDocumentFailsCleanly() {
        val context = RuntimeEnvironment.getApplication()
        val source = File(context.cacheDir, "not-an-image.txt").apply { writeText("not an image") }
        importImage(context, Uri.fromFile(source))
    }

    @Test
    fun exportedStillIsAReadablePortraitPng() {
        val context = RuntimeEnvironment.getApplication()
        val target = File(context.cacheDir, "export.png")
        exportFrame(context, Uri.fromFile(target), WallpaperSettings(presetId = "glass-prism"))
        val bitmap = requireNotNull(BitmapFactory.decodeFile(target.absolutePath))
        assertEquals(1080, bitmap.width)
        assertEquals(2400, bitmap.height)
        assertEquals(255, Color.alpha(bitmap.getPixel(540, 1200)))
        assertNotEquals(bitmap.getPixel(100, 100), bitmap.getPixel(500, 1200))
        bitmap.recycle()
    }
}
