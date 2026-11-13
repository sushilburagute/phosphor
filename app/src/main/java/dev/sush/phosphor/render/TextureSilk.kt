package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rose silk: a height field of folds, lit per vertex from a light you can move.
 *
 * The folds are a function of one warped "fold coordinate" q, so the lines where the sheen
 * peaks can be found by a 1-D scan and traced exactly; they are drawn as crisp highlight
 * lines over the softly shaded mesh. A halftone screen follows the shading: dots gather in
 * the mid-tones at night and print the shadows by day.
 *
 * Touch: a held finger is a lamp hovering over the cloth and presses a dimple that deepens
 * with charge (with wrinkles radiating from it) and springs back with a ripple. A swipe drags
 * the folds along and leaves a crease that relaxes as the wake fades.
 */
internal class TextureSilk(private val k: RenderKit, private val halftone: Halftone) {
    private val grid = ShadedGrid()
    private var height = FloatArray(0)
    private val lines = Array(3) { FloatArray(MAX_LINE_FLOATS) }
    private val lineCount = IntArray(3)
    private val peaks = FloatArray(MAX_PEAKS)
    private val crease = Array(3) { FloatArray(TouchField.TRAIL * 4) }
    private val creaseCount = IntArray(3)

    private var dotKey = 0
    private var dotFilter: ColorFilter? = null

    // The dimple springs back past flat when released, then settles.
    private var dimple = 0f
    private var dimpleV = 0f

    // Light direction (toward the light) for the frame, before any finger lamp.
    private var lx = 0f
    private var ly = 0f
    private var lz = 1f

    // Fold-coordinate frame.
    private var ca = 1f
    private var sa = 0f

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        val reveal = smooth(f.reveal)
        springs(f)
        val cols = (24 + 18 * f.detail).toInt()
        val rows = (cols * h / 1000f).toInt()
        if (grid.ensure(cols, rows, 1000f, h, 40f)) height = FloatArray((cols + 1) * (rows + 1))

        val angle = -0.52f + sin(f.t * 0.031f) * 0.05f * f.amount
        ca = cos(angle); sa = sin(angle)
        // A key light from the upper left that swings slowly, like a window moving with the day.
        val az = -2.3f + sin(f.t * 0.05f) * 0.45f * f.amount
        lx = cos(az) * 0.72f; ly = sin(az) * 0.72f; lz = 0.69f

