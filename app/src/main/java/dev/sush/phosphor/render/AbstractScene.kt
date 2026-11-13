package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Filaments and loops that part around a finger and pulse with each tap. */
internal class AbstractScene(private val k: RenderKit) {
    fun draw(canvas: Canvas, f: Frame, loop: Boolean) {
        val h = f.h
        val mode = k.modeFor(f)
        k.glow(canvas, k.glowA, 140f, h * 0.65f, 650f, h * 0.45f, if (f.night) 60 else 70, mode)
        k.glow(canvas, k.glowB, 850f, h * 0.25f, 530f, h * 0.25f, if (f.night) 50 else 60, mode)
        if (loop) loop(canvas, f) else filaments(canvas, f)
    }

    private fun filaments(canvas: Canvas, f: Frame) {
        val h = f.h
        val s = k.stroke
        val count = (150 * (0.5f + f.detail * 0.5f)).toInt()
        val reveal = smooth(f.reveal)
        for (index in 0 until count) {
            val u = index / (count - 1f)
            val drift = sin(f.t * 0.6f + u * 3f) * 65f * f.amount
            var c1x = 1350f + drift; var c1y = h * (0.01f + u * 0.2f)
            var c2x = -540f - drift; var c2y = h * (0.87f - u * 0.1f)
            // Control points are pushed away from the finger and from passing pulses.
            if (f.busy) {
                val mx = 500f; val my = h * (0.4f + u * 0.2f)
                val push = f.dragInfluence(mx, my, 300f) * 260f + f.wave(mx, my, 160f) * 140f
                val ddx = mx - f.dragX; val ddy = my - f.dragY
                val d = sqrt(ddx * ddx + ddy * ddy).coerceAtLeast(1f)
                c1x += ddx / d * push; c1y += ddy / d * push
                c2x += ddx / d * push; c2y += ddy / d * push
            }
            k.path.reset()
            k.path.moveTo(-180f, h * (0.12f + u * 0.78f))
            k.path.cubicTo(c1x, c1y, c2x, c2y, 1180f, h * (0.69f + u * 0.3f))
            val accent = index % 23 < 2
            k.additive(s, f.night)
            s.strokeWidth = if (accent) 3.2f else 1.3f
            s.color = if (accent) f.c2 else mix(f.c1, if (f.night) Color.WHITE else f.c1, u * 0.3f)
            s.alpha = ((if (accent) 230 else 70 + index % 50) * reveal * (0.6f + f.amount * 0.4f)).toInt()
            canvas.drawPath(k.path, s)
            if (accent && f.night) {
                s.strokeWidth = 14f; s.alpha = 40
                canvas.drawPath(k.path, s)
            }
        }
        k.reset(s)
    }

    private fun loop(canvas: Canvas, f: Frame) {
        val h = f.h
        val s = k.stroke
        val saved = canvas.save()
        canvas.translate(500f + f.shiftX(0.6f), h * 0.5f)
        canvas.rotate(-28f + sin(f.t * 0.25f) * 5f * f.amount)
        val pulseAngle = (f.now * 2.2f) % (2f * Math.PI.toFloat())
        val count = (90 * (0.5f + f.detail * 0.5f)).toInt()
        for (index in 0 until count) {
            val u = index / (count - 1f)
            val r = 170f + u * 250f
            for (pass in 0 until if (f.night && index % 3 == 0) 3 else 1) {
                k.path.reset()
                for (step in 0..90) {
                    val angle = step / 90f * (Math.PI * 2).toFloat()
                    var ripple = sin(angle * 3f + f.t * 0.5f) * 25f * f.amount
                    if (f.pulses > 0) ripple += exp(-((angle - pulseAngle + 7f) % 6.283f - 3.14f).let { it * it } * 4f) * 30f * f.pulseA[0]
                    val x = cos(angle) * (r + ripple)
                    val y = sin(angle) * (r * 1.6f + ripple)
                    if (step == 0) k.path.moveTo(x, y) else k.path.lineTo(x, y)
                }
                k.path.close()
                k.additive(s, f.night)
                s.strokeWidth = 2.6f
                when (pass) {
                    0 -> { s.color = mix(f.c1, f.c2, u); s.alpha = ((if (f.night) 22f + sin(u * Math.PI).toFloat() * 62f else 35f + sin(u * Math.PI).toFloat() * 120f) * smooth(f.reveal)).toInt() }
                    1 -> { s.color = f.c2; s.alpha = 40; canvas.translate(-3f, 0f) }
                    else -> { s.color = f.c1; s.alpha = 40; canvas.translate(6f, 0f) }
                }
                canvas.drawPath(k.path, s)
                if (pass == 2) canvas.translate(-3f, 0f)
            }
        }
        canvas.restoreToCount(saved)
        k.reset(s)
    }
}
