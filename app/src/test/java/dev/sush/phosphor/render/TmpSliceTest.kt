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
import java.io.FileOutputStream

// TEMPORARY: slice-local checks; not committed.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TmpSliceTest {
    private val ids = listOf("gradient-daybreak", "gradient-tide", "glass-prism", "glass-orbit")

    @Test
    fun slice() {
        val out = StringBuilder()
        val dir = File("build/reports/tmpslice").apply { mkdirs() }
        for (id in ids) {
            val preset = WallpaperCatalog.find(id)
            for (night in listOf(true, false)) {
                for (time in listOf(0f, 12f, 31f)) {
                    val b = Bitmap.createBitmap(270, 585, Bitmap.Config.ARGB_8888)
                    val r = WallpaperRenderer().apply { forcedDetail = 1f }
                    r.draw(Canvas(b), 270, 585, time, preset, WallpaperSettings(presetId = id), night = night)
                    r.release()
                    FileOutputStream(File(dir, "$id-${if (night) "night" else "day"}-${time.toInt()}.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    b.recycle()
                }
                val swipe = Gestures.swipe()
                val still = Gestures.hold(swipe.now - 10f, swipe.touch.dragX, swipe.touch.dragY)
                out.append("$id ${if (night) "night" else "day"} swipe=${changed(gesture(preset, swipe, night), gesture(preset, still, night))}\n")
            }
            out.append("$id hold=${changed(gesture(preset, Gestures.hold(0.6f), true), gesture(preset, Gestures.hold(TouchField.FULL_HOLD), true))}\n")
            val settings = WallpaperSettings(presetId = id, speed = 0f)
            val calm = render(preset, settings, 5f)
            val touch = TouchField().apply { tap(0.5f, 0.5f, 10f) }
            val b = Bitmap.createBitmap(180, 390, Bitmap.Config.ARGB_8888)
            WallpaperRenderer().also { it.draw(Canvas(b), 180, 390, 5f, preset, settings, touch = touch, realSeconds = 10.5f); it.release() }
            out.append("$id tap=${changed(calm, b)} anim=${changed(render(preset, WallpaperSettings(presetId = id), 0f), render(preset, WallpaperSettings(presetId = id), 19f))}\n")
            val rm = WallpaperSettings(presetId = id, reducedMotion = true)
            out.append("$id reduced=${render(preset, rm, 0f, -1f).sameAs(render(preset, rm, 91f, 1f))}\n")
        }
        File("build/reports/tmpslice.txt").writeText(out.toString())
        println(out)
    }

    private fun render(p: WallpaperPreset, s: WallpaperSettings, t: Float, o: Float = 0f): Bitmap {
        val b = Bitmap.createBitmap(180, 390, Bitmap.Config.ARGB_8888)
        WallpaperRenderer().also { it.draw(Canvas(b), 180, 390, t, p, s, o, o); it.release() }
        return b
    }

    private fun gesture(p: WallpaperPreset, g: Gestures.Gesture, night: Boolean): Bitmap {
        val b = Bitmap.createBitmap(180, 390, Bitmap.Config.ARGB_8888)
        WallpaperRenderer().also {
            it.draw(Canvas(b), 180, 390, 12f, p, WallpaperSettings(presetId = p.id), night = night, touch = g.touch, realSeconds = g.now); it.release()
        }
        return b
    }

    private fun changed(a: Bitmap, b: Bitmap): Float {
        val x = IntArray(a.width * a.height); val y = IntArray(x.size)
        a.getPixels(x, 0, a.width, 0, 0, a.width, a.height); b.getPixels(y, 0, b.width, 0, 0, b.width, b.height)
        return x.indices.count { x[it] != y[it] }.toFloat() / x.size
    }
}
