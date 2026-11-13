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
 * `PHOSPHOR_PREVIEWS=1 ./gradlew testDebugUnitTest --tests '*ArtworkPreviewTest*'`
 * Set PHOSPHOR_PREVIEW_ONLY to a preset id prefix to render a subset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkPreviewTest {
    @Test
    fun renderFullResolutionPreviews() {
        assumeTrue(System.getenv("PHOSPHOR_PREVIEWS") == "1")
        val only = System.getenv("PHOSPHOR_PREVIEW_ONLY").orEmpty()
        val directory = File("build/reports/previews").apply { mkdirs() }
        for (preset in WallpaperCatalog.presets.filter { it.kind != WallpaperKind.IMAGE && it.id.startsWith(only) }) {
            for (night in listOf(true, false)) {
                val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
                val renderer = WallpaperRenderer()
                val touch = System.getenv("PHOSPHOR_PREVIEW_TAP")?.let { TouchField().apply { tap(0.5f, 0.45f, 10f); press(0.3f, 0.6f) } }
                // Warm the touch easing so a held finger is fully present.
                touch?.let { repeat(40) { i -> it.advance(10f + i * 0.016f) } }
                renderer.draw(Canvas(bitmap), 1080, 2340, 12f, preset, WallpaperSettings(presetId = preset.id),
                    night = night, touch = touch, realSeconds = if (touch != null) 10.7f else Float.NaN)
                FileOutputStream(File(directory, "${preset.id}-${if (night) "night" else "day"}.png")).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                renderer.release()
                bitmap.recycle()
            }
        }
    }
}
