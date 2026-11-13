package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import dev.sush.phosphor.render.OrbitKit.Companion.BLUE
import dev.sush.phosphor.render.OrbitKit.Companion.DEEP
import dev.sush.phosphor.render.OrbitKit.Companion.DEG
import dev.sush.phosphor.render.OrbitKit.Companion.EMBER
import dev.sush.phosphor.render.OrbitKit.Companion.PALE
import dev.sush.phosphor.render.OrbitKit.Companion.RED
import dev.sush.phosphor.render.OrbitKit.Companion.WHITE
import dev.sush.phosphor.render.OrbitKit.Companion.inLabelRow
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * Threshold: a bowl of strobed light trails rising from a blazing horizon, a lone figure at its
 * foot, and a perspective floor of colored specks drifting toward the viewer.
 *
 * Each ring of the bowl is the near half of a wide ellipse drawn as a line of dots over a faint
 * continuous smear. Rings rise and widen with height, so their sides climb like the bowl's walls.
 */
internal class OrbitThreshold(private val o: OrbitKit) {
    private val k = o.k

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        val horizon = h * HORIZON
        val reveal = smooth(f.reveal)
        val heat = o.heat(f)
        val cx = 500f + f.shiftX(0.7f)

        o.stars(f, 420, 0f, h * 0.5f, 0.15f)
        o.flushSpecks(canvas, f, reveal * 0.8f, 0.85f)
        o.meteor(canvas, f, 23f, 9f, h * 0.02f, h * 0.24f)
        satellite(canvas, f)
        glow(canvas, f, horizon, cx, reveal, heat)
        rays(canvas, f, horizon, cx, reveal, heat)
        bowl(canvas, f, horizon, cx, reveal)
        o.flushSpecks(canvas, f, (0.3f + reveal * 0.7f) * (0.6f + f.amount * 0.4f))
        floor(canvas, f, horizon, cx, reveal)
        o.flushSpecks(canvas, f, reveal)
        horizonLine(canvas, f, horizon, cx, reveal, heat)
        if (f.trail > 1) o.wake(canvas, f, cx, horizon - h * 0.2f, 0.22f, if (f.flowX >= 0f) 1f else -1f)
        o.gather(canvas, f, 0.3f)
        floorShock(canvas, f, horizon)
        o.shockRings(canvas, f, 0.22f)
        o.flushSpecks(canvas, f, 1f)
        // Backlit by the horizon, the figure's shadow runs straight toward the viewer.
        o.figure(canvas, f, cx, horizon + 1f, 28f, 0f, 1.6f, heat)
    }

    /**
     * Rings of the bowl, bottom to top. Dots travel along each ring; a pulse passing through
     * lifts the ring and turns its dots white; the swipe spin carries every ring along.
     */
    private fun bowl(canvas: Canvas, f: Frame, horizon: Float, cx: Float, reveal: Float) {
        val h = f.h
        val rings = (66 * (0.55f + f.detail * 0.45f)).toInt()
        val night = f.night
        val gain = (0.55f + f.amount * 0.45f) * (0.3f + reveal * 0.7f)
        val spacing = if (f.detail > 0.6f) 8.5f else 14f
        val p = k.stroke
        p.shader = null
        k.additive(p, night)
        for (ring in 0 until rings) {
            // Rings crowd toward the horizon, with a little jitter so the spacing never looks ruled.
            val q = ((ring + fraction(ring * 5) * 0.8f) / rings).pow(1.18f)
            val yb = horizon - 22f - q * (horizon - h * 0.3f) + f.shiftY(0.4f * q)
            // Low rings are narrow and deep, so their sides climb like the walls of a bowl;
            // high rings are wide and nearly flat.
            val rx = 520f + q.pow(1.2f) * 1150f + fraction(ring * 7) * 50f
            val sag = 160f + q * 140f
            val cy = yb - sag
            val omega = (12f + (1f - q) * 30f) * (0.85f + fraction(ring * 3) * 0.3f)
            val phase = f.t * omega + o.spin * (1.4f - q) + fraction(ring * 11) * 360f
            val lift = if (f.busy) f.wave(cx, yb, 120f) * 30f + f.dragInfluence(f.dragX, yb, 260f) * 14f else 0f
            val ry = sag + lift
            val bright = fraction(ring * 13 + 1)

            // A faint smear under the dots gives each ring its line of light; the lowest rings
            // burn red, like the band of light lying on the horizon.
            val color = when {
                q < 0.05f -> f.c2
                q < 0.2f -> if (night) mix(f.c1, Color.WHITE, 0.5f) else f.c1
                bright < 0.06f -> f.c2
                else -> f.c1
            }
            k.rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
            p.shader = o.lane(f, color)
            p.alpha = ((if (night) 40f + (1f - q) * 140f else 34f + (1f - q) * 90f) * gain * (0.6f + bright * 0.7f))
                .toInt().coerceIn(0, 255)
            p.strokeWidth = 0.8f + (1f - q) * 2.4f
            canvas.drawArc(k.rect, 0f, 180f, false, p)

            // Dots: a strobed exposure moving along the ring, with stray colored specks.
            val big = q < 0.4f && bright > 0.3f
            val near = 1f - q
            val n = (180f * (rx + ry) * 0.5f * DEG / spacing).toInt()
            val step = 180f / n
            val offset = phase - floor(phase / step) * step
            val first = floor(phase / step).toInt()
            for (d in 0 until n) {
                val ang = offset + d * step
                val x = cx + cos(ang * DEG) * rx
                if (x < -8f || x > 1008f) continue
                val y = cy + sin(ang * DEG) * ry
                val seed = ring * 7919 + first - d
                val r = fraction(seed)
                // Dots blink in and out like distant traffic; label rows let fewer through.
                if (r < 0.12f + q * 0.35f - bright * 0.1f || (inLabelRow(y / h) && r < 0.6f)) continue
                val wave = if (f.pulses > 0) f.wave(x, y, 80f) else 0f
                val tint = fraction(seed * 3 + 1)
                val cls = when {
                    wave > 0.25f -> WHITE
                    tint < 0.04f + near * 0.04f -> RED
                    tint < 0.1f -> EMBER
                    tint < 0.1f + near * near * 0.45f -> if (near > 0.75f) WHITE else PALE
                    q > 0.6f && tint > 0.6f -> DEEP
                    else -> BLUE
                }
                o.speck(cls, big && fraction(seed + 5) < 0.6f, x, y)
            }
        }
        k.reset(p)
    }

    /** The pale bowl of light behind the figure, kept below the bottom label row. */
    private fun glow(canvas: Canvas, f: Frame, horizon: Float, cx: Float, reveal: Float, heat: Float) {
        val h = f.h
        val mode = if (f.night) RenderKit.Mode.SCREEN else RenderKit.Mode.INK
        val hot = 1f + heat
        val sky = canvas.save()
        canvas.clipRect(-50f, -50f, 1050f, horizon)
        if (f.night) {
            k.glow(canvas, k.glowA, cx, horizon, 900f, h * 0.3f, (60 * reveal * hot).toInt(), mode)
            k.glow(canvas, k.glowA, cx, horizon, 760f, h * 0.075f, (120 * reveal * hot).toInt(), mode)
            k.glow(canvas, k.glowWhite, cx, horizon, 620f * (1f + heat * 0.3f), h * 0.05f * hot, (150 * reveal).toInt(), mode)
            k.glow(canvas, k.glowWhite, cx, horizon, 300f * (1f + heat * 0.3f), h * 0.025f * hot, (230 * reveal).toInt(), mode)
        } else {
            k.glow(canvas, k.glowA, cx, horizon, 900f, h * 0.3f, (60 * reveal * hot).toInt(), mode)
            k.glow(canvas, k.glowA, cx, horizon, 760f, h * 0.06f, (70 * reveal * hot).toInt(), mode)
        }
        canvas.restoreToCount(sky)
        // Light spilling onto the floor, and its reflection straight toward the viewer.
        k.glow(canvas, k.glowA, cx, horizon, 700f, h * 0.03f, ((if (f.night) 90 else 50) * reveal * hot).toInt(), mode)
        k.glow(canvas, k.glowWhite, cx, horizon + h * 0.03f, 40f * hot, h * 0.05f, ((if (f.night) 60 else 30) * reveal * hot).toInt(), mode)
    }

    /** Soft shafts of light fanning up from the horizon behind the figure, slowly swaying. */
    private fun rays(canvas: Canvas, f: Frame, horizon: Float, cx: Float, reveal: Float, heat: Float) {
        val mode = if (f.night) RenderKit.Mode.ADD else RenderKit.Mode.INK
        val length = f.h * 0.34f
        for (i in 0 until 11) {
            val side = (i - 5f) / 5f
            val sway = sin(f.t * 0.11f + i * 1.9f) * 3f
            val angle = side * 52f + sway + (fraction(i * 7) - 0.5f) * 8f
            val saved = canvas.save()
            canvas.rotate(angle, cx, horizon)
            val opacity = ((if (f.night) 34f else 22f) * (0.4f + fraction(i * 3)) * reveal * (1f + heat)).toInt()
            k.glow(canvas, k.glowA, cx, horizon - length * 0.5f, 22f + fraction(i * 5) * 36f, length * 0.55f, opacity, mode)
            canvas.restoreToCount(saved)
        }
    }

    /** A perspective field of colored specks below the horizon, drifting toward the viewer. */
    private fun floor(canvas: Canvas, f: Frame, horizon: Float, cx: Float, reveal: Float) {
        val travel = f.t * 0.1f - floor(f.t * 0.1f)
        val passed = floor(f.t * 0.1f).toInt()
        val rows = (44 * (0.6f + f.detail * 0.4f)).toInt()
        val span = f.h - horizon
        for (row in 0 until rows) {
            val z = (row + travel) / rows
            val y = horizon + z.pow(2.1f) * span * 1.08f
            if (y > f.h + 10f || y < horizon + 2f) continue
            // The dock area stays calm: specks thin out and dim as they come close.
            val dock = y / f.h in 0.85f..0.94f
            val spread = 6f + z * 58f
            val cols = (540f / spread).toInt() + 1
            val shift = f.shiftX(1.2f) * z
            // Rows keep their pattern as they approach: identity is the row minus rows passed.
            val rowSeed = row - passed
            for (col in -cols..cols) {
                val x = cx + col * spread + shift
                if (x < -10f || x > 1010f) continue
                val seed = rowSeed * 131 + col
                val r = fraction(seed)
                if (r < 0.25f + (1f - z) * 0.25f || (dock && r < 0.82f)) continue
                val wave = if (f.pulses > 0) f.wave(x, y, 90f) else 0f
                val tint = fraction(seed * 7 + 3)
                val cls = when {
                    wave > 0.3f -> WHITE
                    tint < 0.12f -> RED
                    tint < 0.2f -> EMBER
                    tint < 0.32f && !dock -> if (z < 0.3f) WHITE else PALE
                    dock || tint > 0.8f -> DEEP
                    else -> BLUE
                }
                o.speck(cls, z > 0.35f && !dock, x, y)
            }
        }
    }

    private fun horizonLine(canvas: Canvas, f: Frame, y: Float, cx: Float, reveal: Float, heat: Float) {
        val span = 540f * (0.15f + reveal * 0.85f)
        val hot = 1f + heat
        val p = k.stroke
        p.shader = null
        if (f.night) {
            k.glow(canvas, k.glowB, cx, y + 4f, span * 1.9f, 16f * hot, (170 * reveal).toInt(), RenderKit.Mode.SCREEN)
            k.glow(canvas, k.glowWhite, cx, y, span * 1.2f, 9f * hot, (230 * reveal).toInt(), RenderKit.Mode.SCREEN)
            k.additive(p, true)
            p.color = f.c2; p.alpha = (210 * reveal).toInt(); p.strokeWidth = 4f
            canvas.drawLine(cx - span * 1.9f, y + 3.5f, cx + span * 1.9f, y + 3.5f, p)
            p.color = Color.WHITE; p.alpha = (240 * reveal).toInt(); p.strokeWidth = 2f * hot
            canvas.drawLine(cx - span * 1.9f, y, cx + span * 1.9f, y, p)
        } else {
            k.glow(canvas, k.glowB, cx, y + 3f, span * 1.8f, 12f * hot, (120 * reveal).toInt(), RenderKit.Mode.INK)
            k.blend(p, RenderKit.Mode.INK)
            p.color = f.c2; p.alpha = (210 * reveal).toInt(); p.strokeWidth = 3f * hot
            canvas.drawLine(cx - span * 1.9f, y + 2.5f, cx + span * 1.9f, y + 2.5f, p)
            p.color = f.c1; p.alpha = (150 * reveal).toInt(); p.strokeWidth = 1.2f
            canvas.drawLine(cx - span * 1.9f, y - 2.5f, cx + span * 1.9f, y - 2.5f, p)
        }
        k.reset(p)
    }

    /** Each pulse also runs across the floor as a foreshortened ring. */
    private fun floorShock(canvas: Canvas, f: Frame, horizon: Float) {
        if (f.pulses == 0) return
        val s = k.stroke
        k.additive(s, f.night)
        for (i in 0 until f.pulses) {
            val r = f.pulseR[i] * 0.9f
            val y = horizon + 30f
            k.rect.set(f.pulseX[i] - r, y - r * 0.1f, f.pulseX[i] + r, y + r * 0.1f)
            s.color = f.c1; s.strokeWidth = 5f; s.alpha = (f.pulseA[i] * 170f).toInt().coerceIn(0, 255)
            canvas.drawOval(k.rect, s)
            s.color = if (f.night) Color.WHITE else f.c2; s.strokeWidth = 1.6f
            canvas.drawOval(k.rect, s)
        }
        k.reset(s)
    }

    /** A satellite crossing the top of the sky every couple of minutes, glinting as it turns. */
    private fun satellite(canvas: Canvas, f: Frame) {
        val period = 70f
        val u = ((f.t + 20f) % period) / period
        val cycle = ((f.t + 20f) / period).toInt()
        if (f.t <= 0f) return
        val y0 = f.h * (0.02f + fraction(cycle * 3) * 0.05f)
        val x = -20f + u * 1040f
        val y = y0 + u * f.h * 0.03f
        val glint = 0.4f + 0.6f * abs(sin(f.t * 1.7f))
        o.speck(if (glint > 0.85f) WHITE else PALE, false, x, y)
        o.flushSpecks(canvas, f, glint)
    }

    private companion object {
        const val HORIZON = 0.8f
    }
}
