package dev.sush.phosphor.render

import android.graphics.Canvas
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.sin
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.mix

internal class OrbitExposure(private val orbit: OrbitKit) {
    private val k = orbit.k

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        // Sits in the gap between typical icon rows, so no label row crosses the bright line.
        val horizon = h * 0.55f
        val reveal = smooth(f.reveal)
        orbit.stars(canvas, f, 150, 0f, horizon, 0.25f)

        orbit.meteor(canvas, f, 17f, 4f, 0f, horizon * 0.85f)

        // The hot upper track precesses above a slower reflection.
        val drift = sin(f.t * 0.27f) * f.amount
        val ux = 1180f + drift * 95f + f.shiftX(0.65f)
        val uy = h * (0.40f + drift * 0.022f) + f.shiftY(0.5f)
        val lx = 1180f - drift * 70f + f.shiftX(1.45f)
        val ly = h * 0.69f + f.shiftY(1.25f)

        var saved = canvas.save()
        canvas.clipRect(-50f, -50f, 1050f, horizon)
        orbit.trails(canvas, f, ux, uy, 0.50f, -8f + drift * 4f, (1500 * f.detail).toInt(), 11, 0.73f, 1500f)
        canvas.restoreToCount(saved)

        saved = canvas.save()
        canvas.clipRect(-50f, horizon, 1050f, h + 50f)
        orbit.trails(canvas, f, lx, ly, 0.32f, 5f - drift * 3f, (1600 * f.detail).toInt(), 29, 0.68f, 1600f)
        canvas.restoreToCount(saved)

        strata(canvas, f, horizon, reveal)
        orbit.horizonLine(canvas, f, horizon, reveal, 0.88f)
        val crossing = 500f + sin(f.t * 0.38f) * 440f * f.amount + f.shiftX(0.9f)
        k.glow(canvas, k.glowB, crossing, horizon, 115f, 13f, (95 * reveal).toInt(), k.modeFor(f))
        val figureColor = if (f.night) 0xFF010204.toInt() else alpha(f.c1, 235)
        k.figure(canvas, 452f + f.shiftX(1f), horizon + h * 0.082f, 21f, figureColor, 0f,
            if (f.night) 0x99000000.toInt() else alpha(f.c1, 70))
    }

    /** Distant edge-on trails shimmer in the narrow gap on either side of the horizon. */
    private fun strata(canvas: Canvas, f: Frame, horizon: Float, reveal: Float) {
        val p = k.stroke
        k.luminous(p, f.night)
        val lines = (8 + 24 * f.detail).toInt()
        for (i in 0 until lines) {
            val distance = fraction(i * 37 + 5)
            val direction = if (i % 2 == 0) 1f else -1f
            val y = horizon + direction * (4f + distance * distance * 46f)
            val shimmer = 0.72f + sin(f.t * (0.24f + distance * 0.42f) + i * 2.3f) * 0.28f
            p.color = mix(f.c1, f.c2, distance)
            p.alpha = (48f * (1f - distance) * reveal * shimmer).toInt()
            p.strokeWidth = 0.9f + distance
            canvas.drawLine(0f, y, 1000f, y, p)
        }
        k.reset(p)
    }
}
