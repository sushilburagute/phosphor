package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Fluted glass and refracting lenses: the light behind them is drawn once per facet. */
internal class GlassScene(private val k: RenderKit) {
    private val lensSpring = Spring(3)
    private var grabbed = -1
    private var flute: Shader? = null
    private var lensShade: Shader? = null
    private var key = 0L
    private val clip = Path()

    private fun prepare(f: Frame) {
        val next = f.c0.toLong() * 7 + if (f.night) 1 else 2
        if (next == key && flute != null) return
        key = next
        val dark = if (f.night) 0x66000000 else 0x30000000
        val light = if (f.night) 0x40FFFFFF else 0x99FFFFFF.toInt()
        // Across one flute: shadowed left wall, clear middle, bright right lip.
        flute = LinearGradient(0f, 0f, 1f, 0f, intArrayOf(dark, 0x00000000, 0x00FFFFFF, light, dark),
            floatArrayOf(0f, 0.25f, 0.6f, 0.88f, 1f), Shader.TileMode.CLAMP)
        lensShade = RadialGradient(-0.3f, -0.4f, 1.45f,
            intArrayOf(if (f.night) 0x40FFFFFF else 0x70FFFFFF, 0x00FFFFFF, 0x10000000, if (f.night) 0x66000000 else 0x30000000),
            floatArrayOf(0f, 0.5f, 0.8f, 1f), Shader.TileMode.CLAMP)
    }

    /** The light behind the glass. A held finger becomes a lamp you can move. */
    private fun backdrop(canvas: Canvas, f: Frame) {
        val h = f.h
        val t = f.t
        val a = f.amount
        val mode = k.modeFor(f)
        val lampX = 620f + sin(t * 0.5f) * 260f * a
        val lampY = h * 0.42f + cos(t * 0.37f) * h * 0.14f * a
        val lx = lampX + (f.dragX - lampX) * f.drag
        val ly = lampY + (f.dragY - lampY) * f.drag
        k.glow(canvas, k.glowB, 120f + cos(t * 0.3f) * 120f * a, h * 0.7f, 760f, h * 0.42f, if (f.night) 170 else 150, mode)
        k.glow(canvas, k.glowA, lx, ly, 680f, h * 0.3f, if (f.night) 130 else 190, mode)
        k.glow(canvas, k.glowWhite, lx + 40f, ly - 20f, 230f, h * 0.08f, if (f.night) 45 else 120,
            if (f.night) RenderKit.Mode.SCREEN else RenderKit.Mode.NORMAL)
    }

    fun draw(canvas: Canvas, f: Frame, lenses: Boolean) {
        prepare(f)
        if (lenses) lenses(canvas, f) else fluted(canvas, f)
    }

    private fun fluted(canvas: Canvas, f: Frame) {
        val h = f.h
        // Flutes narrow from left to right, as in a pane photographed at an angle.
        var x = -10f
        var index = 0
        val p = k.fill
        while (x < 1010f) {
            val width = 22f + 92f * (1f - (x / 1000f).coerceIn(0f, 1f)).pow(2f)
            val center = x + width / 2f
            val wave = if (f.busy) f.wave(center, f.dragY, 120f) else 0f
            val saved = canvas.save()
            canvas.clipRect(x, 0f, x + width + 0.6f, h)
            // Each flute compresses and shifts what it sees: a cheap, convincing refraction.
            canvas.translate(center, 0f)
            canvas.scale(0.55f + wave * 0.1f, 1f)
            canvas.translate(-center + (center - 500f) * 0.42f + sin(index * 1.7f) * 18f, -(index % 3) * 18f)
            backdrop(canvas, f)
            canvas.restoreToCount(saved)
            val shade = canvas.save()
            canvas.translate(x, 0f)
            canvas.scale(width, 1f)
            p.shader = flute
            canvas.drawRect(0f, 0f, 1f, h, p)
            canvas.restoreToCount(shade)
            x += width
            index++
        }
        k.reset(p)
    }

