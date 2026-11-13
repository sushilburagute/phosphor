package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperKind
import dev.sush.phosphor.model.WallpaperSettings
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Full-resolution renders for design review and store screenshots. Slow, so opt-in:
 * `PHOSPHOR_PREVIEWS=1 ./gradlew testDebugUnitTest --tests '*ArtworkPreviewTest*' --rerun`
 *
 * - PHOSPHOR_PREVIEW_ONLY: a preset id prefix, to render a subset.
 * - PHOSPHOR_PREVIEW_GESTURES: comma-separated gestures from [Gestures.named] (tap, swipe,
 *   hold, burst), each saved as `<id>-<mood>-<gesture>.png`.
 * - PHOSPHOR_PREVIEW_TIMES: comma-separated animation times; extra times are saved as
 *   `<id>-<mood>-t<time>.png`. The first time (default 12) is the plain `<id>-<mood>.png`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkPreviewTest {
    @Test
    fun renderFullResolutionPreviews() {
        assumeTrue(System.getenv("PHOSPHOR_PREVIEWS") == "1")
        val only = System.getenv("PHOSPHOR_PREVIEW_ONLY").orEmpty()
        val gestures = list("PHOSPHOR_PREVIEW_GESTURES")
        val times = list("PHOSPHOR_PREVIEW_TIMES").mapNotNull { it.toFloatOrNull() }.ifEmpty { listOf(12f) }
        val directory = File("build/reports/previews").apply { mkdirs() }
        for (preset in WallpaperCatalog.presets.filter { it.kind != WallpaperKind.IMAGE && it.id.startsWith(only) }) {
            for (night in listOf(true, false)) {
                val mood = if (night) "night" else "day"
                for ((index, time) in times.withIndex()) {
                    save(directory, if (index == 0) "${preset.id}-$mood" else "${preset.id}-$mood-t${time.toInt()}") { canvas, renderer ->
                        renderer.draw(canvas, W, H, time, preset, WallpaperSettings(presetId = preset.id), night = night)
                    }
                }
                for (name in gestures) {
                    val gesture = Gestures.named(name) ?: continue
                    save(directory, "${preset.id}-$mood-$name") { canvas, renderer ->
                        renderer.draw(canvas, W, H, times.first(), preset, WallpaperSettings(presetId = preset.id),
                            night = night, touch = gesture.touch, realSeconds = gesture.now)
                    }
                }
            }
        }
    }

    private fun list(name: String) = System.getenv(name).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun save(directory: File, name: String, draw: (Canvas, WallpaperRenderer) -> Unit) {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val renderer = WallpaperRenderer()
        try {
            draw(Canvas(bitmap), renderer)
            FileOutputStream(File(directory, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            renderer.release()
            bitmap.recycle()
        }
    }

    private companion object {
        const val W = 1080
        const val H = 2340
    }
}
