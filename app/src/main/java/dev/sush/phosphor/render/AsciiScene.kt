package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.hash
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Light rendered as characters. Glyph density stands in for brightness, the way ASCII art
 * maps tone to ink. Taps "scramble" a ring of cells; the reveal decodes glyphs into place.
 */
internal class AsciiScene(private val k: RenderKit) {
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val ramp = arrayOf(".", ":", "-", "=", "+", "*", "#", "%", "@", "0")
    private val noise = arrayOf("/", "\\", "|", "<", ">", "{", "}", "$", "&", "?", "1", "7")

    fun draw(canvas: Canvas, f: Frame, terminal: Boolean) {
        if (terminal) afterimage(canvas, f) else beam(canvas, f)
    }

    /** A diagonal beam, with motion-blur streaks around a glyph lattice. */
    private fun beam(canvas: Canvas, f: Frame) {
        val h = f.h
        // Axis runs from the left edge at 0.74h to the right edge at 0.26h.
        val ax = 500f + f.shiftX(0.6f)
        val ay = h * 0.5f + f.shiftY(0.6f)
        val dirX0 = 1000f; val dirY0 = -h * 0.48f
        val len = sqrt(dirX0 * dirX0 + dirY0 * dirY0)
        val dx = dirX0 / len; val dy = dirY0 / len
        val nx = -dy; val ny = dx  // normal: points down-right
        val reveal = smooth(f.reveal)

        k.prepare(f)
        val mode = k.modeFor(f)
        // Soft body of the beam.
        val saved = canvas.save()
        canvas.translate(ax, ay)
        canvas.rotate(Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat())
        k.glow(canvas, k.glowA, 60f, 50f, 900f, 150f, if (f.night) 70 else 45, mode)
        k.glow(canvas, k.glowB, -60f, -70f, 820f, 110f, if (f.night) 60 else 40, mode)
        if (f.night) k.glow(canvas, k.glowWhite, 0f, 0f, 620f, 46f, (60 * reveal).toInt(), RenderKit.Mode.SCREEN)
        // Streaks: long hairlines parallel to the axis, flowing along it.
        val s = k.stroke
        k.additive(s, f.night)
        for (i in 0 until (70 * (0.5f + f.detail * 0.5f)).toInt()) {
            val off = (fraction(i * 7 + 1) - 0.5f).let { it * abs(it) } * 900f
            val length = 160f + fraction(i * 7 + 2) * 620f
            val travel = ((f.t * (60f + fraction(i * 7 + 3) * 140f) + fraction(i * 7 + 4) * 2400f) % 2400f) - 1200f
            val side = if (off < 0) f.c2 else f.c1
            s.color = if (abs(off) < 60f && f.night) Color.WHITE else side
            s.alpha = ((if (f.night) 110f else 90f) * exp(-abs(off) / 260f) * reveal).toInt()
            s.strokeWidth = 1f + fraction(i * 7 + 5) * 2.4f
            canvas.drawLine(travel, off, travel + length, off, s)
        }
        k.reset(s)
        canvas.restoreToCount(saved)

        // Glyph lattice.
        val cellW = 19f
        val cellH = 23f
        glyph.textSize = 22f
        k.additive(glyph, f.night)
        val cols = (1000f / cellW).toInt() + 2
        val rows = (h / cellH).toInt() + 2
        val bend = f.drag
        for (row in 0 until rows) {
            val y = row * cellH
            for (col in 0 until cols) {
                val x = col * cellW + (row % 2) * cellW * 0.5f
                val rx = x - ax; val ry = y - ay
                var across = rx * nx + ry * ny
                val along = rx * dx + ry * dy
                if (bend > 0f) across -= ((f.dragX - ax) * nx + (f.dragY - ay) * ny) * exp(-((along - ((f.dragX - ax) * dx + (f.dragY - ay) * dy)) / 300f).let { it * it }) * bend * 0.7f
                if (abs(across) > 330f) continue
                val envelope = exp(-(across / 165f).let { it * it })
                val flow = 0.6f + 0.4f * sin(along * 0.018f - f.t * 2.4f + sin(across * 0.03f) * 1.5f)
                val wave = if (f.busy) f.wave(x, y, 60f) else 0f
                var lum = envelope * flow * (0.75f + f.amount * 0.4f) + wave * 0.5f
                // Decode on reveal: cells resolve from the center of the beam outward.
                if (reveal < 1f && fraction(row * 131 + col) > reveal * 1.1f) continue
                if (lum < 0.06f) continue
                lum = lum.coerceAtMost(1f)
                val cell = row * 977 + col
                val text = if (wave > 0.2f) noise[hash(cell + (f.now * 18f).toInt()) % noise.size]
                    else ramp[((lum * (ramp.size - 1)) + fraction(cell) * 0.6f).toInt().coerceIn(0, ramp.size - 1)]
                val chroma = (across / 160f).coerceIn(-1f, 1f)
                var color = if (chroma < 0f) mix(f.c1, f.c2, -chroma * 0.0f).let { mix(Color.WHITE, f.c2, -chroma) }
                    else mix(Color.WHITE, f.c1, chroma)
                if (!f.night) color = if (chroma < 0f) mix(f.c1, f.c2, -chroma) else f.c1
                glyph.color = color
                glyph.alpha = ((if (f.night) 245f else 220f) * lum).toInt().coerceIn(0, 255)
                canvas.drawText(text, x, y, glyph)
            }
        }
        k.normal(glyph)
    }

