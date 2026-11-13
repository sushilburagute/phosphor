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
    fun everyFamilyProducesOpaqueArtworkWithVisibleVariationInBothMoods() {
        val presets = WallpaperCatalog.presets.filter { it.kind != WallpaperKind.IMAGE }
        val columns = 6
        val sheet = Bitmap.createBitmap(columns * 2 * (WIDTH + 12) + 12, (presets.size + columns - 1) / columns * (HEIGHT + 34) + 12, Bitmap.Config.ARGB_8888)
        sheet.eraseColor(0xFF18212C.toInt())
        val sheetCanvas = Canvas(sheet)
        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 13f }
        for (night in listOf(true, false)) {
            for ((index, preset) in WallpaperCatalog.presets.withIndex()) {
                val bitmap = render(preset, night = night)
                val pixels = pixels(bitmap)
                assertTrue("${preset.id} contains transparent pixels", pixels.all { Color.alpha(it) == 255 })
                assertTrue("${preset.id} is effectively a solid fill", pixels.toSet().size > 80)
                val slot = presets.indexOf(preset)
                if (slot >= 0) {
                    val x = 12f + ((slot % columns) * 2 + if (night) 0 else 1) * (WIDTH + 12f)
                    val y = 12f + (slot / columns) * (HEIGHT + 34f)
                    sheetCanvas.drawBitmap(bitmap, x, y, null)
                    if (night) sheetCanvas.drawText(preset.name, x, y + HEIGHT + 20f, caption)
                }
                bitmap.recycle()
            }
        }
        val report = File("build/reports/phosphor-artwork.png")
        report.parentFile?.mkdirs()
        FileOutputStream(report).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sheet.recycle()
    }

    /**
     * Launcher labels are white at night and near-black by day (the wallpaper reports which).
     * Score every label slot of a typical 4x4 grid, plus the clock and dock zones, by the share
     * of pixels where a label would meet WCAG 4.5:1. Rendered at full on-device detail.
     */
    @Test
    fun launcherLabelsStayReadableInBothMoods() {
        val failures = mutableListOf<String>()
        val report = StringBuilder()
        val w = 270
        val h = 585
        for (preset in WallpaperCatalog.presets.filter { it.kind != WallpaperKind.IMAGE }) {
            for (night in listOf(true, false)) {
                for (time in listOf(0f, 12f, 31f)) {
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val renderer = WallpaperRenderer().apply { forcedDetail = 1f }
                    renderer.draw(Canvas(bitmap), w, h, time, preset, WallpaperSettings(presetId = preset.id), night = night)
                    renderer.release()
                    val label = if (night) Color.WHITE else 0xFF1B1B1F.toInt()
                    val mood = if (night) "night" else "day"
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
                    report.append("${preset.id} $mood t=$time clock=${"%.2f".format(clock)} dock=${"%.2f".format(dock)} labelsMean=${"%.2f".format(mean)} labelsWorst=${"%.2f".format(worst)}\n")
                    if (clock < 0.9f || dock < 0.9f || mean < 0.85f || worst < 0.45f) {
                        failures += "${preset.id} $mood t=$time clock=$clock dock=$dock mean=$mean worst=$worst"
                    }
                    bitmap.recycle()
                }
            }
        }
        File("build/reports/phosphor-legibility.txt").apply { parentFile?.mkdirs() }.writeText(report.toString())
        assertTrue("Labels would be hard to read:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun tapsVisiblyAnswerInEveryFamilyEvenWhenMotionSpeedIsZero() {
        for (preset in WallpaperCatalog.presets) {
            val settings = WallpaperSettings(presetId = preset.id, speed = 0f)
            val calm = render(preset, settings, time = 5f)
            val touch = TouchField().apply { tap(0.5f, 0.5f, 10f) }
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            val renderer = WallpaperRenderer()
            try {
                renderer.draw(Canvas(bitmap), WIDTH, HEIGHT, 5f, preset, settings, touch = touch, realSeconds = 10.5f)
                assertTrue("${preset.id} ignores taps", changedFraction(calm, bitmap) > 0.01f)
                assertTrue(touch.isActive(10.5f))
                assertFalse("pulses must end", touch.isActive(10f + TouchField.LIFE + 0.1f))
            } finally {
                renderer.release(); bitmap.recycle(); calm.recycle()
            }
        }
    }

    /** The wake and the finger's velocity must show, not just where the finger ends up. */
    @Test
    fun swipesLeaveAVisibleWakeInEveryFamily() {
        for (preset in WallpaperCatalog.presets) {
            for (night in listOf(true, false)) {
                val swipe = Gestures.swipe()
                val end = swipe.touch
                val still = Gestures.hold(swipe.now - 10f, end.dragX, end.dragY)
                val moving = renderGesture(preset, swipe, night)
                val resting = renderGesture(preset, still, night)
                try {
                    assertTrue("${preset.id} ${if (night) "night" else "day"} shows no wake behind a swipe",
                        changedFraction(moving, resting) > 0.01f)
                } finally {
                    moving.recycle(); resting.recycle()
                }
            }
        }
    }

    @Test
    fun holdingStillVisiblyGathersChargeInEveryFamily() {
        for (preset in WallpaperCatalog.presets) {
            val early = renderGesture(preset, Gestures.hold(0.6f))
            val full = renderGesture(preset, Gestures.hold(TouchField.FULL_HOLD))
            try {
                assertTrue("${preset.id} does not show a held charge", changedFraction(early, full) > 0.005f)
            } finally {
                early.recycle(); full.recycle()
            }
        }
    }

    @Test
    fun gesturesSettleSoTheWallpaperCanStopDrawing() {
        val swipe = Gestures.swipe()
        swipe.touch.release(swipe.now)
        assertTrue(swipe.touch.isActive(swipe.now + 0.1f))
        var now = swipe.now
        repeat(240) { now += 1f / 60f; swipe.touch.advance(now) }
        assertFalse("swipe glide and wake must end", swipe.touch.isActive(now))

        val burst = Gestures.burst(0f)
        val frame = Frame()
        burst.touch.fill(frame, burst.now + 0.01f)
        assertTrue("a charged release bursts", (0 until frame.pulses).any { frame.pulseA[it] > 1f })
        now = burst.now
        repeat(((TouchField.LIFE + 1f) * 60f).toInt()) { now += 1f / 60f; burst.touch.advance(now) }
        assertFalse("the burst must end", burst.touch.isActive(now))
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
    fun wakeAndUnlockTreatmentsCanBeDisabledIndependentlyInBothMoods() {
        val preset = WallpaperCatalog.find("orbit-exposure")
        for (night in listOf(true, false)) {
            fun frame(settings: WallpaperSettings, wake: Float = 1f, unlock: Float = 1f): Bitmap {
                val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
                val renderer = WallpaperRenderer()
                try {
                    renderer.draw(Canvas(bitmap), WIDTH, HEIGHT, 12f, preset, settings,
                        night = night, reveal = wake, unlockReveal = unlock)
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
    }

    private fun render(
        preset: WallpaperPreset,
        settings: WallpaperSettings = WallpaperSettings(presetId = preset.id),
        time: Float = 12f,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        night: Boolean = true,
        interaction: WallpaperInteraction? = null,
        reveal: Float = 1f,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val renderer = WallpaperRenderer()
        try {
            renderer.draw(Canvas(bitmap), WIDTH, HEIGHT, time, preset, settings, offsetX, offsetY, night = night, interaction = interaction, unlockReveal = reveal)
        } finally {
            renderer.release()
        }
        return bitmap
    }

    private fun renderGesture(preset: WallpaperPreset, gesture: Gestures.Gesture, night: Boolean = true): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val renderer = WallpaperRenderer()
        try {
            renderer.draw(Canvas(bitmap), WIDTH, HEIGHT, 12f, preset, WallpaperSettings(presetId = preset.id),
                night = night, touch = gesture.touch, realSeconds = gesture.now)
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
