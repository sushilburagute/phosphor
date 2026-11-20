package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

internal class OrbitThreshold(private val orbit: OrbitKit) {
    private val k = orbit.k
    private val points = FloatArray(4096)

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        val horizon = h * 0.79f
        val reveal = smooth(f.reveal)
        orbit.stars(canvas, f, 260, 0f, horizon - h * 0.25f, 0.2f)

        orbit.meteor(canvas, f, 23f, 9f, h * 0.02f, h * 0.24f)

        // A pale bowl of light rising from the horizon behind the figure; the floor stays dark.
        val sky = canvas.save()
        canvas.clipRect(-50f, -50f, 1050f, horizon)
        if (f.night) {
            k.glow(canvas, k.glowA, 500f, horizon, 820f, h * 0.26f, (95 * reveal).toInt(), RenderKit.Mode.SCREEN)
            k.glow(canvas, k.glowWhite, 500f, horizon, 380f, h * 0.06f, (150 * reveal).toInt(), RenderKit.Mode.SCREEN)
        } else {
            k.glow(canvas, k.glowA, 500f, horizon, 820f, h * 0.28f, (90 * reveal).toInt(), RenderKit.Mode.INK)
        }
        canvas.restoreToCount(sky)
        if (f.night) k.glow(canvas, k.glowA, 500f, horizon, 700f, h * 0.035f, (60 * reveal).toInt(), RenderKit.Mode.SCREEN)

        // Concentric arcs rise from a small opening. Bright packets travel outwards in waves.
        val skyClip = canvas.save()
        canvas.clipRect(0f, 0f, 1000f, horizon)
        val rings = (36 * (0.6f + f.detail * 0.4f)).toInt()
        val p = k.dots
        k.additive(p, f.night)
        val cx = 505f + sin(f.t * 0.18f) * 60f * f.amount
        val travel = (f.t * 0.14f) % 1f
        for (ring in 0 until rings) {
            val q = (ring + travel) / rings
            val size = q * q
            val rx = 66f + size * 1140f
            val ry = h * (0.039f + size * 0.46f)
            val cy = horizon - h * (0.11f + q * 0.12f) + f.shiftY(0.3f + q)
            val center = cx + f.shiftX(0.3f + q * 1.2f)
            val dots = (32 + rx * f.detail * 0.38f).toInt()
            val start = f.t * (0.08f + q * 0.08f)
            val step = 2f * PI.toFloat() / dots
            val fade = smooth(q * 7f) * smooth((1f - q) * 6f)
            val beacon = f.t * 0.72f - q * 6.3f
            var n = 0
            var hot = 0
            for (d in 0 until dots) {
                val angle = start + d * step
                val x = center + cos(angle) * rx
                if (x < -10f || x > 1010f) continue
                val y = cy + sin(angle) * ry
                if (y > horizon + 8f || y < -8f) continue
                val lit = sin(angle * 2f - beacon) > 0.96f
                if (lit) {
                    points[1600 + hot * 2] = x
                    points[1600 + hot * 2 + 1] = y
                    hot++
                } else {
                    points[n * 2] = x
                    points[n * 2 + 1] = y
                    n++
                }
            }
            p.strokeWidth = (2.1f + q * 2.7f) * (1.5f - f.detail * 0.5f)
            p.color = f.c1
            p.alpha = ((if (f.night) 160f else 126f) * reveal * fade).toInt()
            canvas.drawPoints(points, 0, n * 2, p)
            p.color = if (ring % 4 == 0) f.c2 else mix(f.c1, if (f.night) Color.WHITE else f.c2, 0.68f)
            p.alpha = (220f * reveal * fade).toInt()
            p.strokeWidth *= 1.35f
            canvas.drawPoints(points, 1600, hot * 2, p)
        }
        canvas.restoreToCount(skyClip)
        k.reset(p)

        orbit.horizonLine(canvas, f, horizon, reveal, 0.85f)
        floor(canvas, f, horizon)
        val stride = sin(f.t * 3.1f) * if (f.amount > 0f && f.t != 0f) 1f else 0f
        k.figure(canvas, 500f + f.shiftX(1f), horizon + 1f, 28f,
            if (f.night) 0xFF000000.toInt() else alpha(f.c1, 240), stride)
    }

    /** A perspective field of dots below the horizon, drifting slowly toward the viewer. */
    private fun floor(canvas: Canvas, f: Frame, horizon: Float) {
        val p = k.dots
        k.additive(p, f.night)
        val travel = (f.t * 0.12f) % 1f
        val rows = 14
        for (row in 0 until rows) {
            val z = (row + 1f - travel) / rows
            val y = horizon + z.pow(1.9f) * (f.h - horizon) * 1.05f
            if (y > f.h + 10f) continue
            val spread = 40f + z * 520f
            var n = 0
            for (col in -14..14) {
                val x = 500f + col * spread * 0.16f + f.shiftX(1.2f) * z
                if (x < -10f || x > 1010f) continue
                points[n * 2] = x; points[n * 2 + 1] = y; n++
            }
            p.strokeWidth = 1.4f + z * 5.5f
            p.color = if (row % 4 == 1) f.c2 else f.c1
            // Keep the dock area calm: the floor dims as it approaches the viewer.
            p.alpha = ((if (f.night) 120f else 80f) * (1f - z * 0.75f) * smooth(f.reveal)).toInt()
            canvas.drawPoints(points, 0, n * 2, p)
        }
        k.reset(p)
    }

}
