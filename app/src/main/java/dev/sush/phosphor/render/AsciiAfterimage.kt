package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * A phosphor terminal. The live trace is redrawn by a sweeping beam along the bottom of a
 * stack; every earlier sweep lingers above it as a fading afterimage, rising like a waterfall
 * plot, nearer lines hiding the ones behind (hidden-line removal per column). Strokes are
 * chosen by slope ( _ / \ | ), so the plot reads as ASCII art.
 *
 * Night is green phosphor with scanlines; day is the same plot from a dot-matrix printer on
 * green-bar continuous paper.
 */
internal class AsciiAfterimage(private val k: RenderKit, private val glyphs: AsciiGlyphs) {
    private val horizon = FloatArray(COLS)
    private val ys = FloatArray(COLS)
    private val xs = FloatArray(COLS)
    private val scan = FloatArray(2800)
    private val centers = FloatArray(4)
    private val heights = FloatArray(4)
    private val widths = FloatArray(4) { i -> 34f + 30f * fraction(i * 13 + 5) }

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        val t = f.t * 0.5f
        val reveal = smooth(f.reveal)
        background(canvas, f, t)

        val rate = 0.42f
        val phase = t * rate - floor(t * rate)
        val idBase = floor(t * rate).toInt()
        val front = h * 0.705f + f.shiftY(0.5f)
        val back = h * 0.29f
        val spacing = (front - back) / LINES
        val sweep = (t * 260f + 590f) % 1200f - 100f
        horizon.fill(h * 2f)
        val p = glyphs.paint
        k.blend(p, if (f.night) RenderKit.Mode.ADD else RenderKit.Mode.INK)

        for (line in 0..LINES) {
            val m = line + phase
            val age = m / LINES
            if (age > 1f) break
            val base = front - m * spacing
            val id = idBase - line
            // The newest scan evolves live. Once it recedes, retain its shape at the time it was recorded.
            val tau = t - (m - 1f).coerceAtLeast(0f) / rate
            // Newest line fades in at the front; old ones dim as they rise. Reveal lights them in order.
            val bright = smooth((m + 0.2f) * 3f) * (1f - age * 0.85f) * smooth(reveal * 1.6f - age * 0.6f)
            if (bright < 0.02f) continue
            val depth = 1.12f - age * 0.65f
            val shift = f.shiftX(depth)
            preparePulses(tau)
            for (col in 0 until COLS) {
                val x = (col - 3 + 0.5f) * CELL_W
                xs[col] = x + shift
                ys[col] = base - amplitude(x, id, f) * (0.35f + 0.65f * (1f - age * 0.5f))
            }
            for (col in 0 until COLS) {
                val y = ys[col]
                // Hidden-line removal: a line shows only above every nearer line in its column.
                if (y > horizon[col] - CELL_H * 0.55f) continue
                horizon[col] = y
                val prev = ys[if (col > 0) col - 1 else col]
                val next = ys[if (col < COLS - 1) col + 1 else col]
                val slope = (next - prev) / (2f * CELL_W)
                val lo = minOf(y, (y + prev) * 0.5f, (y + next) * 0.5f)
                val hi = maxOf(y, (y + prev) * 0.5f, (y + next) * 0.5f)
                val cells = ((hi - lo) / CELL_H).toInt() + 1
                val x = xs[col]
                // The live line glows hottest just behind the sweeping beam.
                var lum = bright * (0.75f + 0.35f * ((base - y) / (h * 0.06f)).coerceIn(0f, 1f))
                var hot = 0f
                if (line == 0) {
                    val behind = ((sweep - x) % 1200f + 1200f) % 1200f
                    hot = exp(-behind / 260f)
                    lum *= 0.6f + 0.6f * hot
                }
                lum = lum.coerceAtMost(1f)
                val cell = col * 131 + id * 977
                for (c in 0 until cells) {
                    val cy = if (cells == 1) y else lo + c * CELL_H
                    val index = when {
                        cells > 1 -> AsciiGlyphs.BAR
                        slope < -0.3f -> AsciiGlyphs.SLASH
                        slope > 0.3f -> AsciiGlyphs.BACKSLASH
                        base - y < CELL_H * 0.4f -> AsciiGlyphs.UNDERSCORE
                        else -> (2 + lum * 6.5f + fraction(cell) * 0.8f).toInt().coerceIn(2, AsciiGlyphs.RAMP - 1)
                    }
                    p.color = color(f, age, hot)
                    p.alpha = (255f * lum).toInt()
                    glyphs.draw(canvas, index, x, cy, CELL_W, CELL_H)
                }
                // Phosphor bleed: a faint dither under the live line.
                if (line == 0 && lum > 0.3f) {
                    p.color = color(f, 0.3f, 0f)
                    p.alpha = (110f * lum).toInt()
                    glyphs.draw(canvas, 0, x, hi + CELL_H, CELL_W, CELL_H)
                }
            }
            if (line == 0) scopeSweep(canvas, f, sweep, reveal)
        }

