package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.hash
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Light rendered as characters. Glyph density stands in for brightness, the way ASCII art maps
 * tone to ink: " .:-=+*#%@". Signal bloom is a diagonal beam resolved into a glyph lattice with
 * a white core, chromatic edges and motion-blur streaks; Afterimage ([AsciiAfterimage]) is a
 * phosphor terminal. Glyphs come from a cached atlas ([AsciiGlyphs]).
 */
internal class AsciiScene(private val k: RenderKit) {
    private val glyphs = AsciiGlyphs()
    private val afterimage = AsciiAfterimage(k, glyphs)
    private val streaks = Array(STREAK_BUCKETS) { FloatArray(STREAKS * 4) }
    private val streakCounts = IntArray(STREAK_BUCKETS)

    fun draw(canvas: Canvas, f: Frame, terminal: Boolean) {
        glyphs.ensure()
        k.prepare(f)
        if (terminal) afterimage.draw(canvas, f) else signal(canvas, f)
    }

    private fun signal(canvas: Canvas, f: Frame) {
        val h = f.h
        val t = f.t * 0.4f
        val reveal = smooth(f.reveal)
        val mode = k.modeFor(f)
        // The beam runs from the lower left to the upper right; across > 0 is its lower-right side.
        val len = sqrt(1000f * 1000f + (h * 0.6f) * (h * 0.6f))
        val dx = 1000f / len
        val dy = -h * 0.6f / len
        val nx = -dy
        val ny = dx
        val ax = 500f + f.shiftX(0.6f)
        val ay = h * 0.5f + f.shiftY(0.6f)

        // Body glow and motion-blur streaks, drawn in the beam's own frame (x along, y across).
        val saved = canvas.save()
        canvas.translate(ax, ay)
        canvas.rotate(Math.toDegrees(atan2(dy, dx).toDouble()).toFloat())
        k.glow(canvas, k.glowA, 0f, -80f, 1100f, 190f, if (f.night) 60 else 40, mode)
        k.glow(canvas, k.glowB, 40f, 90f, 1000f, 150f, if (f.night) 56 else 36, mode)
        if (f.night) k.glow(canvas, k.glowWhite, 0f, 0f, 760f, 52f, (54 * reveal).toInt(), RenderKit.Mode.SCREEN)
        streaks(canvas, f, t, reveal)
        canvas.restoreToCount(saved)

        // The unlit screen: a faint lattice of glyphs everywhere.
        glyphs.lattice(canvas, 0f, 0f, 1000f, h, CELL_W, CELL_H, f.shiftX(0.3f), f.shiftY(0.3f),
            if (f.night) RenderKit.alpha(f.c1, 16) else RenderKit.alpha(f.c1, 26), if (f.night) RenderKit.Mode.ADD else RenderKit.Mode.INK, k)

        // A held finger pulls the beam toward itself; a swipe drags it along.
        val fa = (f.dragX - ax) * dx + (f.dragY - ay) * dy
        val fc = (f.dragX - ax) * nx + (f.dragY - ay) * ny
        val sweep = (f.flowX * nx + f.flowY * ny) * 0.05f
        val charge = f.charge * f.drag
        val ringR = 340f * (1f - 0.75f * charge)
        // A decode front runs up the beam every few seconds; glyphs behind it are still scrambling.
        val front = (t * 320f) % 3000f - 1500f
        val scrambleClock = floor(f.now * 14f + t * 10f).toInt()

        val p = glyphs.paint
        k.blend(p, if (f.night) RenderKit.Mode.ADD else RenderKit.Mode.INK)
        val cols = (1000f / CELL_W).toInt()
        val rows = (h / CELL_H).toInt() + 1
        for (row in 0 until rows) {
            val y0 = (row + 0.5f) * CELL_H
            // At night, glyph rows that sit where launcher labels go burn a little dimmer.
            val quiet = if (f.night && inLabelRow(y0 / h)) 0.7f else 1f
            for (col in 0 until cols) {
                val x0 = (col + 0.5f) * CELL_W
                val rx = x0 - ax
                val ry = y0 - ay
                val along = rx * dx + ry * dy
                var across = rx * nx + ry * ny
                if (f.drag > 0.001f) {
                    val g = exp(-((along - fa) / 360f).let { it * it }) * f.drag
                    across -= (fc * 0.7f + sweep) * g
                }
                val fdx = x0 - f.dragX
                val fdy = y0 - f.dragY
                val near = f.drag > 0.001f && fdx * fdx + fdy * fdy < 420f * 420f
                if (abs(across) > 380f && f.pulses == 0 && f.trail == 0 && !near) continue

                val env = exp(-(across / 150f).let { it * it }) +
                    0.38f * exp(-((across - 190f) / 46f).let { it * it }) +
                    0.32f * exp(-((across + 205f) / 58f).let { it * it })
                val flow = 0.6f + 0.4f * sin(along * 0.011f - t * 2f + sin(across * 0.025f + t * 0.3f) * 1.4f)
                val cell = row * 977 + col
                val twinkle = fraction(cell + floor(t * 2.5f + fraction(cell) * 5f).toInt() * 131)
                var lum = env * flow * (0.72f + 0.28f * twinkle) * (0.95f + 0.4f * f.amount)
                val behind = front - along
                val scramble = if (behind in 0f..170f) (1f - behind / 170f) * env.coerceAtMost(1f) else 0f
                lum += scramble * 0.3f

                // Touch: tap/burst rings scramble, the wake decodes, a hold gathers glyphs inward.
                var wave = 0f
                if (f.pulses > 0) wave = f.wave(x0, y0, 64f)
                var wake = 0f
                if (f.trail > 0) wake = f.trailInfluence(x0, y0, 72f)
                var x = x0
                var y = y0
                if (near) {
                    val d = sqrt(fdx * fdx + fdy * fdy)
                    lum += charge * (exp(-((d - ringR) / 46f).let { it * it }) * 0.9f + exp(-(d / ringR).let { it * it }) * 0.45f)
                    val pull = charge * 0.3f * exp(-(d / 320f).let { it * it })
                    x -= fdx * pull; y -= fdy * pull
                }
                if (wave > 0.02f && f.pulses > 0) {
                    // Burst rings push glyphs outward as they pass.
                    val wx = x0 - f.pulseX[0]; val wy = y0 - f.pulseY[0]
                    val wd = sqrt(wx * wx + wy * wy).coerceAtLeast(1f)
                    x += wx / wd * wave * 22f; y += wy / wd * wave * 22f
                }
                lum += wave * 0.6f + wake * 0.95f
                // Reveal decodes from noise into the picture.
                val order = fraction(cell * 7 + 3)
                if (reveal < 1f && order > reveal * 1.2f) continue
                if (lum < 0.07f) continue
                lum = lum.coerceAtMost(1f) * quiet

                val noisy = scramble > 0.35f || wave > 0.22f || wake > 0.45f || (reveal < 1f && order > reveal * 1.2f - 0.2f)
                val index = if (noisy) AsciiGlyphs.NOISE + hash(cell + scrambleClock * 7919) % AsciiGlyphs.NOISE_COUNT
                    else (lum * 8.99f + (fraction(cell) - 0.5f) * 0.9f).toInt().coerceIn(0, AsciiGlyphs.RAMP - 1)
                val chroma = (across / 170f).coerceIn(-1.4f, 1.4f)
                if (f.night) {
                    p.color = nightColor(f, chroma, wake)
                    p.alpha = (255f * lum).toInt()
                    glyphs.draw(canvas, index, x, y, CELL_W, CELL_H)
                    if (lum > 0.5f || wave > 0.15f) {
                        // Chromatic ghosts trail along the beam, wider while a ring passes.
                        val g = 7f + wave * 34f
                        p.color = f.c2; p.alpha = (90f * lum).toInt()
                        glyphs.draw(canvas, index, x + dx * g, y + dy * g, CELL_W, CELL_H)
                        p.color = f.c1; p.alpha = (90f * lum).toInt()
                        glyphs.draw(canvas, index, x - dx * g, y - dy * g, CELL_W, CELL_H)
                    }
                } else {
                    // Riso: blue carries the cool side and the core, red the warm side, slightly off register.
                    val blue = (1f - (chroma - 0.2f).coerceIn(0f, 1f))
                    val red = ((chroma + 0.3f) / 0.9f).coerceIn(0f, 1f)
                    if (blue > 0.05f) {
                        p.color = f.c1; p.alpha = (215f * lum * blue).toInt()
                        glyphs.draw(canvas, index, x, y, CELL_W, CELL_H)
                    }
                    if (red > 0.05f) {
                        p.color = f.c2; p.alpha = (200f * lum * red).toInt()
                        glyphs.draw(canvas, index, x + 2.6f, y - 1.6f, CELL_W, CELL_H)
                    }
                }
            }
        }
        k.reset(p)
        p.isFilterBitmap = true

        if (charge > 0.01f) {
            k.glow(canvas, k.glowA, f.dragX, f.dragY, 60f + 120f * charge, 60f + 120f * charge, (charge * 120f).toInt(), mode)
            if (f.night) k.glow(canvas, k.glowWhite, f.dragX, f.dragY, 20f + 30f * charge, 20f + 30f * charge, (charge * 150f).toInt(), RenderKit.Mode.ADD)
        }
    }