    /**
     * An oscilloscope trace drawn in glyphs. Earlier positions of the wave linger as fading
     * ghosts, the way a phosphor screen holds an afterimage; a slow scanline refreshes them.
     */
    private fun afterimage(canvas: Canvas, f: Frame) {
        val h = f.h
        val reveal = smooth(f.reveal)
        k.prepare(f)
        val mode = k.modeFor(f)
        k.glow(canvas, k.glowA, 500f, h * 0.55f, 820f, h * 0.22f, if (f.night) 55 else 40, mode)
        val cellW = 21f
        val cellH = 24f
        glyph.textSize = 20f
        k.additive(glyph, f.night)
        val top = h * 0.3f
        val bottom = h * 0.8f
        val scan = top + ((f.t * 0.06f) % 1f) * (bottom - top)
        val cols = 49
        val firstRow = (top / cellH).toInt()
        val lastRow = (bottom / cellH).toInt()
        val ghosts = 6
        val crests = FloatArray(ghosts)
        for (col in 0 until cols) {
            val x = col * cellW + 4f + f.shiftX(0.5f)
            val drag = if (f.drag > 0f) exp(-((x - f.dragX) / 160f).let { it * it }) * f.drag else 0f
            for (g in 0 until ghosts) {
                val tg = f.t - g * 0.55f
                crests[g] = h * 0.55f +
                    sin(x * 0.009f + tg * 0.7f) * h * 0.08f * (0.6f + f.amount * 0.4f) +
                    sin(x * 0.023f - tg * 1.1f) * h * 0.028f +
                    sin(x * 0.051f + tg * 2.3f) * h * 0.008f * f.amount +
                    (f.dragY - h * 0.55f) * drag * 0.5f - g * h * 0.012f
            }
            for (row in firstRow..lastRow) {
                val y = row * cellH
                if (reveal < 1f && fraction(row * 71 + col) > reveal * 1.1f) continue
                var lum = 0f
                for (g in 0 until ghosts) {
                    val d = (y - crests[g]) / (cellH * (0.7f + g * 0.25f))
                    lum = maxOf(lum, exp(-d * d) * (1f - g / ghosts.toFloat()).let { it * it })
                }
                // Glyph rain below the live trace, thinning with depth.
                val depth = (y - crests[0]) / (h * 0.2f)
                if (depth in 0f..1f) lum = maxOf(lum, (1f - depth) * 0.38f * (0.5f + 0.5f * fraction(row * 13 + col * 7 + (f.t * 3f).toInt())))
                val since = scan - y
                val persist = if (since in 0f..h * 0.25f) exp(-since / (h * 0.06f)) else 0f
                val wave = if (f.busy) f.wave(x, y, 60f) else 0f
                lum = ((lum * (0.75f + persist * 0.6f)) + wave * 0.5f).coerceAtMost(1f) * (0.65f + f.amount * 0.35f)
                if (lum < 0.05f) continue
                val cell = row * 401 + col
                val text = if (wave > 0.2f) noise[hash(cell + (f.now * 18f).toInt()) % noise.size]
                    else ramp[(lum * (ramp.size - 1) + fraction(cell) * 0.8f).toInt().coerceIn(0, ramp.size - 1)]
                glyph.color = if (f.night) (if (lum > 0.7f) mix(f.c1, Color.WHITE, 0.5f) else mix(f.c2, f.c1, lum)) else mix(f.c2, f.c1, lum)
                glyph.alpha = ((if (f.night) 245f else 225f) * lum).toInt().coerceIn(0, 255)
                canvas.drawText(text, x, y, glyph)
            }
        }
        k.normal(glyph)
        val s = k.stroke
        k.additive(s, f.night)
        s.color = f.c1
        s.alpha = if (f.night) 50 else 40
        s.strokeWidth = 1.6f
        canvas.drawLine(0f, scan, 1000f, scan, s)
        k.reset(s)
    }
}