        k.reset(p)
        p.isFilterBitmap = true
        if (f.night) scanlines(canvas, f)
    }

    /** Pulse widths are fixed; centers and amplitudes are shared by all columns of a trace. */
    private fun preparePulses(tau: Float) {
        for (i in centers.indices) {
            centers[i] = 500f + 210f * sin(tau * (0.21f + i * 0.07f) + i * 2.1f)
            heights[i] = 100f + 120f * (0.5f + 0.5f * sin(tau * (0.3f + i * 0.05f) + i * 1.7f))
        }
    }

    private fun amplitude(x: Float, id: Int, f: Frame): Float {
        val distance = (x - 500f) / 270f
        val env = exp(-distance * distance)
        var a = 0f
        for (i in centers.indices) {
            val delta = (x - centers[i]) / widths[i]
            a += heights[i] * exp(-delta * delta)
        }
        val col = (x / CELL_W).toInt()
        val noise = (fraction(col * 31 + id * 977) - 0.5f) * 16f * (0.3f + env)
        return (a * env + noise * (0.4f + 0.6f * env) + 6f) * (0.6f + 0.4f * f.amount)
    }

    private fun color(f: Frame, age: Float, hot: Float): Int = if (f.night) {
        mix(mix(f.c1, f.c2, age), Color.WHITE, hot * 0.6f)
    } else {
        mix(f.c1, f.c2, age * 0.7f)
    }

    /** Night: a dim raster of unlit glyphs and a soft phosphor glow. Day: green-bar printer paper. */
    private fun background(canvas: Canvas, f: Frame, t: Float) {
        val h = f.h
        val mode = k.modeFor(f)
        if (f.night) {
            k.glow(canvas, k.glowA, 500f, h * 0.55f, 900f, h * 0.3f, 34, mode)
            glyphs.lattice(canvas, 0f, 0f, 1000f, h, CELL_W, CELL_H, f.shiftX(0.3f), f.shiftY(0.3f), alpha(f.c1, 18), RenderKit.Mode.ADD, k)
            // The hum bar: a soft band of brightness rolling slowly down the tube.
            val bar = ((t * 0.05f) % 1f) * h * 1.2f - h * 0.1f
            k.glow(canvas, k.glowA, 500f, bar, 1200f, 90f, 20, mode)
        } else {
            val p = k.fill
            p.color = alpha(f.c2, 15)
            k.blend(p, RenderKit.Mode.INK)
            val band = CELL_H * 3f
            var y = 0f + f.shiftY(0.2f) % (band * 2f)
            while (y < h) {
                canvas.drawRect(44f, y, 956f, y + band, p)
                y += band * 2f
            }
            k.reset(p)
            // Tractor-feed holes and perforations along both margins.
            p.color = mix(f.c0, Color.BLACK, 0.1f)
            val s = k.stroke
            s.color = alpha(f.c2, 70)
            s.strokeWidth = 1f
            var hy = CELL_H
            while (hy < h) {
                canvas.drawCircle(20f, hy, 6f, p)
                canvas.drawCircle(980f, hy, 6f, p)
                canvas.drawLine(40f, hy - 6f, 40f, hy + 4f, s)
                canvas.drawLine(960f, hy - 6f, 960f, hy + 4f, s)
                hy += CELL_H * 2f
            }
            k.reset(p)
            k.reset(s)
            glyphs.lattice(canvas, 44f, 0f, 956f, h, CELL_W, CELL_H, 0f, 0f, alpha(f.c1, 12), RenderKit.Mode.INK, k)
        }
    }

    /** The scope cursor illuminates the segment it is writing. It uses the same crest as the glyphs. */
    private fun scopeSweep(canvas: Canvas, f: Frame, sweep: Float, reveal: Float) {
        val position = (sweep - xs[0]) / CELL_W
        if (position < 0f || position >= COLS - 1) return
        val head = position.toInt()
        val u = position - head
        val y = ys[head] + (ys[head + 1] - ys[head]) * u
        val stroke = k.stroke
        k.luminous(stroke, f.night)
        stroke.color = f.c1
        stroke.strokeWidth = 1.5f
        stroke.alpha = (52f * reveal).toInt()
        canvas.drawLine(sweep, f.h * 0.32f, sweep, f.h * 0.75f, stroke)
        // A short analog trace under the fresh characters fades behind the moving head.
        val start = (head - 6).coerceAtLeast(0)
        stroke.strokeWidth = 3f
        for (i in start..head) {
            val freshness = 1f - (head - i) / 7f
            stroke.alpha = (120f * freshness * reveal).toInt()
            val x1 = if (i == head) sweep else xs[i + 1]
            val y1 = if (i == head) y else ys[i + 1]
            canvas.drawLine(xs[i], ys[i], x1, y1, stroke)
        }
        k.reset(stroke)
        k.glow(canvas, k.glowA, sweep, y, 65f, 65f, ((if (f.night) 118 else 52) * reveal).toInt(), k.modeFor(f))
        if (f.night) k.glow(canvas, k.glowWhite, sweep, y, 16f, 16f, (165f * reveal).toInt(), RenderKit.Mode.ADD)
    }

    /** CRT scanlines: thin dark rows across the whole tube, one batched call. */
    private fun scanlines(canvas: Canvas, f: Frame) {
        var n = 0
        var y = 0f
        while (y < f.h && n + 4 <= scan.size) {
            scan[n] = 0f; scan[n + 1] = y; scan[n + 2] = 1000f; scan[n + 3] = y
            n += 4
            y += 4f
        }
        val s = k.stroke
        s.strokeCap = android.graphics.Paint.Cap.BUTT
        s.color = Color.BLACK
        s.alpha = 46
        s.strokeWidth = 1.4f
        canvas.drawLines(scan, 0, n, s)
        s.strokeCap = android.graphics.Paint.Cap.ROUND
        k.reset(s)
    }

    private companion object {
        const val CELL_W = 20f
        const val CELL_H = 24f
        const val COLS = 56
        const val LINES = 18
    }
}
