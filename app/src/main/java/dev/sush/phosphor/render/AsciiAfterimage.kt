package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.hash
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

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

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        val t = f.t * 0.5f
        val reveal = smooth(f.reveal)
        val mode = k.modeFor(f)
        background(canvas, f, t)

        val rate = 0.42f
        val phase = t * rate - floor(t * rate)
        val idBase = floor(t * rate).toInt()
        val front = h * 0.705f + f.shiftY(0.5f)
        val back = h * 0.29f
        val spacing = (front - back) / LINES
        val sweep = (t * 260f) % 1200f - 100f
        val charge = f.charge * f.drag
        horizon.fill(h * 2f)
        val p = glyphs.paint
        k.blend(p, if (f.night) RenderKit.Mode.ADD else RenderKit.Mode.INK)

        for (line in 0..LINES) {
            val m = line + phase
            val age = m / LINES
            if (age > 1f) break
            val base = front - m * spacing
            val id = idBase - line
            val tau = t - m / rate
            // Newest line fades in at the front; old ones dim as they rise. Reveal lights them in order.
            val bright = smooth((m + 0.2f) * 3f) * (1f - age * 0.85f) * smooth(reveal * 1.6f - age * 0.6f)
            if (bright < 0.02f) continue
            for (col in 0 until COLS) {
                val x0 = col * CELL_W + CELL_W * 0.5f + f.shiftX(0.5f)
                xs[col] = x0
                ys[col] = base - amplitude(x0, tau, id, f) * (0.35f + 0.65f * (1f - age * 0.5f))
                if (f.drag > 0.001f) {
                    // The finger is a magnet near the tube: traces bend toward it, more as it charges.
                    val g = exp(-((x0 - f.dragX) / 150f).let { it * it }) * exp(-((ys[col] - f.dragY) / (h * 0.2f)).let { it * it })
                    ys[col] += (f.dragY - ys[col]) * g * f.drag * (0.25f + 0.4f * charge)
                }
                if (f.pulses > 0) {
                    // Degauss: a passing ring wobbles the raster sideways.
                    val w = f.wave(x0, ys[col], 90f)
                    xs[col] += w * 18f * sin(ys[col] * 0.06f + f.now * 9f)
                }
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
                var wave = 0f
                if (f.pulses > 0) wave = f.wave(x, y, 70f)
                lum = (lum + wave * 0.5f).coerceAtMost(1f)
                val cell = col * 131 + id * 977
                for (c in 0 until cells) {
                    val cy = if (cells == 1) y else lo + c * CELL_H
                    val index = when {
                        wave > 0.25f -> AsciiGlyphs.NOISE + hash(cell + c + floor(f.now * 15f).toInt() * 31) % AsciiGlyphs.NOISE_COUNT
                        cells > 1 -> AsciiGlyphs.BAR
                        slope < -0.3f -> AsciiGlyphs.SLASH
                        slope > 0.3f -> AsciiGlyphs.BACKSLASH
                        base - y < CELL_H * 0.4f -> AsciiGlyphs.UNDERSCORE
                        else -> (2 + lum * 6.5f + fraction(cell) * 0.8f).toInt().coerceIn(2, AsciiGlyphs.RAMP - 1)
                    }
                    p.color = color(f, age, hot)
                    p.alpha = (255f * lum).toInt()
                    glyphs.draw(canvas, index, x, cy, CELL_W, CELL_H)
                    if (wave > 0.12f) {
                        // Degauss fringes: the color guns slip apart for a moment.
                        val g = 5f + wave * 14f
                        p.color = if (f.night) FRINGE_A else f.c2; p.alpha = (140f * wave).toInt().coerceAtMost(200)
                        glyphs.draw(canvas, index, x + g, cy, CELL_W, CELL_H)
                        p.color = if (f.night) FRINGE_B else f.c1; p.alpha = (140f * wave).toInt().coerceAtMost(200)
                        glyphs.draw(canvas, index, x - g, cy, CELL_W, CELL_H)
                    }
                }
                // Phosphor bleed: a faint dither under the live line.
                if (line == 0 && lum > 0.3f) {
                    p.color = color(f, 0.3f, 0f)
                    p.alpha = (110f * lum).toInt()
                    glyphs.draw(canvas, 0, x, hi + CELL_H, CELL_W, CELL_H)
                }
            }
        }

        // The beam head: a bloom on the live line where the sweep is drawing.
        val headCol = ((sweep - f.shiftX(0.5f)) / CELL_W).toInt()
        if (headCol in 0 until COLS && reveal > 0.5f) {
            val hy = front - phase * spacing - amplitude(sweep, t - phase / rate, idBase, f)
            k.glow(canvas, k.glowA, sweep, hy, 70f, 70f, if (f.night) 120 else 60, mode)
            if (f.night) k.glow(canvas, k.glowWhite, sweep, hy, 18f, 18f, 170, RenderKit.Mode.ADD)
        }

        wake(canvas, f)
        if (charge > 0.01f) burn(canvas, f, charge)
        k.reset(p)
        p.isFilterBitmap = true
        if (f.night) scanlines(canvas, f, t)
    }

    /** The signal: a few drifting pulses over a quiet baseline, flat at the edges like a pulsar plot. */
    private fun amplitude(x: Float, tau: Float, id: Int, f: Frame): Float {
        val env = exp(-((x - 500f) / 270f).let { it * it })
        var a = 0f
        for (i in 0 until 4) {
            val c = 500f + 210f * sin(tau * (0.21f + i * 0.07f) + i * 2.1f)
            val w = 34f + 30f * fraction(i * 13 + 5)
            val amp = 100f + 120f * (0.5f + 0.5f * sin(tau * (0.3f + i * 0.05f) + i * 1.7f))
            a += amp * exp(-((x - c) / w).let { it * it })
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

    /** A swipe is the electron beam: it writes strokes that persist on the phosphor and fade. */
    private fun wake(canvas: Canvas, f: Frame) {
        if (f.trail < 2) return
        val p = glyphs.paint
        for (i in 1 until f.trail) {
            val x0 = f.trailX[i - 1]; val y0 = f.trailY[i - 1]
            val x1 = f.trailX[i]; val y1 = f.trailY[i]
            val ddx = x1 - x0; val ddy = y1 - y0
            val steps = (sqrt(ddx * ddx + ddy * ddy) / CELL_W).toInt().coerceIn(1, 8)
            val index = when {
                abs(ddy) < abs(ddx) * 0.4f -> 2
                abs(ddx) < abs(ddy) * 0.4f -> AsciiGlyphs.BAR
                ddx * ddy < 0f -> AsciiGlyphs.SLASH
                else -> AsciiGlyphs.BACKSLASH
            }
            for (s in 0 until steps) {
                val u = s / steps.toFloat()
                // Snap to the character grid, as a terminal would.
                val x = floor((x0 + ddx * u) / CELL_W) * CELL_W + CELL_W * 0.5f
                val y = floor((y0 + ddy * u) / CELL_H) * CELL_H + CELL_H * 0.5f
                val a = f.trailA[i - 1] + (f.trailA[i] - f.trailA[i - 1]) * u
                val fresh = a > 0.75f
                p.color = if (f.night) mix(f.c1, Color.WHITE, a * 0.7f) else f.c1
                p.alpha = (255f * a).toInt()
                val glyph = if (fresh) AsciiGlyphs.NOISE + hash(i * 8 + s + floor(f.now * 12f).toInt()) % AsciiGlyphs.NOISE_COUNT else index
                glyphs.draw(canvas, glyph, x, y, CELL_W, CELL_H)
                p.alpha = (110f * a).toInt()
                glyphs.draw(canvas, 0, x, y + CELL_H, CELL_W, CELL_H)
            }
        }
        // The written stroke glows on the phosphor while it fades.
        for (i in 0 until f.trail step 2) {
            k.glow(canvas, k.glowA, f.trailX[i], f.trailY[i], 46f, 46f, (f.trailA[i] * 70f).toInt(), k.modeFor(f))
        }
        if (f.drag > 0.01f) k.glow(canvas, k.glowA, f.dragX, f.dragY, 90f, 90f, (f.drag * 90f).toInt(), k.modeFor(f))
    }

    /** Holding the beam still burns a spot: dense glyphs pile up under a growing bloom. */
    private fun burn(canvas: Canvas, f: Frame, charge: Float) {
        val p = glyphs.paint
        val r = 40f + 150f * charge
        val c0 = ((f.dragX - r) / CELL_W).toInt()
        val c1 = ((f.dragX + r) / CELL_W).toInt()
        val r0 = ((f.dragY - r) / CELL_H).toInt()
        val r1 = ((f.dragY + r) / CELL_H).toInt()
        for (row in r0..r1) for (col in c0..c1) {
            val x = col * CELL_W + CELL_W * 0.5f
            val y = row * CELL_H + CELL_H * 0.5f
            val d = sqrt((x - f.dragX) * (x - f.dragX) + (y - f.dragY) * (y - f.dragY)) / r
            if (d > 1f) continue
            val v = (1f - d) * charge
            val flicker = fraction(row * 37 + col * 11 + floor(f.now * 10f).toInt())
            p.color = if (f.night) mix(f.c1, Color.WHITE, v) else f.c1
            p.alpha = (255f * (0.4f + 0.6f * v) * (0.8f + 0.2f * flicker)).toInt().coerceAtMost(255)
            glyphs.draw(canvas, (v * 12f + flicker * 2f).toInt().coerceIn(0, AsciiGlyphs.RAMP - 1), x, y, CELL_W, CELL_H)
        }
        k.glow(canvas, k.glowA, f.dragX, f.dragY, r * 1.6f, r * 1.6f, (charge * 110f).toInt(), k.modeFor(f))
        if (f.night) k.glow(canvas, k.glowWhite, f.dragX, f.dragY, r * 0.4f, r * 0.4f, (charge * 160f).toInt(), RenderKit.Mode.ADD)
    }

    /** CRT scanlines: thin dark rows across the whole tube, one batched call. */
    private fun scanlines(canvas: Canvas, f: Frame, t: Float) {
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
        const val COLS = 50
        const val LINES = 18
        val FRINGE_A = Color.rgb(255, 70, 190)
        val FRINGE_B = Color.rgb(70, 140, 255)
    }
}
