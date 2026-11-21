package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import androidx.core.graphics.withSave
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.mix
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** A rotating triangular optical prism. The beam intersections share the solid's projection. */
internal class GlassSpectrum(private val k: RenderKit) {
    private val tip = PointF()
    private val left = PointF()
    private val right = PointF()
    private val backTip = PointF()
    private val backLeft = PointF()
    private val backRight = PointF()
    private val entry = PointF()
    private val exit = PointF()
    private val colors = IntArray(7)

    fun draw(canvas: Canvas, f: Frame) {
        val cx = 478f + f.shiftX(0.78f)
        val cy = f.h * 0.54f + f.shiftY(0.78f)
        val scale = minOf(1.18f, f.h / 760f)
        val time = f.t
        val turn = 0.58f + 0.33f * sin(time * 0.24f) * f.amount
        val pitch = 0.27f + 0.105f * cos(time * 0.22f) * f.amount
        val ct = cos(turn)
        val st = sin(turn)
        val cp = cos(pitch)
        val sp = sin(pitch)
        fun project(out: PointF, x: Float, y: Float, z: Float) {
            out.set(x * ct - z * st, y * cp + (x * st + z * ct) * sp)
        }
        project(tip, 0f, -210f, 86f)
        project(left, -207f, 133f, 86f)
        project(right, 207f, 133f, 86f)
        project(backTip, 0f, -210f, -86f)
        project(backLeft, -207f, 133f, -86f)
        project(backRight, 207f, 133f, -86f)

        val scan = sin(time * 0.38f) * f.amount
        val entryHeight = 0.57f + scan * 0.13f
        val exitHeight = 0.62f + scan * 0.10f
        project(entry, -207f * entryHeight, -210f + 343f * entryHeight, 0f)
        project(exit, 207f * exitHeight, -210f + 343f * exitHeight, 0f)

        canvas.withSave {
            translate(cx, cy)
            scale(scale, scale)
            val source = -cx / scale - 24f
            val far = (1000f - cx) / scale + 24f
            val aim = -0.16f + scan * 0.10f
            val sourceY = entry.y + (source - entry.x) * aim
            k.glow(this, k.glowA, -38f, 12f, 540f, 310f, 62, RenderKit.Mode.SCREEN)
            k.glow(this, k.glowB, 110f, 165f, 244f, 13f, 42, RenderKit.Mode.SCREEN)

            // The back triangle is visible through the glass. Its edges carry softer reflections.
            triangle(this, backTip, backLeft, backRight, alpha(f.c1, 33))
            edge(this, backTip, backLeft, alpha(f.c1, 82), 1.7f)
            edge(this, backLeft, backRight, alpha(f.c1, 63), 1.3f)
            edge(this, left, backLeft, alpha(f.c1, 62), 1.5f)

            val white = mix(f.c1, Color.WHITE, 0.84f)
            val flux = 0.82f + sin(time * 0.78f) * 0.18f * f.amount
            beam(this, source, sourceY, entry.x, entry.y, white, flux)
            beam(this, entry.x, entry.y, exit.x, exit.y, mix(f.c1, white, 0.45f), flux)

            // A narrow traveling maximum in the incident light advances inside the crystal.
            val arrival = (time * 0.20f + 0.38f) % 1f
            val incident = arrival < 0.67f
            val u = if (incident) arrival / 0.67f else (arrival - 0.67f) / 0.33f
            val x0 = if (incident) source else entry.x
            val y0 = if (incident) sourceY else entry.y
            val x1 = if (incident) entry.x else exit.x
            val y1 = if (incident) entry.y else exit.y
            val headX = x0 + (x1 - x0) * u
            val headY = y0 + (y1 - y0) * u
            k.glow(this, k.glowWhite, headX, headY, 12f, 4f,
                (140f * sin(u * Math.PI).toFloat() * f.amount).toInt(), RenderKit.Mode.SCREEN)

            triangle(this, tip, left, right, alpha(mix(f.c0, f.c1, 0.52f), 76))
            withSave {
                clipPath(k.path)
                k.glow(this, k.glowA, left.x + 28f, left.y - 28f, 290f, 112f, 101, RenderKit.Mode.SCREEN)
                k.glow(this, k.glowWhite, 38f + scan * 33f, -210f, 118f, 245f, 58, RenderKit.Mode.SCREEN)
            }
            val side = mix(f.c1, f.c2, 0.57f + 0.20f * sin(turn))
            k.path.rewind()
            k.path.moveTo(tip.x, tip.y)
            k.path.lineTo(backTip.x, backTip.y)
            k.path.lineTo(backRight.x, backRight.y)
            k.path.lineTo(right.x, right.y)
            k.path.close()
            k.fill.color = alpha(side, 80)
            drawPath(k.path, k.fill)
            withSave {
                clipPath(k.path)
                k.glow(this, k.glowWhite, backTip.x, backTip.y, 93f, 290f, 130, RenderKit.Mode.SCREEN)
            }

            dispersion(this, f, far, flux, scan)
            edge(this, tip, left, alpha(white, 183), 2.1f)
            edge(this, left, right, alpha(f.c1, 130), 2.8f)
            edge(this, right, tip, alpha(white, 131), 1.5f)
            edge(this, tip, backTip, alpha(white, 225), 2.6f)
            edge(this, backTip, backRight, alpha(side, 166), 2.3f)
            edge(this, right, backRight, alpha(side, 196), 1.6f)
            k.glow(this, k.glowWhite, exit.x, exit.y, 23f, 7f, (185 * flux).toInt(), RenderKit.Mode.SCREEN)
        }
        k.reset(k.stroke)
        k.reset(k.fill)
    }

