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
 * - PHOSPHOR_PREVIEW_TIMES: comma-separated animation times; extra times are saved as
 *   `<id>-night-t<time>.png`. The first time (default 12) is the plain `<id>-night.png`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkPreviewTest {
    @Test
    fun renderFullResolutionPreviews() {
        assumeTrue(System.getenv("PHOSPHOR_PREVIEWS") == "1")
        val only = System.getenv("PHOSPHOR_PREVIEW_ONLY").orEmpty()
        val times = System.getenv("PHOSPHOR_PREVIEW_TIMES").orEmpty().split(',')
            .mapNotNull { it.trim().toFloatOrNull()?.takeIf(Float::isFinite) }.distinct().ifEmpty { listOf(12f) }
        val directory = File("build/reports/previews").apply { mkdirs() }
        for (preset in WallpaperCatalog.presets.filter { it.kind != WallpaperKind.IMAGE && it.id.startsWith(only) }) {
            for ((index, time) in times.withIndex()) {
                val stamp = time.toString().removeSuffix(".0")
                save(directory, if (index == 0) "${preset.id}-night" else "${preset.id}-night-t$stamp") { canvas, renderer ->
                    renderer.draw(canvas, W, H, time, preset, WallpaperSettings(presetId = preset.id))
                }
            }
        }
    }

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
