package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.mix
import kotlin.math.sin

internal class TextureSilk(private val k: RenderKit, private val halftone: Halftone) {
    private val edge = FloatArray(49 * 2)
    private val underside = FloatArray(49)
    private val cloth = arrayOfNulls<Shader>(4)
    private var shaderHeight = -1f
    private var shaderGround = 0
    private var shaderInk = 0
    private var shaderNight = false

    private fun prepareCloth(f: Frame) {
        if (shaderHeight == f.h && shaderGround == f.c0 && shaderInk == f.c1 && shaderNight == f.night) return
        for (band in cloth.indices) {
            val y = f.h * (0.14f + band * 0.205f)
            val face = mix(f.c0, f.c1, if (f.night) 0.58f else 0.63f)
            val shadow = if (f.night) mix(f.c0, Color.BLACK, 0.42f) else mix(f.c0, f.c1, 0.18f)
            val fade = if (f.night) mix(f.c0, f.c1, 0.12f) else f.c0
            cloth[band] = LinearGradient(215f + band * 54f, y - f.h * 0.10f, 620f - band * 32f, y + f.h * 0.30f,
                intArrayOf(shadow, face, fade), floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
        }
        shaderHeight = f.h
        shaderGround = f.c0
        shaderInk = f.c1
        shaderNight = f.night
    }

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        val mode = k.modeFor(f)
        k.glow(canvas, k.glowA, 800f, h * 0.18f, 1100f, h * 0.75f, if (f.night) 70 else 110, mode)
        val p = k.fill
        val s = k.stroke
        prepareCloth(f)
        for (band in cloth.indices) {
            val y = h * (0.14f + band * 0.205f)
            val phase = f.t * 0.24f + band * 0.95f
            val tilt = -0.28f + band * 0.16f
            val depth = 0.4f + band * 0.30f
            val n = 48
            k.path.reset()
            for (i in 0..n) {
                val u = i / n.toFloat()
                val x = -145f + u * 1290f + f.shiftX(depth)
                val arch = sin(u * 4.5f + band * 1.29f) * 0.105f +
                    sin(u * 3.8f - phase) * 0.052f * f.amount
                val crest = y + h * ((u - 0.5f) * tilt + arch * (1f + f.composition)) + f.shiftY(depth)
                val fold = 0.5f + 0.5f * sin(u * 5.3f - phase * 0.60f + band * 0.85f)
                edge[i * 2] = x
                edge[i * 2 + 1] = crest
                underside[i] = crest + 3f + fold * fold * h * 0.084f
                if (i == 0) k.path.moveTo(x, crest) else k.path.lineTo(x, crest)
            }
            k.path.lineTo(1180f, h + 200f)
            k.path.lineTo(-180f, h + 200f)
            k.path.close()
            p.shader = cloth[band]
            canvas.drawPath(k.path, p)
            k.reset(p)

            // A turned-over lip casts a narrow shadow on the layer below.
            k.path.reset()
            k.path.moveTo(edge[0], edge[1])
            for (i in 1..n) k.path.lineTo(edge[i * 2], edge[i * 2 + 1])
            for (i in n downTo 0) k.path.lineTo(edge[i * 2], underside[i])
            k.path.close()
            p.color = if (f.night) mix(f.c0, f.c2, 0.11f) else mix(f.c0, f.c2, 0.40f)
            p.alpha = 104
            canvas.drawPath(k.path, p)
            k.reset(p)

            k.path.reset()
            k.path.moveTo(edge[0], edge[1])
            for (i in 1..n) k.path.lineTo(edge[i * 2], edge[i * 2 + 1])
            k.luminous(s, f.night)
            s.color = f.c2
            s.strokeWidth = 22f
            s.alpha = if (f.night) 21 else 24
            canvas.drawPath(k.path, s)
            s.strokeWidth = 2.2f
            s.alpha = if (f.night) 112 else 94
            canvas.drawPath(k.path, s)
            k.reset(s)

            // A soft highlight travels along the fold instead of lighting its entire length.
            val progress = (0.50f + 0.34f * sin(phase + band) * f.amount) * n
            val j = progress.toInt()
            val part = progress - j
            val gx = edge[j * 2] + (edge[j * 2 + 2] - edge[j * 2]) * part
            val gy = edge[j * 2 + 1] + (edge[j * 2 + 3] - edge[j * 2 + 1]) * part
            val highlight = canvas.save()
            canvas.translate(gx, gy)
            val angle = kotlin.math.atan2(edge[j * 2 + 3] - edge[j * 2 + 1], edge[j * 2 + 2] - edge[j * 2])
            canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat())
            k.glow(canvas, k.glowB, 0f, 0f, 164f, 21f, 78, mode)
            canvas.restoreToCount(highlight)
        }
        halftone.ensure()
        p.shader = halftone.shader
        p.alpha = if (f.night) 48 else 32
        if (!f.night) k.blend(p, RenderKit.Mode.INK)
        canvas.drawRect(0f, 0f, 1000f, h, p)
        k.reset(p)
    }

}
