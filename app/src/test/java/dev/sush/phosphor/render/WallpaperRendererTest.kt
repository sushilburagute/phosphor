package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperKind
import dev.sush.phosphor.model.WallpaperPreset
import dev.sush.phosphor.model.WallpaperSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Exercise actual Skia drawing, rather than Robolectric's legacy recording Canvas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperRendererTest {
    @Test
    fun everyFamilyProducesOpaqueArtworkWithVisibleVariation() {
        val sheet = Bitmap.createBitmap(620, 1600, Bitmap.Config.ARGB_8888)
        sheet.eraseColor(0xFF18212C.toInt())
        val sheetCanvas = Canvas(sheet)
        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 13f }
        for ((index, preset) in WallpaperCatalog.presets.withIndex()) {
            val bitmap = render(preset)
            val pixels = pixels(bitmap)
            assertTrue("${preset.id} contains transparent pixels", pixels.all { Color.alpha(it) == 255 })
            // Ignore the watermark area: the art itself must carry visible detail.
            val art = pixels.take(WIDTH * HEIGHT * 3 / 4)
            assertTrue("${preset.id} is effectively a solid fill", art.toSet().size > 80)
            if (preset.kind != WallpaperKind.IMAGE) {
                val x = 20f + (index % 3) * 200f
                val y = 12f + (index / 3) * 400f
                sheetCanvas.drawBitmap(bitmap, x, y, null)
                sheetCanvas.drawText(preset.name, x, y + HEIGHT + 22f, caption)
            }
            bitmap.recycle()
        }
        val report = File("build/reports/phosphor-artwork.png")
        report.parentFile?.mkdirs()
        FileOutputStream(report).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sheet.recycle()
    }

    @Test
    fun everyFamilyChangesWhenAnimationTimeAdvances() {
        for (preset in WallpaperCatalog.presets) {
            val first = render(preset, time = 0f)
            val later = render(preset, time = 19f)
            assertTrue("${preset.id} did not animate", changedFraction(first, later) > 0.02f)
            first.recycle()
            later.recycle()
        }
    }

    @Test
    fun reducedMotionFreezesTimeAndParallaxForEveryFamily() {
        for (preset in WallpaperCatalog.presets) {
            val settings = WallpaperSettings(presetId = preset.id, reducedMotion = true)
            val first = render(preset, settings, 0f, -1f, -1f)
            val later = render(preset, settings, 91f, 1f, 1f)
            assertTrue("${preset.id} moves with reduced motion", first.sameAs(later))
            first.recycle()
            later.recycle()
        }
    }

    @Test
    fun everyFamilyRespondsToATapEvenWhenAmbientSpeedIsZero() {
        for (preset in WallpaperCatalog.presets) {
            val settings = WallpaperSettings(presetId = preset.id, speed = 0f)
            val first = render(preset, settings)
            val touched = render(preset, settings, interaction = WallpaperInteraction(
                x = 0.4f, y = 0.45f, strength = 0.8f, rippleProgress = 0.3f, offsetX = 0.4f,
            ))
            assertTrue("${preset.id} ignores touch at zero speed", changedFraction(first, touched) > 0.02f)
            first.recycle()
            touched.recycle()
        }
    }

    @Test
    fun reducedMotionSuppressesTouchEffectsAndLockReveal() {
        val preset = WallpaperCatalog.find("aurora-veil")
        val settings = WallpaperSettings(reducedMotion = true)
        val first = render(preset, settings)
        val touched = render(preset, settings, interaction = WallpaperInteraction(strength = 1f, rippleProgress = 0.2f), reveal = 0f)
        assertTrue(first.sameAs(touched))
        first.recycle()
        touched.recycle()
    }

    @Test
    fun lockTreatmentPreservesOpaqueArtworkAndCanBeDisabled() {
        val preset = WallpaperCatalog.find("gradient-tide")
        val awake = render(preset)
        val locked = render(preset, reveal = 0f)
        assertTrue(changedFraction(awake, locked) > 0.1f)
        assertTrue(pixels(locked).all { Color.alpha(it) == 255 })
        val disabled = render(preset, WallpaperSettings(presetId = preset.id, unlockAnimation = false), reveal = 0f)
        assertTrue(awake.sameAs(disabled))
        awake.recycle()
        locked.recycle()
        disabled.recycle()
    }

    @Test
    fun missingImageUsesTheSameSafeFallbackAsAnEmptyImport() {
        val image = WallpaperCatalog.find("image-custom")
        val absent = render(image, WallpaperSettings(imagePath = "/not-a-real-phosphor-image.png"))
        val empty = render(image, WallpaperSettings(imagePath = null))
        assertTrue(absent.sameAs(empty))
        absent.recycle()
        empty.recycle()
    }

    @Test
    fun importedImageCoversLandscapeEvenWithMaximumDriftAndParallax() {
        val source = Bitmap.createBitmap(64, 256, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.RED)
        val imageFile = File.createTempFile("phosphor-render-test-", ".png")
        try {
            FileOutputStream(imageFile).use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val bitmap = Bitmap.createBitmap(360, 150, Bitmap.Config.ARGB_8888)
            val renderer = WallpaperRenderer()
            try {
                renderer.draw(
                    Canvas(bitmap), bitmap.width, bitmap.height, 15f,
                    WallpaperCatalog.find("image-custom"),
                    WallpaperSettings(imagePath = imageFile.absolutePath, intensity = 1f),
                    offsetX = 1f, offsetY = -1f,
                    interaction = WallpaperInteraction(offsetX = 1f, offsetY = -1f),
                )
                // A tall image must still cover all four corners of a wide surface.
                for ((x, y) in listOf(0 to 0, 359 to 0, 0 to 149, 359 to 149)) {
                    assertEquals("An uncovered wallpaper edge at $x,$y", Color.RED, bitmap.getPixel(x, y))
                }
            } finally {
                renderer.release()
                bitmap.recycle()
            }
        } finally {
            source.recycle()
            imageFile.delete()
        }
    }

    @Test
    fun releaseAllowsTheSameRendererToBeUsedAgain() {
        val renderer = WallpaperRenderer()
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val preset = WallpaperCatalog.presets.first { it.kind == WallpaperKind.TEXTURE }
        try {
            renderer.draw(Canvas(bitmap), WIDTH, HEIGHT, 12f, preset, WallpaperSettings())
            val first = pixels(bitmap)
            renderer.release()
            bitmap.eraseColor(Color.TRANSPARENT)
            renderer.draw(Canvas(bitmap), WIDTH, HEIGHT, 12f, preset, WallpaperSettings())
            assertTrue(first.contentEquals(pixels(bitmap)))
        } finally {
            renderer.release()
            bitmap.recycle()
        }
    }

    @Test
    fun darkeningPreservesOpacityAndLowersArtworkLuminance() {
        val preset = WallpaperCatalog.find("gradient-daybreak")
        val light = render(preset, WallpaperSettings(darken = 0f))
        val dark = render(preset, WallpaperSettings(darken = 1f))
        val x = WIDTH / 2
        val y = HEIGHT / 2
        assertTrue(Color.red(dark.getPixel(x, y)) < Color.red(light.getPixel(x, y)))
        assertTrue(Color.green(dark.getPixel(x, y)) < Color.green(light.getPixel(x, y)))
        assertEquals(255, Color.alpha(dark.getPixel(x, y)))
        assertFalse(light.sameAs(dark))
        light.recycle()
        dark.recycle()
    }

    private fun render(
        preset: WallpaperPreset,
        settings: WallpaperSettings = WallpaperSettings(presetId = preset.id),
        time: Float = 12f,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        interaction: WallpaperInteraction = WallpaperInteraction(),
        reveal: Float = 1f,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val renderer = WallpaperRenderer()
        try {
            renderer.draw(Canvas(bitmap), WIDTH, HEIGHT, time, preset, settings, offsetX, offsetY,
                interaction = interaction, reveal = reveal)
        } finally {
            renderer.release()
        }
        return bitmap
    }

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun changedFraction(first: Bitmap, second: Bitmap): Float {
        val a = pixels(first)
        val b = pixels(second)
        return a.indices.count { a[it] != b[it] }.toFloat() / a.size
    }

    companion object {
        private const val WIDTH = 180
        private const val HEIGHT = 360
    }
}
