package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.sin

/**
 * Aurora as it actually looks: vertical rays hanging from a bright, folding lower edge,
 * shifting color with altitude. Two curtains at different depths, over a dark ridge.
 */
internal class AuroraScene(private val k: RenderKit) {
    private var rayFront: Shader? = null
    private var rayBack: Shader? = null
    private var key = 0L

    private fun prepare(f: Frame) {
        val next = f.c1.toLong() * 31 + f.c2 + if (f.night) 1 else 2
        if (next == key && rayFront != null) return
        key = next
        // By day the rays are pigment light (pastels of the same hues) and only the hem is ink.
        val a = if (f.night) f.c1 else k.fieldA
        val b = if (f.night) f.c2 else k.fieldB
        // Local coordinates: y = 0 is the fold, y = -1 the top of the ray.
        rayFront = LinearGradient(0f, 0f, 0f, -1f,
            intArrayOf(alpha(mix(a, Color.WHITE, if (f.night) 0.35f else 0f), 255), alpha(a, 190), alpha(mix(a, b, 0.6f), 80), alpha(b, 0)),
            floatArrayOf(0f, 0.08f, 0.45f, 1f), Shader.TileMode.CLAMP)
        rayBack = LinearGradient(0f, 0f, 0f, -1f,
            intArrayOf(alpha(b, 200), alpha(b, 110), alpha(b, 0)),
            floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
    }

    fun draw(canvas: Canvas, f: Frame) {
        prepare(f)
        val h = f.h
        val reveal = smooth(f.reveal)
        if (f.night) stars(canvas, f)
        k.glow(canvas, k.glowB, 760f, h * 0.3f, 900f, h * 0.3f, if (f.night) 40 else 120, k.modeFor(f))
        curtain(canvas, f, rayBack, h * 0.41f, 0.62f, 0.6f, 0.2f * reveal, 2.1f, 0.5f)
        // Leave space around the middle launcher-label row as the bright hem moves.
        curtain(canvas, f, rayFront, h * 0.56f, 1f, 1f, 0.27f * reveal, 0f, 1f)
        ridge(canvas, f)
    }

    private fun foldY(x: Float, base: Float, f: Frame, phase: Float, scale: Float): Float {
        val t = f.t
        var y = base +
            sin(x * 0.0042f + t * 0.42f + phase) * 120f * scale +
            sin(x * 0.0113f - t * 0.73f + phase * 2f) * 46f * scale * (0.5f + f.amount * 0.5f) +
            sin(x * 0.027f + t * 1.3f) * 9f * f.amount
        // A held finger folds the curtain toward itself.
        if (f.drag > 0f) {
            val dx = (x - f.dragX) / 220f
            y += (f.dragY - y) * kotlin.math.exp(-dx * dx) * 0.35f * f.drag
        }
        return y
    }

    private fun curtain(canvas: Canvas, f: Frame, shader: Shader?, base: Float, scale: Float, strength: Float, length: Float, phase: Float, depth: Float) {
        val h = f.h
        val step = if (f.detail > 0.6f) 5f else 9f
        val p = k.fill
        p.shader = shader
        if (f.night) k.blend(p, RenderKit.Mode.ADD) else k.blend(p, RenderKit.Mode.INK)
        val shift = f.shiftX(depth)
        var x = -40f
        var index = 0
        while (x < 1040f) {
            val y = foldY(x, base, f, phase, scale) + f.shiftY(depth)
            val flare = if (f.busy) f.waveX(x, 80f) + f.wave(x, y, 140f) * 0.5f else 0f
            val shimmer = 0.5f + 0.5f * sin(x * 0.047f + f.t * 1.6f + sin(x * 0.013f + f.t * 0.3f) * 3f)
            val streak = fraction(index * 31 + (f.t * 0.6f).toInt())  // rays flicker independently
            val ray = h * length * (0.55f + 0.45f * sin(x * 0.009f - f.t * 0.35f + phase) + streak * 0.35f) * (1f + flare * 0.35f)
            val a = (if (f.night) 50f + 110f * shimmer else 90f + 120f * shimmer) * strength * (0.55f + f.amount * 0.45f) + flare * 110f
            p.alpha = a.toInt().coerceIn(0, 255)
            val saved = canvas.save()
            canvas.translate(x + shift, y)
            canvas.scale(1f, ray.coerceAtLeast(1f))
            canvas.drawRect(-step * 0.75f, -1f, step * 0.75f, 0.02f, p)
            canvas.restoreToCount(saved)
            x += step
            index++
        }
        k.reset(p)

        // The bright lower hem, with a soft glow under it.
        val path = k.path
        path.reset()
        x = -40f
        while (x <= 1040f) {
            val y = foldY(x, base, f, phase, scale) + f.shiftY(depth)
            if (x == -40f) path.moveTo(x + shift, y) else path.lineTo(x + shift, y)
            x += 20f
        }
        val s = k.stroke
        if (f.night) k.blend(s, RenderKit.Mode.ADD) else k.blend(s, RenderKit.Mode.INK)
        s.color = f.c1
        s.alpha = (40 * strength * smooth(f.reveal)).toInt()
        s.strokeWidth = 22f
        canvas.drawPath(path, s)
        s.color = if (f.night) mix(f.c1, Color.WHITE, 0.6f) else f.c1
        s.alpha = (170 * strength * smooth(f.reveal)).toInt()
        s.strokeWidth = 2.4f
        canvas.drawPath(path, s)
        k.reset(s)
    }

    private fun stars(canvas: Canvas, f: Frame) {
        val p = k.fill
        p.color = Color.WHITE
        for (i in 0 until (120 * (0.5f + f.detail * 0.5f)).toInt()) {
            val x = fraction(i * 17 + 3) * 1000f + f.shiftX(0.15f)
            val y = fraction(i * 23 + 5) * f.h * 0.8f
            p.alpha = (60 + 120 * (0.5f + 0.5f * sin(f.t * 1.7f + i))).toInt()
            canvas.drawCircle(x, y, 0.7f + fraction(i + 41) * 1.4f, p)
        }
        p.alpha = 255
    }

    /** A mountain ridge keeps the dock area dark at night and gives the scene scale. */
    private fun ridge(canvas: Canvas, f: Frame) {
        val h = f.h
        val path = k.path
        path.reset()
        val base = h * 0.84f
        path.moveTo(-40f, h + 40f)
        var x = -40f
        while (x <= 1040f) {
            val ridge = base - 70f * sin(x * 0.006f + 1.2f) - 34f * sin(x * 0.019f) - 12f * sin(x * 0.07f) + f.shiftX(1.3f) * 0f
            path.lineTo(x + f.shiftX(1.3f), ridge + f.shiftY(1.2f))
            x += 16f
        }
        path.lineTo(1040f, h + 40f)
        path.close()
        val p = k.fill
        // By day the ridge is a hazy, key-tinted hill that fades into the paper at its foot.
        p.shader = RenderKit.vertical(
            if (f.night) mix(f.c0, Color.BLACK, 0.3f) else mix(f.c0, f.c1, 0.3f),
            if (f.night) Color.BLACK else mix(f.c0, k.fieldA, 0.35f), base - 100f, h)
        canvas.drawPath(path, p)
        k.reset(p)
    }
}

internal fun RenderKit.modeFor(f: Frame) = if (f.night) RenderKit.Mode.SCREEN else RenderKit.Mode.INK