        heights(f)
        shade(f, reveal)
        val p = k.fill
        k.normal(p)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        grid.draw(canvas, p)
        screen(canvas, f)
        highlights(canvas, f, reveal)
        touch(canvas, f, reveal)
    }

    private fun springs(f: Frame) {
        val target = f.drag * (16f + f.charge * 58f)
        when {
            !f.busy && f.dt == 0f -> { dimple = 0f; dimpleV = 0f }
            f.dt == 0f -> { dimple = target; dimpleV = 0f }
            else -> {
                // Under-damped: on release the cloth overshoots into a bulge, then settles.
                dimpleV += ((target - dimple) * 60f - dimpleV * 5f) * f.dt
                dimple += dimpleV * f.dt
            }
        }
    }

    // region Field

    /** The fold coordinate at a point, without touch displacement. */
    private fun fold(f: Frame, x: Float, y: Float): Float {
        val a = x * ca + y * sa
        val b = -x * sa + y * ca
        val bc = b - f.h * 0.45f
        return a + bc * bc * 0.00017f + 46f * sin(b * 0.0021f + f.t * 0.07f)
    }

    /** Fold profile: sharper crests, broader valleys, at three wavelengths. */
    private fun profile(f: Frame, q: Float): Float {
        val t = f.t
        val p1 = q * 0.0124f + t * 0.05f
        val p2 = q * 0.0231f - t * 0.08f + 1.3f
        val p3 = q * 0.0067f + t * 0.03f + 0.4f
        return sin(p1) - 0.24f * cos(2f * p1) + 0.4f * (sin(p2) - 0.2f * cos(2f * p2)) + 0.7f * sin(p3)
    }

    /** Slope of the main folds only; the short ripple shades the cloth but carries no line. */
    private fun slope(f: Frame, q: Float): Float {
        val t = f.t
        val p1 = q * 0.0124f + t * 0.05f
        val p3 = q * 0.0067f + t * 0.03f + 0.4f
        return 0.0124f * (cos(p1) + 0.48f * sin(2f * p1)) + 0.7f * 0.0067f * cos(p3)
    }

    /** Touch displacement of the fold coordinate: the cloth is dragged by a swipe and pulled into a press. */
    private fun displace(f: Frame, x: Float, y: Float): Float {
        if (!f.busy) return 0f
        var d = 0f
        if (f.trail > 1) {
            var j = 0
            while (j < f.trail) {
                val dx = x - f.trailX[j]; val dy = y - f.trailY[j]
                val d2 = dx * dx + dy * dy
                if (d2 < 140f * 140f * 4f) {
                    val w = exp(-d2 / (140f * 140f)) * f.trailA[j]
                    val jj = (j + 1).coerceAtMost(f.trail - 1); val ji = (j - 1).coerceAtLeast(0)
                    val tx = f.trailX[jj] - f.trailX[ji]; val ty = f.trailY[jj] - f.trailY[ji]
                    val tl = sqrt(tx * tx + ty * ty).coerceAtLeast(1f)
                    d -= (tx * ca + ty * sa) / tl * w * 30f
                }
                j += 2
            }
        }
        if (abs(dimple) > 0.5f) {
            val dx = x - f.dragX; val dy = y - f.dragY
            val r = 170f
            d -= (dx * ca + dy * sa) * exp(-(dx * dx + dy * dy) / (r * r)) * dimple * 0.006f
        }
        for (i in 0 until f.pulses) {
            val dx = x - f.pulseX[i]; val dy = y - f.pulseY[i]
            val k = (sqrt(dx * dx + dy * dy) - f.pulseR[i]) / 70f
            if (k > -3f && k < 3f) d += exp(-k * k) * sin(k * 2.6f) * f.pulseA[i] * 14f
        }
        return d
    }

    /** Height at every grid vertex: folds plus the dimple, the crease and the ripples. */
    private fun heights(f: Frame) {
        val g = grid
        for (j in 0..g.rows) for (i in 0..g.cols) {
            val at = g.index(i, j)
            val x = g.verts[at * 2]; val y = g.verts[at * 2 + 1]
            val q = fold(f, x, y) + displace(f, x, y)
            val b = -x * sa + y * ca
            val m = 0.78f + 0.22f * sin(b * 0.0031f + f.t * 0.09f)
            var z = FOLD * m * profile(f, q)
            if (f.busy || dimple != 0f) z += local(f, x, y)
            height[at] = z
        }
    }

    private fun local(f: Frame, x: Float, y: Float): Float {
        var z = 0f
        if (dimple != 0f) {
            val dx = x - f.dragX; val dy = y - f.dragY
            val d2 = dx * dx + dy * dy
            val r = 110f + f.charge * 70f
            val e = exp(-d2 / (r * r))
            z -= dimple * e
            // Wrinkles radiate from a press, strongest on its rim.
            val rim = exp(-d2 / (r * r * 3.2f)) - e * 0.6f
            z += dimple * 0.3f * rim * cos(7f * atan2(dy, dx) + sqrt(d2) * 0.012f)
        }
        if (f.trail > 1) z -= 24f * f.trailInfluence(x, y, 34f)
        for (i in 0 until f.pulses) {
            val dx = x - f.pulseX[i]; val dy = y - f.pulseY[i]
            val k = (sqrt(dx * dx + dy * dy) - f.pulseR[i]) / 64f
            if (k > -3f && k < 3f) z += exp(-k * k) * cos(k * 2.6f) * f.pulseA[i] * 20f
        }
        return z
    }

    // endregion

    // region Shading

    private fun shade(f: Frame, reveal: Float) {
        val g = grid
        val night = f.night
        val dx = (g.verts[2] - g.verts[0]) * 2f
        val dy = (g.y(1) - g.y(0)) * 2f
        val deep = if (night) mix(f.c0, Color.BLACK, 0.4f) else mix(f.c1, f.c2, 0.38f)
        val lit = if (night) f.c1 else f.c0
        val sheen = if (night) f.c2 else Color.WHITE
        val ink = if (night) Color.BLACK else f.c2
        val lamp = f.drag > 0.01f
        for (j in 0..g.rows) for (i in 0..g.cols) {
            val at = g.index(i, j)
            val x = g.verts[at * 2]; val y = g.verts[at * 2 + 1]
            val l = g.index((i - 1).coerceAtLeast(0), j); val r = g.index((i + 1).coerceAtMost(g.cols), j)
            val u = g.index(i, (j - 1).coerceAtLeast(0)); val d = g.index(i, (j + 1).coerceAtMost(g.rows))
            var nx = -(height[r] - height[l]) / dx
            var ny = -(height[d] - height[u]) / dy
            var nz = 1f
            val nl = sqrt(nx * nx + ny * ny + 1f); nx /= nl; ny /= nl; nz /= nl
            var ex = lx; var ey = ly; var ez = lz
            var pool = 1f
            if (lamp) {
                // The finger is a lamp a little above the cloth; its pool of light falls off with distance.
                var fx = f.dragX - x; var fy = f.dragY - y; var fz = 230f
                val fl = sqrt(fx * fx + fy * fy + fz * fz); fx /= fl; fy /= fl; fz /= fl
                val m = f.drag
                ex += (fx - ex) * m; ey += (fy - ey) * m; ez += (fz - ez) * m
                val el = sqrt(ex * ex + ey * ey + ez * ez); ex /= el; ey /= el; ez /= el
                val d2 = (f.dragX - x) * (f.dragX - x) + (f.dragY - y) * (f.dragY - y)
                pool = 1f - m * 0.35f + m * 0.9f * exp(-d2 / (330f * 330f))
            }
            // Wrapped diffuse keeps silk soft; height adds occlusion in the valleys.
            val diffuse = ((nx * ex + ny * ey + nz * ez + 0.08f) / 1.08f).coerceIn(0f, 1f).pow(1.9f)
            val occlusion = 0.25f + 0.75f * smooth(height[at] / (FOLD * 1.7f) * 0.5f + 0.5f)
            var hx = ex; var hy = ey; var hz = ez + 1f
            val hl = sqrt(hx * hx + hy * hy + hz * hz); hx /= hl; hy /= hl; hz /= hl
            val spec = (nx * hx + ny * hy + nz * hz).coerceAtLeast(0f).pow(14f)
            val v = (diffuse * occlusion * pool).coerceIn(0f, 1.2f) * reveal
            val s = (spec * 0.65f * occlusion * pool * reveal).coerceIn(0f, 1f)
            var c = mix(deep, lit, v.coerceAtMost(1f))
            if (!night) c = mix(f.c0, c, reveal)
            g.colors[at] = mix(c, sheen, s)
            // Halftone: mid-tones by night, shadows by day.
            val dots = if (night) 0.04f + 0.36f * 4f * v * (1f - v).coerceAtLeast(0f) else (1f - v).pow(1.5f) * 0.75f * reveal
            g.overlay[at] = alpha(ink, (dots.coerceIn(0f, 1f) * 255f).toInt())
        }
    }

    private fun screen(canvas: Canvas, f: Frame) {
        val p = k.fill
        halftone.ensure(p)
        p.shader = halftone.shader
        p.color = Color.WHITE
        if (!f.night) {
            if (dotKey != f.c2 || dotFilter == null) { dotKey = f.c2; dotFilter = PorterDuffColorFilter(f.c2, PorterDuff.Mode.SRC_IN) }
            p.colorFilter = dotFilter
            k.blend(p, RenderKit.Mode.INK)
        }
        grid.draw(canvas, p, textured = true, colors = grid.overlay)
        p.colorFilter = null
        k.reset(p)
    }

    // endregion

    // region Highlight lines

    /**
     * The sheen peaks where a fold's slope faces halfway between the light and the eye. Scan
     * the fold profile once, then trace each peak across the cloth as a crisp line.
     */
    private fun highlights(canvas: Canvas, f: Frame, reveal: Float) {
        val h = f.h
        // Light for the scan: the key light, or the lamp seen from the middle of the screen.
        var ex = lx; var ey = ly; var ez = lz
        if (f.drag > 0.01f) {
            var fx = f.dragX - 500f; var fy = f.dragY - h * 0.5f; var fz = 400f
            val fl = sqrt(fx * fx + fy * fy + fz * fz); fx /= fl; fy /= fl; fz /= fl
            ex += (fx - ex) * f.drag; ey += (fy - ey) * f.drag; ez += (fz - ez) * f.drag
        }
        var hx = ex; var hy = ey; var hz = ez + 1f
        val hl = sqrt(hx * hx + hy * hy + hz * hz); hx /= hl; hy /= hl; hz /= hl
        val hq = hx * ca + hy * sa

        // q range over the screen.
        var qMin = Float.MAX_VALUE; var qMax = -Float.MAX_VALUE
        for (c in 0..3) {
            val q = fold(f, if (c % 2 == 0) -60f else 1060f, if (c < 2) -60f else h + 60f)
            if (q < qMin) qMin = q; if (q > qMax) qMax = q
        }
        qMin -= 300f; qMax += 300f
        var count = 0
        var prev2 = 0f; var prev1 = 0f
        var q = qMin
        val step = 3f
        while (q < qMax && count < MAX_PEAKS) {
            val sl = slope(f, q) * FOLD
            val n = 1f / sqrt(1f + sl * sl)
            val s = (-sl * hq + hz) * n
            if (q > qMin + 2 * step && prev1 > prev2 && prev1 >= s && prev1 > 0.9f) peaks[count++] = q - step
            prev2 = prev1; prev1 = s
            q += step
        }

        lineCount.fill(0)
        val bMin = -1060f * abs(sa) - 60f
        val bMax = h * ca + 1060f * abs(sa) + 60f
        val stepB = 22f
        for (n in 0 until count) {
            val target = peaks[n]
            var px = 0f; var py = 0f; var have = false
            var b = bMin
            while (b <= bMax) {
                // Solve q(a, b) = target for a: exact for the base folds, then refine for touch.
                val bc = b - h * 0.45f
                val a0 = target - bc * bc * 0.00017f - 46f * sin(b * 0.0021f + f.t * 0.07f)
                var a = a0
                var x = a * ca - b * sa; var y = a * sa + b * ca
                if (f.busy) repeat(2) {
                    a = a0 - displace(f, x, y)
                    x = a * ca - b * sa; y = a * sa + b * ca
                }
                val inside = x > -40f && x < 1040f && y > -40f && y < h + 40f
                if (inside && have) {
                    // Brightness: fold amplitude, a slow shimmer travelling along the line, and the lamp.
                    val m = 0.78f + 0.22f * sin(b * 0.0031f + f.t * 0.09f)
                    var v = m * (0.55f + 0.45f * sin(b * 0.006f - f.t * 0.22f + n * 1.7f))
                    if (f.drag > 0.01f) {
                        val d2 = (x - f.dragX) * (x - f.dragX) + (y - f.dragY) * (y - f.dragY)
                        v = v * (1f - f.drag * 0.5f) + f.drag * 1.2f * exp(-d2 / (300f * 300f))
                    }
                    val bucket = when { v > 0.85f -> 2; v > 0.55f -> 1; else -> 0 }
                    val arr = lines[bucket]
                    val at = lineCount[bucket]
                    if (at + 4 <= arr.size) {
                        arr[at] = px; arr[at + 1] = py; arr[at + 2] = x; arr[at + 3] = y
                        lineCount[bucket] = at + 4
                    }
                }
                px = x; py = y; have = inside
                b += stepB
            }
        }

        val s = k.stroke
        val night = f.night
        if (night) {
            k.additive(s, true)
            for (bucket in 0 until 3) {
                if (lineCount[bucket] == 0) continue
                s.color = f.c1
                s.strokeWidth = 16f; s.alpha = ((12 + bucket * 12) * reveal).toInt()
                canvas.drawLines(lines[bucket], 0, lineCount[bucket], s)
                s.strokeWidth = 5f; s.alpha = ((20 + bucket * 24) * reveal).toInt()
                canvas.drawLines(lines[bucket], 0, lineCount[bucket], s)
                s.color = mix(f.c2, Color.WHITE, 0.4f)
                s.strokeWidth = 1.7f; s.alpha = ((80 + bucket * 75) * reveal).toInt()
                canvas.drawLines(lines[bucket], 0, lineCount[bucket], s)
            }
        } else {
            for (bucket in 0 until 3) {
                if (lineCount[bucket] == 0) continue
                // A fine ink line beside a paper-white shine, like an engraved highlight.
                k.blend(s, RenderKit.Mode.INK)
                s.color = f.c2; s.strokeWidth = 1.1f; s.alpha = ((50 + bucket * 50) * reveal).toInt()
                canvas.save(); canvas.translate(ca * 3f, sa * 3f)
                canvas.drawLines(lines[bucket], 0, lineCount[bucket], s)
                canvas.restore()
                k.normal(s)
                s.color = Color.WHITE; s.strokeWidth = 2.2f; s.alpha = ((90 + bucket * 60) * reveal).toInt()
                canvas.drawLines(lines[bucket], 0, lineCount[bucket], s)
            }
        }
        k.reset(s)
    }

    // endregion

    /** The crease left by a swipe, and the lamp under a held finger. */
    private fun touch(canvas: Canvas, f: Frame, reveal: Float) {
        val night = f.night
        val s = k.stroke
        if (f.trail > 1) {
            creaseCount.fill(0)
            for (j in 1 until f.trail) {
                val a = f.trailA[j]
                val bucket = when { a > 0.6f -> 2; a > 0.25f -> 1; else -> 0 }
                val arr = crease[bucket]
                val at = creaseCount[bucket]
                arr[at] = f.trailX[j - 1]; arr[at + 1] = f.trailY[j - 1]; arr[at + 2] = f.trailX[j]; arr[at + 3] = f.trailY[j]
                creaseCount[bucket] = at + 4
            }
            // Shadow on the side away from the light, a crisp fold of light on the near side.
            val ox = lx * 7f; val oy = ly * 7f
            for (bucket in 0 until 3) {
                if (creaseCount[bucket] == 0) continue
                val fresh = 0.35f + bucket * 0.32f
                k.blend(s, if (night) RenderKit.Mode.NORMAL else RenderKit.Mode.INK)
                s.color = if (night) Color.BLACK else f.c2
                s.strokeWidth = 7f; s.alpha = ((if (night) 120 else 90) * fresh).toInt()
                canvas.save(); canvas.translate(-ox, -oy); canvas.drawLines(crease[bucket], 0, creaseCount[bucket], s); canvas.restore()
                if (night) k.additive(s, true) else k.normal(s)
                s.color = if (night) mix(f.c2, Color.WHITE, 0.5f) else Color.WHITE
                s.strokeWidth = 1.6f; s.alpha = ((if (night) 200 else 220) * fresh).toInt()
                canvas.save(); canvas.translate(ox, oy); canvas.drawLines(crease[bucket], 0, creaseCount[bucket], s); canvas.restore()
            }
            k.reset(s)
        }
        if (f.drag > 0.01f && night) {
            // The lamp itself, small and warm, brightening as the press deepens.
            val c = f.charge
            k.glow(canvas, k.glowWhite, f.dragX, f.dragY, 14f + c * 20f, 14f + c * 20f, ((110 + c * 120) * f.drag * reveal).toInt(), RenderKit.Mode.ADD)
            k.glow(canvas, k.glowB, f.dragX, f.dragY, 90f + c * 60f, 90f + c * 60f, ((30 + c * 40) * f.drag * reveal).toInt(), RenderKit.Mode.ADD)
        }
    }

    private companion object {
        const val FOLD = 105f
        const val MAX_PEAKS = 48
        const val MAX_LINE_FLOATS = 48 * 160 * 4
    }
}
