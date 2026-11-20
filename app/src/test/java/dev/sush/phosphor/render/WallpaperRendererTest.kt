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
import kotlin.math.abs

/** Exercise actual Skia drawing, rather than Robolectric's legacy recording Canvas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperRendererTest {
    @Test
    fun everyFamilyProducesOpaqueArtworkWithVisibleVariation() {
        val presets = WallpaperCatalog.presets.filter { it.kind != WallpaperKind.IMAGE }
        val columns = 6
        val sheet = Bitmap.createBitmap(columns * (WIDTH + 12) + 12, (presets.size + columns - 1) / columns * (HEIGHT + 34) + 12, Bitmap.Config.ARGB_8888)
        sheet.eraseColor(0xFF18212C.toInt())
        val sheetCanvas = Canvas(sheet)
        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 13f }
        for (preset in WallpaperCatalog.presets) {
            val bitmap = render(preset)
            val pixels = pixels(bitmap)
            assertTrue("${preset.id} contains transparent pixels", pixels.all { Color.alpha(it) == 255 })
            assertTrue("${preset.id} is effectively a solid fill", pixels.toSet().size > 80)
            val slot = presets.indexOf(preset)
            if (slot >= 0) {
                val x = 12f + (slot % columns) * (WIDTH + 12f)
                val y = 12f + (slot / columns) * (HEIGHT + 34f)
                sheetCanvas.drawBitmap(bitmap, x, y, null)
                sheetCanvas.drawText(preset.name, x, y + HEIGHT + 20f, caption)
            }
            bitmap.recycle()
        }
        val report = File("build/reports/phosphor-artwork.png")
        report.parentFile?.mkdirs()
        FileOutputStream(report).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sheet.recycle()
    }

    /**
     * The wallpaper asks the launcher for white labels.
     * Score every label slot of a typical 4x4 grid, plus the clock and dock zones, by the share
     * of pixels where a label would meet WCAG 4.5:1. Rendered at full on-device detail.
     */
    @Test
    fun launcherLabelsStayReadable() {
        val failures = mutableListOf<String>()
        val report = StringBuilder()
        val w = 270
        val h = 585
        for (preset in WallpaperCatalog.presets.filter { it.kind != WallpaperKind.IMAGE }) {
            for (time in listOf(0f, 12f, 31f)) {
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val renderer = WallpaperRenderer().apply { forcedDetail = 1f }
                renderer.draw(Canvas(bitmap), w, h, time, preset, WallpaperSettings(presetId = preset.id))
                renderer.release()
                val label = Color.WHITE
                val clock = readableShare(bitmap, label, 0.07f, 0.27f, 0f, 1f)
                val dock = readableShare(bitmap, label, 0.86f, 0.93f, 0f, 1f)
                var worst = 1f
                var sum = 0f
                for (row in 0 until 4) for (col in 0 until 4) {
                    val y = 0.36f + row * 0.12f
                    val x = 0.04f + col * 0.24f
                    val share = readableShare(bitmap, label, y, y + 0.025f, x, x + 0.18f)
                    worst = minOf(worst, share); sum += share
                }
                val mean = sum / 16f
                report.append("${preset.id} t=$time clock=${"%.2f".format(clock)} dock=${"%.2f".format(dock)} labelsMean=${"%.2f".format(mean)} labelsWorst=${"%.2f".format(worst)}\n")
                val minimumSlot = when (preset.id) {
                    "aurora-veil", "gradient-tide" -> 0.60f
                    else -> 0.45f
                }
                if (clock < 0.9f || dock < 0.9f || mean < 0.85f || worst < minimumSlot) {
                    failures += "${preset.id} t=$time clock=$clock dock=$dock mean=$mean worst=$worst"
                }
                bitmap.recycle()
            }
        }
        File("build/reports/phosphor-legibility.txt").apply { parentFile?.mkdirs() }.writeText(report.toString())
        assertTrue("Labels would be hard to read:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun wakeRevealStartsFromTheGroundAndEndsAtTheFullArtwork() {
        val preset = WallpaperCatalog.find("orbit-exposure")
        val settings = WallpaperSettings(presetId = preset.id)
        val dark = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val full = render(preset, settings)
        val renderer = WallpaperRenderer()
        try {
            renderer.draw(Canvas(dark), WIDTH, HEIGHT, 12f, preset, settings, reveal = 0f)
            val art = pixels(dark).take(WIDTH * HEIGHT * 3 / 4)
            assertTrue("reveal should begin from black", art.all { Color.red(it) < 40 && Color.green(it) < 40 && Color.blue(it) < 40 })
            renderer.release()
            val done = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            WallpaperRenderer().also { it.draw(Canvas(done), WIDTH, HEIGHT, 12f, preset, settings, reveal = 1f); it.release() }
            assertTrue(done.sameAs(full))
            done.recycle()
        } finally {
            renderer.release(); dark.recycle(); full.recycle()
        }
    }

    @Test
    fun echoMusicChangesTheTraceOnlyWhenEnabledAndMoving() {
        val spectrum = EchoSpectrum()
        val fft = ByteArray(1024).apply { this[8] = 100 }
        repeat(19) { spectrum.accept(fft, 48000000, it * 50L) }
        val echo = WallpaperCatalog.find("ascii-echo")
        fun draw(settings: WallpaperSettings, input: EchoSpectrum?) = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also {
            val renderer = WallpaperRenderer()
            renderer.musicSpectrum = input
            try { renderer.draw(Canvas(it), WIDTH, HEIGHT, 12f, echo, settings) } finally { renderer.release() }
        }
        val enabled = WallpaperSettings(presetId = echo.id, musicReactive = true)
        for (settings in listOf(enabled, enabled.copy(musicReactive = false), enabled.copy(reducedMotion = true), enabled.copy(speed = 0f))) {
            val ambient = draw(settings, null)
            val withInput = draw(settings, spectrum)
            try {
                if (settings == enabled) assertTrue(changedFraction(ambient, withInput) > 0.02f)
                else assertTrue(ambient.sameAs(withInput))
            } finally {
                ambient.recycle()
                withInput.recycle()
            }
        }
    }

    @Test
    fun eachProceduralSceneHasAVisibleCompositionControlEvenWhenStill() {
        for (preset in WallpaperCatalog.presets.filter { it.kind != WallpaperKind.IMAGE }) {
            val settings = WallpaperSettings(presetId = preset.id, reducedMotion = true)
            val low = render(preset, settings.copy(composition = 0f))
            val high = render(preset, settings.copy(composition = 1f))
            try {
                assertTrue("${preset.id} composition has no visible effect", changedFraction(low, high) > 0.01f)
            } finally {
                low.recycle()
                high.recycle()
            }
        }
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
    fun variantsHaveDifferentCompositionsEvenWithMatchingColors() {
        for ((firstId, secondId) in listOf("aurora-veil" to "aurora-solstice", "gradient-daybreak" to "gradient-tide",
                "ascii-signal" to "ascii-echo", "glass-prism" to "glass-spectrum", "texture-basalt" to "texture-satin")) {
            val firstPreset = WallpaperCatalog.find(firstId)
            val secondPreset = WallpaperCatalog.find(secondId).copy(colors = firstPreset.colors)
            val first = render(firstPreset)
            val second = render(secondPreset)
            try {
                val a = pixels(first)
                val b = pixels(second)
                // Ignore faint grain and rounding differences. Require a visible change over an area of the artwork.
                val different = a.indices.count { i ->
                    maxOf(abs(Color.red(a[i]) - Color.red(b[i])), abs(Color.green(a[i]) - Color.green(b[i])),
                        abs(Color.blue(a[i]) - Color.blue(b[i]))) >= 16
                }
                assertTrue("$firstId and $secondId share the same composition", different > a.size * 0.03f)
            } finally {
                first.recycle()
                second.recycle()
            }
        }
    }

    @Test
    fun reducedMotionFreezesTimeAndParallaxForEveryFamily() {
        for (preset in WallpaperCatalog.presets) {
            val settings = WallpaperSettings(presetId = preset.id, reducedMotion = true, tiltParallax = true)
            val first = render(preset, settings, 0f, -1f, -1f, tiltX = -1f)
            val later = render(preset, settings, 91f, 1f, 1f, tiltX = 1f)
            assertTrue("${preset.id} moves with reduced motion", first.sameAs(later))
            first.recycle()
            later.recycle()
        }
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
                    WallpaperSettings(imagePath = imageFile.absolutePath, intensity = 1f, imageFocusX = 0f, imageFocusY = 1f, tiltParallax = true),
                    offsetX = 1f, offsetY = -1f, tiltX = 1f, tiltY = -1f,
                )
                // A tall image must still cover all four corners of a wide surface.
                for ((x, y) in listOf(0 to 0, 359 to 0, 0 to 149, 359 to 149)) {
                    val pixel = bitmap.getPixel(x, y)
                    // Corners sit under the readability veil, so expect red-dominated image color.
                    assertTrue("An uncovered wallpaper edge at $x,$y: ${Integer.toHexString(pixel)}",
                        Color.red(pixel) > 90 && Color.red(pixel) > 4 * maxOf(Color.green(pixel), Color.blue(pixel)))
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
    fun selectedPhotoSubjectStaysCenteredOnPortraitAndLandscapeSurfaces() {
        // The red and green channels encode the source pixel's horizontal and vertical coordinates.
        val source = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        for (y in 0 until 256) for (x in 0 until 256) source.setPixel(x, y, Color.rgb(x, y, 0))
        val file = File.createTempFile("phosphor-framing-", ".png")
        val renderer = WallpaperRenderer()
        try {
            file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val settings = WallpaperSettings(
                imagePath = file.absolutePath, imageFocusX = 0.25f, imageFocusY = 0.75f, reducedMotion = true,
            )
            for ((width, height) in listOf(120 to 300, 300 to 120)) {
                val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    renderer.draw(Canvas(frame), width, height, 0f, WallpaperCatalog.find("image-custom"), settings)
                    val center = frame.getPixel(width / 2, height / 2)
                    if (width < height) {
                        assertEquals(64f, Color.red(center).toFloat(), 3f)
                        assertEquals(128f, Color.green(center).toFloat(), 3f)
                    } else {
                        assertEquals(128f, Color.red(center).toFloat(), 3f)
                        assertEquals(192f, Color.green(center).toFloat(), 3f)
                    }
                } finally {
                    frame.recycle()
                }
            }
        } finally {
            renderer.release()
            source.recycle()
            file.delete()
        }
    }

    @Test
    fun everySceneCanRecoverItsFullDetailFrameAfterLiveDensityDrops() {
        val renderer = WallpaperRenderer()
        val frame = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        try {
            for (preset in WallpaperCatalog.presets.filter { it.kind != WallpaperKind.IMAGE }) {
                val settings = WallpaperSettings(presetId = preset.id)
                renderer.draw(canvas, WIDTH, HEIGHT, 12f, preset, settings)
                val full = pixels(frame)
                renderer.draw(canvas, WIDTH, HEIGHT, 12f, preset, settings, detailScale = 0.1f)
                if (preset.id == "orbit-exposure") {
                    assertFalse("Orbital trails should thin out at minimum density", full.contentEquals(pixels(frame)))
                }
                renderer.draw(canvas, WIDTH, HEIGHT, 12f, preset, settings)
                assertTrue("${preset.id} changed after recovering full detail", full.contentEquals(pixels(frame)))
            }
        } finally {
            renderer.release()
            frame.recycle()
        }
    }

    @Test
    fun releaseAllowsTheSameRendererToBeUsedAgain() {
        val renderer = WallpaperRenderer()
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        try {
            for (id in listOf("texture-basalt", "orbit-first-light", "orbit-nebula")) {
                val preset = WallpaperCatalog.find(id)
                val settings = WallpaperSettings(presetId = id)
                renderer.draw(canvas, WIDTH, HEIGHT, 12f, preset, settings)
                val first = pixels(bitmap)
                renderer.release()
                bitmap.eraseColor(Color.TRANSPARENT)
                renderer.draw(canvas, WIDTH, HEIGHT, 12f, preset, settings)
                assertTrue("$id changed after release", first.contentEquals(pixels(bitmap)))
            }
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

    @Test
    fun launcherOffsetsMoveEverySceneAtZeroSpeedAndRespectTheParallaxSwitch() {
        for (preset in WallpaperCatalog.presets) {
            val settings = WallpaperSettings(presetId = preset.id, speed = 0f)
            val left = render(preset, settings, offsetX = -1f)
            val right = render(preset, settings, offsetX = 1f)
            val fixed = settings.copy(parallax = false)
            val fixedLeft = render(preset, fixed, offsetX = -1f)
            val fixedRight = render(preset, fixed, offsetX = 1f)
            try {
                assertTrue(preset.id + " has no visible launcher parallax", changedFraction(left, right) > 0.01f)
                assertTrue(preset.id + " moves with parallax disabled", fixedLeft.sameAs(fixedRight))
            } finally {
                listOf(left, right, fixedLeft, fixedRight).forEach { it.recycle() }
            }
        }
    }

    @Test
    fun tiltMovesEveryPresetOnASinglePageWhenEnabled() {
        for (preset in WallpaperCatalog.presets) {
            val normal = WallpaperSettings(presetId = preset.id)
            val neutral = render(preset, normal)
            val off = render(preset, normal, tiltX = 1f)
            val tilted = render(preset, normal.copy(tiltParallax = true), tiltX = 1f)
            try {
                assertTrue(preset.id, neutral.sameAs(off))
                assertTrue(preset.id, changedFraction(neutral, tilted) > 0.01f)
            } finally {
                listOf(neutral, off, tilted).forEach { it.recycle() }
            }
        }
    }

    @Test
    fun reducedMotionSuppressesLockReveal() {
        val preset = WallpaperCatalog.find("aurora-veil")
        val settings = WallpaperSettings(reducedMotion = true)
        val first = render(preset, settings)
        val locked = render(preset, settings, reveal = 0f)
        assertTrue(first.sameAs(locked))
        first.recycle()
        locked.recycle()
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
    fun wakeAndUnlockTreatmentsCanBeDisabledIndependently() {
        val preset = WallpaperCatalog.find("orbit-exposure")
        fun frame(settings: WallpaperSettings, wake: Float = 1f, unlock: Float = 1f): Bitmap {
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            val renderer = WallpaperRenderer()
            try {
                renderer.draw(Canvas(bitmap), WIDTH, HEIGHT, 12f, preset, settings,
                    reveal = wake, unlockReveal = unlock)
            } finally {
                renderer.release()
            }
            return bitmap
        }
        val settings = WallpaperSettings(presetId = preset.id)
        val awake = frame(settings)
        val wakeDisabled = frame(settings.copy(wakeReveal = false), wake = 0f)
        val unlockDisabled = frame(settings.copy(unlockAnimation = false), unlock = 0f)
        val waking = frame(settings.copy(unlockAnimation = false), wake = 0.2f)
        val locked = frame(settings.copy(wakeReveal = false), unlock = 0f)
        assertTrue(awake.sameAs(wakeDisabled))
        assertTrue(awake.sameAs(unlockDisabled))
        assertFalse(awake.sameAs(waking))
        assertFalse(awake.sameAs(locked))
        listOf(awake, wakeDisabled, unlockDisabled, waking, locked).forEach { it.recycle() }
    }

    @Test
    fun cachedOrbitalBandsMoveAndRebuildAfterRelease() {
        val renderer = WallpaperRenderer(cacheOrbitBands = true)
        val settings = WallpaperSettings(parallax = false)
        val first = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val next = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            for (id in listOf("orbit-exposure", "orbit-ringfall")) {
                val preset = WallpaperCatalog.find(id)
                renderer.draw(Canvas(first), WIDTH, HEIGHT, 0f, preset, settings)
                renderer.draw(Canvas(next), WIDTH, HEIGHT, 12f, preset, settings)
                assertTrue("$id did not move", changedFraction(first, next) > 0.1f)
                renderer.release()
                renderer.draw(Canvas(next), WIDTH, HEIGHT, 0f, preset, settings)
                assertTrue("$id changed after rebuilding its cache", first.sameAs(next))
            }
        } finally {
            renderer.release()
            first.recycle()
            next.recycle()
        }
    }

    private fun render(
        preset: WallpaperPreset,
        settings: WallpaperSettings = WallpaperSettings(presetId = preset.id),
        time: Float = 12f,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        reveal: Float = 1f,
        tiltX: Float = 0f,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val renderer = WallpaperRenderer()
        try {
            renderer.draw(Canvas(bitmap), WIDTH, HEIGHT, time, preset, settings, offsetX, offsetY, unlockReveal = reveal, tiltX = tiltX)
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

    private fun readableShare(bitmap: Bitmap, label: Int, from: Float, to: Float, left: Float, right: Float): Float {
        val top = (bitmap.height * from).toInt()
        val bottom = (bitmap.height * to).toInt()
        val x0 = (bitmap.width * left).toInt()
        val x1 = (bitmap.width * right).toInt().coerceAtMost(bitmap.width)
        val row = IntArray(x1 - x0)
        val labelLum = RenderKit.luminance(label)
        var ok = 0
        var total = 0
        for (y in top until bottom) {
            bitmap.getPixels(row, 0, row.size, x0, y, row.size, 1)
            for (pixel in row) {
                val l = RenderKit.luminance(pixel)
                val ratio = (maxOf(l, labelLum) + 0.05f) / (minOf(l, labelLum) + 0.05f)
                if (ratio >= 4.5f) ok++
                total++
            }
        }
        return ok.toFloat() / total
    }

    companion object {
        private const val WIDTH = 180
        private const val HEIGHT = 390
    }
}