    private fun lenses(canvas: Canvas, f: Frame) {
        val h = f.h
        val t = f.t
        val a = f.amount
        backdrop(canvas, f)
        val baseX = floatArrayOf(300f + sin(t * 0.6f) * 70f * a, 740f + cos(t * 0.4f) * 60f * a, 210f + sin(t * 0.7f) * 45f * a)
        val baseY = floatArrayOf(h * 0.33f + cos(t * 0.5f) * 45f * a, h * 0.58f + sin(t * 0.5f) * 75f * a, h * 0.78f)
        val radii = floatArrayOf(250f, 330f, 150f)
        // The lens nearest a new touch follows the finger until released.
        if (f.drag > 0.5f && grabbed < 0) {
            var best = Float.MAX_VALUE
            for (i in 0 until 3) {
                val d = sqrt((f.dragX - baseX[i]).pow(2) + (f.dragY - baseY[i]).pow(2)) - radii[i]
                if (d < best) { best = d; grabbed = i }
            }
        }
        if (f.drag < 0.2f) grabbed = -1
        for (i in 0 until 3) {
            val tx = if (i == grabbed) f.dragX - baseX[i] else 0f
            val ty = if (i == grabbed) f.dragY - baseY[i] else 0f
            lensSpring.step(i, tx, ty, f.dt, 30f, 8f)
        }
        if (!f.busy && f.dt == 0f) lensSpring.reset()
        for (i in 0 until 3) {
            val wobble = f.wave(baseX[i], baseY[i], 160f)
            val r = radii[i] * (1f + wobble * 0.06f * sin(f.now * 18f))
            lens(canvas, f, baseX[i] + lensSpring.x[i] + f.shiftX(0.4f + i * 0.3f), baseY[i] + lensSpring.y[i], r)
        }
    }

    private fun lens(canvas: Canvas, f: Frame, x: Float, y: Float, r: Float) {
        val saved = canvas.save()
        clip.reset()
        clip.addCircle(x, y, r, Path.Direction.CW)
        canvas.clipPath(clip)
        k.fill.color = f.c0
        canvas.drawCircle(x, y, r, k.fill)
        // Inside, the world is flipped and magnified, as through a ball lens.
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(-1.35f, -1.35f)
        canvas.translate(-x, -y)
        backdrop(canvas, f)
        canvas.restore()
        canvas.translate(x, y)
        canvas.scale(r, r)
        k.fill.shader = lensShade
        canvas.drawCircle(0f, 0f, 1f, k.fill)
        k.reset(k.fill)
        canvas.restoreToCount(saved)
        // Chromatic rim: the two palette colors separate at the edge.
        val s = k.stroke
        k.rect.set(x - r, y - r, x + r, y + r)
        k.luminous(s, f.night)
        s.strokeWidth = 3f
        s.color = f.c2; s.alpha = 170
        canvas.save(); canvas.translate(-2.5f, 1.5f)
        canvas.drawArc(k.rect, 20f, 140f, false, s)
        canvas.restore()
        s.color = f.c1; s.alpha = 170
        canvas.save(); canvas.translate(2.5f, -1.5f)
        canvas.drawArc(k.rect, 200f, 120f, false, s)
        canvas.restore()
        k.normal(s)
        s.color = if (f.night) 0x99FFFFFF.toInt() else 0xCCFFFFFF.toInt()
        s.strokeWidth = 1.4f
        canvas.drawCircle(x, y, r - 1f, s)
        s.strokeWidth = 5f
        s.color = 0xB3FFFFFF.toInt()
        k.rect.set(x - r * 0.8f, y - r * 0.8f, x + r * 0.8f, y + r * 0.8f)
        canvas.drawArc(k.rect, 205f, 40f, false, s)
        k.reset(s)
    }

    fun release() { lensSpring.reset(); grabbed = -1; flute = null; lensShade = null; key = 0 }
}
