package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Paint
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Staggered rows of square pixels describe two folds of light crossing a dark field. */
internal class TexturePixelWeave {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.SQUARE }
    private val bands = Array(2 * LEVELS) { Points() }
    private val glints = ArrayList<Glint>()
    private var pitch = 0f
    private var height = 0f

    fun draw(canvas: Canvas, f: Frame) {
        val columns = (74f + 36f * f.detail - 66f * f.composition).toInt()
        val spacing = maxOf(1000f / columns, f.h / 300f)
        if (spacing != pitch || height != f.h) build(spacing, f.h)

        val save = canvas.save()
        canvas.translate(f.shiftX(0.27f), f.shiftY(0.27f))
        for (i in bands.indices) {
            val points = bands[i]
            if (points.count == 0) continue
            val light = (i % LEVELS + 1f) / LEVELS
            val accent = if (i < LEVELS) f.c1 else f.c2
            ink.color = mix(f.c0, accent, 0.04f + light * 0.74f)
            ink.strokeWidth = pitch * (0.18f + 0.47f * light)
            canvas.drawPoints(points.xy, 0, points.count, ink)
        }

        // Fade down at each hop and switch on the next cell. The surrounding grid stays put.
        ink.color = f.c2
        ink.strokeWidth = pitch * 0.59f
        for (glint in glints) {
            val cycles = f.t * glint.rate + glint.phase
            val progress = (cycles - floor(cycles)) * 4f
            if (progress >= 3f) continue
            val cell = progress.toInt()
            val pulse = 1f - abs(2f * (progress - cell) - 1f)
            ink.alpha = (215f * f.amount * smooth(pulse)).toInt()
            if (ink.alpha > 0) {
                canvas.drawPoint(glint.x + cell * glint.dx, glint.y + cell * glint.dy, ink)
            }
        }
        canvas.restoreToCount(save)
    }

    private fun build(spacing: Float, frameHeight: Float) {
        pitch = spacing
        height = frameHeight
        for (band in bands) band.count = 0
        glints.clear()
        val rowHeight = pitch * 0.83f
        // Extend the sheet beyond the surface for parallax, including at the smallest spacing.
        val rows = ceil(height / rowHeight).toInt()
        val columns = ceil(1000f / pitch).toInt()
        for (col in -4..columns + 4) {
            for (row in -4..rows + 4) {
                val x = (col + (row and 1) * 0.5f) * pitch
                val y = row * rowHeight
                val u = x / 1000f
                val v = y / height
                val upperCrest = 0.15f + 0.06f * u + 0.22f * u * u
                val lowerCrest = 0.66f + 0.05f * u + 0.29f * u * u
                val upper = smooth(1f - abs(v - upperCrest) / 0.17f) * smooth(1.1f - abs(u - 0.83f))
                val lower = smooth(1f - abs(v - lowerCrest) / 0.22f) * smooth(1.06f - abs(u - 0.1f))
                val light = maxOf(upper, lower)
                if (light < 0.025f) continue
                val seed = row * 14327 + col * 571
                // A fine alternating diagonal leaves a visible weave in the lighter part of each fold.
                val thread = if ((col - row) and 3 == 0) 0.78f else 1f
                val level = (light * thread * (0.92f + 0.08f * fraction(seed)) * LEVELS).toInt()
                    .coerceIn(0, LEVELS - 1)
                bands[(if (upper >= lower) 0 else LEVELS) + level].add(x, y)
                if (light > 0.17f && fraction(seed + 71) < 0.012f) {
                    val direction = if (fraction(seed + 81) < 0.5f) -1f else 1f
                    glints.add(Glint(x, y, direction * pitch * 0.5f, rowHeight,
                        0.09f + 0.11f * fraction(seed + 91), fraction(seed + 101)))
                }
            }
        }
    }

    private class Points {
        var xy = FloatArray(128)
        var count = 0

        fun add(x: Float, y: Float) {
            if (count == xy.size) xy = xy.copyOf(count * 2)
            xy[count++] = x
            xy[count++] = y
        }
    }

    private data class Glint(val x: Float, val y: Float, val dx: Float, val dy: Float, val rate: Float, val phase: Float)

    private companion object {
        const val LEVELS = 24
    }
}