    private fun inLabelRow(v: Float): Boolean {
        val r = (v - 0.355f) / 0.12f
        val i = floor(r).toInt()
        return i in 0..3 && r - i < 0.27f
    }

    /** White core, cyan to one side, a warm sliver then red to the other, like the reference beam. */
    private fun nightColor(f: Frame, chroma: Float, wake: Float): Int {
        val c = when {
            chroma > 0.6f -> mix(WARM, f.c2, (chroma - 0.6f) / 0.5f)
            chroma > 0.25f -> mix(Color.WHITE, WARM, (chroma - 0.25f) / 0.35f)
            chroma > -0.25f -> Color.WHITE
            else -> mix(Color.WHITE, f.c1, (-chroma - 0.25f) / 0.6f)
        }
        return if (wake > 0.05f) mix(c, f.c1, wake * 0.5f) else c
    }

    /** Long hairlines parallel to the beam, flowing along it, colored by side. */
    private fun streaks(canvas: Canvas, f: Frame, t: Float, reveal: Float) {
        streakCounts.fill(0)
        val n = (STREAKS * (0.4f + 0.6f * f.detail)).toInt()
        for (i in 0 until n) {
            val far = i % 3 == 0
            val r = fraction(i * 7 + 1) - 0.5f
            val off = if (far) r * 2200f else r * abs(r) * 1300f
            val length = 140f + fraction(i * 7 + 2) * 700f
            val travel = ((t * (70f + fraction(i * 7 + 3) * 160f) + fraction(i * 7 + 4) * 2600f) % 2600f) - 1300f
            val tint = if (far) 0 else if (abs(off) < 50f) 2 else if (off < 0f) 0 else 1
            val strength = if (far) 0 else if (abs(off) < 180f) 2 else 1
            val b = tint * 3 + strength
            val o = streakCounts[b]
            if (o + 4 > streaks[b].size) continue
            streaks[b][o] = travel; streaks[b][o + 1] = off
            streaks[b][o + 2] = travel + length; streaks[b][o + 3] = off
            streakCounts[b] = o + 4
        }
        val s = k.stroke
        k.additive(s, f.night)
        for (b in 0 until STREAK_BUCKETS) {
            if (streakCounts[b] == 0) continue
            val tint = b / 3
            val strength = b % 3
            s.color = when (tint) { 0 -> f.c1; 1 -> f.c2; else -> if (f.night) Color.WHITE else f.c1 }
            s.strokeWidth = 1.2f + strength * 0.6f
            s.alpha = ((if (f.night) 34 + strength * 40 else 30 + strength * 30) * reveal).toInt()
            canvas.drawLines(streaks[b], 0, streakCounts[b], s)
            // A soft companion stroke gives each streak a little motion blur.
            s.strokeWidth = 6f + strength * 3f
            s.alpha /= 5
            canvas.drawLines(streaks[b], 0, streakCounts[b], s)
        }
        k.reset(s)
    }

    fun release() {
        glyphs.release()
    }

    private companion object {
        const val CELL_W = 18f
        const val CELL_H = 22f
        const val STREAKS = 220
        const val STREAK_BUCKETS = 9
        val WARM = Color.rgb(255, 214, 140)
    }
}
