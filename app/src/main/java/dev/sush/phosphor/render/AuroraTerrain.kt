package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.mix
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Three relief maps viewed from across the water or snowfield. Store illumination separately
 * from ink so a palette edit can recolor the rock without resampling the terrain. */
internal class AuroraTerrain(private val solstice: Boolean) {
    // Evenly spaced cross sections, west to east. The sun sits in the broad saddle at x ≈ 610.
    private val profiles = if (solstice) arrayOf(
        intArrayOf(164, 202, 270, 272, 226, 236, 153, 139, 101, 118, 188, 193, 241, 200),
        intArrayOf(112, 157, 134, 91, 93, 144, 123, 69, 57, 77, 110, 166, 139, 118),
        intArrayOf(64, 48, 28, 36, 54, 54, 43, 26, 20, 27, 61, 85, 78, 49),
    ) else arrayOf(
        intArrayOf(211, 254, 340, 217, 276, 166, 110, 144, 106, 183, 278, 215, 292, 231),
        intArrayOf(174, 205, 176, 104, 87, 68, 44, 71, 105, 158, 203, 188, 132, 108),
        intArrayOf(98, 71, 52, 33, 19, 12, 8, 13, 22, 41, 54, 98, 122, 92),
    )
    private class Relief(val light: ByteArray, val skyline: IntArray, val edgeAlpha: IntArray)
    private val reliefs = arrayOfNulls<Relief>(profiles.size)
    private val bitmaps = arrayOfNulls<Bitmap>(profiles.size)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bounds = RectF()
    private var ground = 0
    private var first = 0
    private var second = 0

    fun draw(canvas: Canvas, f: Frame, reflection: Boolean = false) {
        if (bitmaps[0] == null || f.c0 != ground || f.c1 != first || f.c2 != second) {
            tint(f)
        }
        val shore = f.h * 0.752f
        val scale = minOf(1f, f.h / 1450f)
        // Align the reflected banks at the waterline, then compress them into the lake.
        val saved = canvas.save()
        if (reflection) {
            val water = shore + f.shiftY(1.15f)
            canvas.clipRect(0f, water, 1000f, f.h)
            canvas.scale(1f, -0.46f, 0f, water)
            paint.alpha = 70
        }
        for (layer in profiles.indices) {
            val depth = 0.28f + 0.435f * layer
            val x = LEFT + f.shiftX(depth)
            val y = shore + f.shiftY(depth)
            bounds.set(x, y - (ROWS - TOE) * STEP * scale, x + COLS * STEP, y + TOE * STEP * scale)
            canvas.drawBitmap(checkNotNull(bitmaps[layer]), null, bounds, paint)
        }
        canvas.restoreToCount(saved)
        paint.alpha = 255
    }

    private fun tint(f: Frame) {
        ground = f.c0
        first = f.c1
        second = f.c2
        val pixels = IntArray(COLS * ROWS)
        for (layer in profiles.indices) {
            val relief = reliefs[layer] ?: carve(layer).also { reliefs[layer] = it }
            val shadow = mix(f.c0, if (solstice) f.c1 else f.c2, 0.13f - 0.05f * layer)
            val stone = mix(f.c0, if (solstice) f.c1 else f.c2, 0.36f - 0.10f * layer)
            val sunlit = mix(if (solstice) f.c2 else f.c1, Color.WHITE, if (solstice) 0.13f else 0.38f)
            val summit = mix(stone, sunlit, 0.66f - 0.18f * layer)
            val inks = IntArray(256) { level ->
                if (level < 116) mix(shadow, stone, level / 115f) else mix(stone, summit, (level - 116) / 139f)
            }
            inks[0] = Color.TRANSPARENT
            for (i in relief.light.indices) pixels[i] = inks[relief.light[i].toInt() and 255]
            for (x in 0 until COLS) {
                val i = relief.skyline[x] * COLS + x
                pixels[i] = alpha(pixels[i], relief.edgeAlpha[x])
            }
            val bitmap = bitmaps[layer] ?: Bitmap.createBitmap(COLS, ROWS, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, COLS, 0, 0, COLS, ROWS)
            bitmaps[layer] = bitmap
        }
    }

    private fun carve(layer: Int): Relief {
        val profile = profiles[layer]
        val noise = SpaceNoise(83 + layer * 117 + if (solstice) 19 else 0)
        val heights = FloatArray(COLS * SLICES)
        for (z in 0 until SLICES) {
            val depth = z / (SLICES - 1f)
            val rise = sin(depth * 2.14f)
            for (x in 0 until COLS) {
                val detail = noise.sample(x * 0.049f + depth * 2f, depth * 4.4f)
                val fold = noise.sample(x * 0.018f - depth, depth * 2f)
                val station = (x.toFloat() / (COLS - 1) * profile.lastIndex +
                    (fold - 0.5f) * 0.82f + (1f - depth) * 0.45f).coerceIn(0f, profile.lastIndex - 0.001f)
                val left = station.toInt()
                val u = station - left
                val peak = profile[left] + u * (profile[left + 1] - profile[left])
                // Rills converge toward the foot of the fjord. Low sun reveals horizontal
                // shelves in the mesa, where successive beds resist erosion differently.
                val rills = 1f - abs(2f * detail - 1f)
                val scree = noise.sample(x * 0.21f + depth * 1.7f, depth * 15f)
                val rock = peak * rise * (0.69f + 0.28f * fold + 0.16f * rills) + (scree - 0.5f) * 6f * rise
                heights[z * COLS + x] = if (solstice) rock + 2.1f * sin(rock * 0.23f) else rock
            }
        }
        val map = ByteArray(COLS * ROWS)
        val topRow = IntArray(COLS)
        val edgeAlpha = IntArray(COLS)
        for (x in 0 until COLS) {
            var skyline = ROWS
            var crest = ROWS.toFloat()
            // Near samples hide the backs of the ridges. Fill only newly exposed rock.
            for (z in 1 until SLICES) {
                val i = z * COLS + x
                val height = heights[i]
                val elevation = (ROWS - TOE - (height + z * 0.16f) / STEP).coerceIn(0f, ROWS - 1f)
                crest = minOf(crest, elevation)
                val row = elevation.toInt()
                if (row >= skyline) continue
                val across = (heights[z * COLS + minOf(x + 1, COLS - 1)] -
                    heights[z * COLS + maxOf(x - 1, 0)]) / (2f * STEP)
                val back = (height - heights[i - COLS]) / 3.5f
                val lightX = if (solstice) ((610f - (LEFT + x * STEP)) / 200f).coerceIn(-1f, 1f) else -0.84f
                val normalLight = ((0.40f - lightX * across + 0.18f * back) /
                    sqrt(1f + across * across + back * back)).coerceIn(0f, 1f)
                val high = (height / 200f).coerceIn(0f, 1f)
                val value = (5f + 228f * normalLight * (0.34f + high * 0.66f) + 15f * high).toInt().toByte()
                for (y in row until skyline) map[y * COLS + x] = value
                skyline = row
            }
            topRow[x] = skyline
            edgeAlpha[x] = ((1f + skyline - crest) * 255f).roundToInt().coerceIn(0, 255)
        }
        return Relief(map, topRow, edgeAlpha)
    }

    fun release() {
        for (i in bitmaps.indices) {
            bitmaps[i]?.recycle()
            bitmaps[i] = null
            reliefs[i] = null
        }
    }

    private companion object {
        const val LEFT = -140f
        const val STEP = 2f
        const val COLS = 640
        const val ROWS = 218
        const val TOE = 24
        const val SLICES = 88
    }
}