    private fun dispersion(canvas: Canvas, f: Frame, far: Float, flux: Float, scan: Float) {
        colors[0] = mix(f.c1, 0xFF8059F0.toInt(), 0.79f)
        colors[1] = mix(f.c1, 0xFF4273EF.toInt(), 0.65f)
        colors[2] = f.c1
        colors[3] = mix(f.c1, 0xFF71D78D.toInt(), 0.72f)
        colors[4] = mix(f.c2, 0xFFFFD376.toInt(), 0.74f)
        colors[5] = f.c2
        colors[6] = mix(f.c2, 0xFFEF5973.toInt(), 0.72f)
        val bands = 44
        val spread = 0.54f + f.composition * 0.62f
        val slope = 0.13f + 0.12f * scan
        val distance = far - exit.x
        for (i in 0 until bands) {
            val u = i.toFloat() / bands
            val v = (i + 1f) / bands
            val wavelength = (u + v) * 3f
            val stop = wavelength.toInt().coerceAtMost(colors.lastIndex - 1)
            val color = mix(colors[stop], colors[stop + 1], wavelength - stop)
            val shoulder = 1f - abs(u + v - 1f)
            val brightness = (32f + 118f * (1f - (1f - shoulder) * (1f - shoulder))) * flux
            k.fill.color = alpha(color, brightness.toInt())
            val path = k.path
            path.rewind()
            path.moveTo(exit.x, exit.y + 4f * (u - 0.5f))
            path.lineTo(far, exit.y + distance * (slope + (u - 0.5f) * spread))
            path.lineTo(far, exit.y + distance * (slope + (v - 0.5f) * spread) + 0.45f)
            path.lineTo(exit.x, exit.y + 4f * (v - 0.5f))
            path.close()
            canvas.drawPath(path, k.fill)
        }
    }

    private fun triangle(canvas: Canvas, a: PointF, b: PointF, c: PointF, color: Int) {
        k.path.rewind()
        k.path.moveTo(a.x, a.y)
        k.path.lineTo(b.x, b.y)
        k.path.lineTo(c.x, c.y)
        k.path.close()
        k.fill.color = color
        canvas.drawPath(k.path, k.fill)
    }

    private fun edge(canvas: Canvas, a: PointF, b: PointF, color: Int, width: Float) {
        k.stroke.color = color
        k.stroke.strokeWidth = width
        canvas.drawLine(a.x, a.y, b.x, b.y, k.stroke)
    }

    private fun beam(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, flux: Float) {
        k.stroke.color = alpha(color, (30f * flux).toInt())
        k.stroke.strokeWidth = 8f
        canvas.drawLine(x0, y0, x1, y1, k.stroke)
        k.stroke.color = alpha(color, (218f * flux).toInt())
        k.stroke.strokeWidth = 1.8f
        canvas.drawLine(x0, y0, x1, y1, k.stroke)
    }
}
