package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A small spring that eases a value toward a target; deterministic when left alone. */
private class Spring(count: Int) {
    val x = FloatArray(count)
    val y = FloatArray(count)
    private val vx = FloatArray(count)
    private val vy = FloatArray(count)

    fun step(i: Int, tx: Float, ty: Float, dt: Float, stiffness: Float = 18f, damping: Float = 6.5f) {
        if (dt <= 0f) return
        val ax = (tx - x[i]) * stiffness - vx[i] * damping
        val ay = (ty - y[i]) * stiffness - vy[i] * damping
        vx[i] += ax * dt; vy[i] += ay * dt
        x[i] += vx[i] * dt; y[i] += vy[i] * dt
    }

    fun reset() { x.fill(0f); y.fill(0f); vx.fill(0f); vy.fill(0f) }
}

/** Shared halftone screen: a dot tile at 45 degrees, tinted per mood. */
internal class Halftone {
    private var tile: Bitmap? = null
    var shader: Shader? = null; private set

    fun ensure(paint: Paint) {
        if (tile != null) return
        val size = 12
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        c.drawCircle(3f, 3f, 1.9f, p)
        c.drawCircle(9f, 9f, 1.9f, p)
        tile = bitmap
        shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(1.1f, 1.1f) })
        }
    }

    fun release() { tile?.recycle(); tile = null; shader = null }
}

/** Mesh-like gradient blobs that you can stir with a finger, under a halftone screen. */
internal class GradientScene(private val k: RenderKit, private val halftone: Halftone) {
    private val stir = Spring(4)

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        val t = f.t
        val a = f.amount
        val mode = k.modeFor(f)
        // Base positions follow slow Lissajous paths.
        val bx = floatArrayOf(250f + sin(t * 0.7f) * 220f * a, 800f + cos(t * 0.45f) * 190f * a, 80f + sin(t * 0.35f) * 180f * a, 520f + sin(t * 0.5f) * 170f * a)
        val by = floatArrayOf(h * 0.34f + cos(t * 0.63f) * h * 0.1f * a, h * 0.74f + sin(t * 0.6f) * h * 0.1f * a, h * 0.02f, h * 0.55f + cos(t * 0.4f) * h * 0.08f * a)
        for (i in 0 until 4) {
            val influence = f.dragInfluence(bx[i] + stir.x[i], by[i] + stir.y[i], 520f)
            stir.step(i, (f.dragX - bx[i]) * influence * 0.8f, (f.dragY - by[i]) * influence * 0.8f, f.dt)
        }
        if (!f.busy && f.dt == 0f) stir.reset()
        k.glow(canvas, k.glowA, bx[0] + stir.x[0], by[0] + stir.y[0], 1050f, h * 0.48f, if (f.night) 165 else 210, mode)
        k.glow(canvas, k.glowB, bx[1] + stir.x[1], by[1] + stir.y[1], 1000f, h * 0.44f, if (f.night) 165 else 200, mode)
        k.glow(canvas, k.glowB, bx[2] + stir.x[2], by[2] + stir.y[2], 900f, h * 0.36f, if (f.night) 150 else 150, mode)
        k.glow(canvas, k.glowWhite, bx[3] + stir.x[3], by[3] + stir.y[3], 640f, h * 0.2f, if (f.night) 18 else 90, if (f.night) RenderKit.Mode.SCREEN else RenderKit.Mode.NORMAL)

        // The halftone screen: denser where the color is, like print.
        halftone.ensure(k.fill)
        val p = k.fill
        p.shader = halftone.shader
        p.alpha = if (f.night) 110 else 36
        if (!f.night) k.blend(p, RenderKit.Mode.INK)
        canvas.drawRect(0f, 0f, 1000f, h, p)
        k.reset(p)

        ripples(canvas, f, k)
    }

    fun release() = stir.reset()
}

/** Concentric water-drop rings for each pulse: light crest followed by a shadow trough. */
internal fun ripples(canvas: Canvas, f: Frame, k: RenderKit) {
    if (f.pulses == 0) return
    val s = k.stroke
    for (i in 0 until f.pulses) {
        for (ring in 0 until 3) {
            val r = f.pulseR[i] * (1f - ring * 0.18f)
            if (r < 4f) continue
            val a = f.pulseA[i] * (1f - ring * 0.3f)
            k.luminous(s, f.night)
            s.color = if (f.night) Color.WHITE else f.c1
            s.alpha = (a * (if (f.night) 70f else 60f)).toInt()
            s.strokeWidth = 10f - ring * 2f
            canvas.drawCircle(f.pulseX[i], f.pulseY[i], r, s)
            k.normal(s)
            s.color = if (f.night) Color.BLACK else Color.WHITE
            s.alpha = (a * 40f).toInt()
            s.strokeWidth = 6f
            canvas.drawCircle(f.pulseX[i], f.pulseY[i], r - 12f, s)
        }
    }
    k.reset(s)
}

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
