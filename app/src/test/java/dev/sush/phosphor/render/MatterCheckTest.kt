package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperPreset
import dev.sush.phosphor.model.WallpaperSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Temporary: the gesture checks of WallpaperRendererTest, for this branch's presets only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MatterCheckTest {
    private val ids = listOf("abstract-ribbon", "abstract-orbit", "texture-dune", "texture-basalt")

    @Test
    fun check() {
        val out = StringBuilder()
        for (id in ids) {
            val preset = WallpaperCatalog.find(id)
            for (night in listOf(true, false)) {
                val swipe = Gestures.swipe()
                val end = swipe.touch
                val still = Gestures.hold(swipe.now - 10f, end.dragX, end.dragY)
                out.append("$id ${if (night) "night" else "day"} swipe=${"%.4f".format(changed(renderGesture(preset, swipe, night), renderGesture(preset, still, night)))}\n")
            }
            out.append("$id hold=${"%.4f".format(changed(renderGesture(preset, Gestures.hold(0.6f)), renderGesture(preset, Gestures.hold(TouchField.FULL_HOLD))))}\n")
            val settings = WallpaperSettings(presetId = preset.id, speed = 0f)
            val calm = render(preset, settings, 5f, null, 0f)
            val tap = render(preset, settings, 5f, TouchField().apply { tap(0.5f, 0.5f, 10f) }, 10.5f)
            out.append("$id tap=${"%.4f".format(changed(calm, tap))}\n")
            out.append("$id time=${"%.4f".format(changed(render(preset, WallpaperSettings(presetId = id), 0f, null, 0f), render(preset, WallpaperSettings(presetId = id), 19f, null, 0f)))}\n")
            val rm = WallpaperSettings(presetId = id, reducedMotion = true)
            out.append("$id reduced=${render(preset, rm, 0f, null, 0f).sameAs(render(preset, rm, 91f, null, 0f))}\n")
        }
        File("build/reports/matter-check.txt").writeText(out.toString())
    }

    private fun render(preset: WallpaperPreset, settings: WallpaperSettings, time: Float, touch: TouchField?, real: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(180, 390, Bitmap.Config.ARGB_8888)
        val r = WallpaperRenderer()
        if (touch != null) r.draw(Canvas(bitmap), 180, 390, time, preset, settings, touch = touch, realSeconds = real)
        else r.draw(Canvas(bitmap), 180, 390, time, preset, settings)
        r.release()
        return bitmap
    }

    private fun renderGesture(preset: WallpaperPreset, g: Gestures.Gesture, night: Boolean = true): Bitmap {
        val bitmap = Bitmap.createBitmap(180, 390, Bitmap.Config.ARGB_8888)
        val r = WallpaperRenderer()
        r.draw(Canvas(bitmap), 180, 390, 12f, preset, WallpaperSettings(presetId = preset.id), night = night, touch = g.touch, realSeconds = g.now)
        r.release()
        return bitmap
    }

    private fun changed(a: Bitmap, b: Bitmap): Float {
        val pa = IntArray(a.width * a.height); a.getPixels(pa, 0, a.width, 0, 0, a.width, a.height)
        val pb = IntArray(b.width * b.height); b.getPixels(pb, 0, b.width, 0, 0, b.width, b.height)
        return pa.indices.count { pa[it] != pb[it] }.toFloat() / pa.size
    }
}
