package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.sin

/** Silk folds with a bright crest, or etched basalt contours that bend around a finger. */
internal class TextureScene(private val k: RenderKit, private val halftone: Halftone) {
    private val edge = FloatArray(64 * 2)

    fun draw(canvas: Canvas, f: Frame, basalt: Boolean) {
        if (basalt) basalt(canvas, f) else silk(canvas, f)
    }

    private fun silk(canvas: Canvas, f: Frame) {
        val h = f.h
        val mode = k.modeFor(f)
        k.glow(canvas, k.glowA, 800f, h * 0.18f, 1100f, h * 0.75f, if (f.night) 70 else 110, mode)
        val p = k.fill
        val s = k.stroke
        for (band in 0 until 5) {
            val drift = sin(f.t * 0.42f + band * 0.7f) * 55f * f.amount
            val y = h * (0.02f + band * 0.22f)
            // Sample the fold edge so pulses can ripple it.
            val n = 48
            k.path.reset()
            for (i in 0..n) {
                val u = i / n.toFloat()
                val x = -120f + u * 1240f
                val bez = cubic(u, y + 130f, y - 410f + drift, y + 470f - drift, y - 60f)
                val ripple = if (f.busy) f.wave(x, bez, 90f) * 34f * sin(x * 0.05f - f.now * 8f) else 0f
                edge[i * 2] = x; edge[i * 2 + 1] = bez + ripple
                if (i == 0) k.path.moveTo(x, bez + ripple) else k.path.lineTo(x, bez + ripple)
            }
            k.path.lineTo(1120f, h + 150f)
            k.path.lineTo(-120f, h + 150f)
            k.path.close()
            p.shader = RenderKit.vertical(
                if (f.night) mix(f.c0, f.c1, 0.42f + band * 0.04f) else mix(f.c0, f.c1, 0.32f + band * 0.06f),
                if (f.night) mix(f.c0, Color.BLACK, 0.4f) else f.c0, y - 120f, y + 380f)
            canvas.drawPath(k.path, p)
            k.reset(p)
            // Crest highlight: a bright line with a soft sheen beneath.
            k.path.reset()
            for (i in 0..n) if (i == 0) k.path.moveTo(edge[0], edge[1]) else k.path.lineTo(edge[i * 2], edge[i * 2 + 1])
            k.luminous(s, f.night)
            s.color = if (f.night) f.c2 else f.c2
            s.strokeWidth = 26f; s.alpha = if (f.night) 26 else 30
            canvas.drawPath(k.path, s)
            s.strokeWidth = 2.2f; s.alpha = if (f.night) 140 else 120
            if (f.night) s.color = mix(f.c2, Color.WHITE, 0.5f)
            canvas.drawPath(k.path, s)
            k.reset(s)
        }
        halftone.ensure(p)
        p.shader = halftone.shader
        p.alpha = if (f.night) 90 else 40
        if (!f.night) k.blend(p, RenderKit.Mode.INK)
        canvas.drawRect(0f, 0f, 1000f, h, p)
        k.reset(p)
    }

    private fun basalt(canvas: Canvas, f: Frame) {
        val h = f.h
        val mode = k.modeFor(f)
        k.glow(canvas, k.glowA, 760f, h * 0.22f, 900f, h * 0.5f, if (f.night) 70 else 60, mode)
        k.glow(canvas, k.glowB, 160f, h * 0.8f, 700f, h * 0.35f, if (f.night) 30 else 40, mode)
        val s = k.stroke
        k.luminous(s, f.night)
        val spacing = if (f.detail > 0.6f) 15f else 22f
        val lines = (h / spacing).toInt() + 4
        for (i in 0 until lines) {
            val y = i * spacing - 30f
            k.path.reset()
            var x = -30f
            var first = true
            while (x <= 1030f) {
                val warp = sin(x * 0.005f + y * 0.0021f + f.t * 0.2f) * 80f + sin(x * 0.013f - y * 0.004f) * 26f
                var yy = y + warp - x * 0.06f
                if (f.busy) {
                    // Press: lines bow away from the finger, like contours around a dent.
                    val inf = f.dragInfluence(x, yy, 170f)
                    yy += if (yy > f.dragY) inf * 46f else -inf * 46f
                    yy += f.wave(x, yy, 70f) * 14f
                }
                if (first) { k.path.moveTo(x, yy); first = false } else k.path.lineTo(x, yy)
                x += 30f
            }
            val major = i % 5 == 0
            s.color = if (major) f.c2 else f.c1
            s.strokeWidth = if (major) 1.8f else 1.1f
            s.alpha = ((if (major) 110 else 60) * smooth(f.reveal)).toInt()
            canvas.drawPath(k.path, s)
        }
        k.reset(s)
    }

    private fun cubic(u: Float, p0: Float, p1: Float, p2: Float, p3: Float): Float {
        val v = 1f - u
        return v * v * v * p0 + 3f * v * v * u * p1 + 3f * v * u * u * p2 + u * u * u * p3
    }
}
