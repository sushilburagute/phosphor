package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.hash
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Basalt: a hillshaded relief map of dark stone with etched contour lines and mineral specks.
 *
 * The terrain is cached; only the light moves. A low sun circles slowly, so slopes turn from
 * shadow to light and specks glint as the light crosses their facets. Contours are traced
 * with marching squares and drawn as engraved grooves (a shadow on one side, a lit lip on the
 * other). Foreground minerals shift separately as the launcher scrolls.
 */
internal class TextureBasalt(private val k: RenderKit) {
    private val grid = ShadedGrid()
    private var height = FloatArray(0)
    private var nX = FloatArray(0)
    private var nY = FloatArray(0)
    private var baseKey = 0L

    private val minor = FloatArray(MAX_SEGMENTS * 4)
    private val major = FloatArray(MAX_SEGMENTS / 3 * 4)
    private var minorCount = 0
    private var majorCount = 0

    private val specks = Array(3) { FloatArray(SPECKS * 2) }
    private val speckCount = IntArray(3)
    private val stars = FloatArray(MAX_STARS * 8)
    private var starCount = 0

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        val reveal = smooth(f.reveal)
        val cols = (24 + 52 * f.detail).toInt()
        val rows = (cols * h / 1000f).toInt()
        val rebuilt = grid.ensure(cols, rows, 1000f, h, 40f)
        val key = (cols.toLong() shl 20) + h.toLong()
        if (rebuilt || key != baseKey) {
            baseKey = key
            val n = (cols + 1) * (rows + 1)
            height = FloatArray(n); nX = FloatArray(n); nY = FloatArray(n)
            for (at in 0 until n) height[at] = terrain(grid.verts[at * 2], grid.verts[at * 2 + 1])
            normals()
            contours()
        }
        // A low sun sweeps across the strata.
        val az = 0.72f + f.composition * 5f + sin(f.t * 0.16f) * (0.48f + f.amount * 0.64f)
        val el = 0.38f
        val lx = cos(az) * cos(el); val ly = sin(az) * cos(el); val lz = sin(el)
        shade(f, lx, ly, lz, reveal)
        val p = k.fill
        k.normal(p)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        val saved = canvas.save()
        canvas.translate(f.shiftX(0.58f), f.shiftY(0.58f))
        grid.draw(canvas, p)
        val light = canvas.save()
        canvas.translate(520f + 115f * sin(az), h * 0.58f)
        canvas.rotate(-33f)
        k.glow(canvas, k.glowA, 0f, -60f, 1050f, h * 0.19f, ((if (f.night) 61 else 41) * reveal).toInt(), k.modeFor(f))
        k.glow(canvas, k.glowB, 120f, 85f, 890f, h * 0.075f, ((if (f.night) 33 else 26) * reveal).toInt(), k.modeFor(f))
        canvas.restoreToCount(light)
        etch(canvas, f, lx, ly, reveal)
        canvas.restoreToCount(saved)
        glints(canvas, f, az, reveal)
    }

    // region Terrain

    /** Domain-warped value-noise relief, normalized to roughly -1..1. */
    private fun terrain(x: Float, y: Float): Float {
        val wx = x + 80f * noise((x * 0.7f - y * 0.7f) / 380f + 3.1f, (x * 0.7f + y * 0.7f) / 380f, 11)
        val wy = y + 80f * noise((x * 0.9f + y * 0.4f) / 380f, (y * 0.9f - x * 0.4f) / 380f + 7.7f, 23)
        // Stretched octaves follow the grain of the cooled lava.
        val along = wx * 0.84f - wy * 0.54f
        val across = wx * 0.54f + wy * 0.84f
        var sum = noise(along / 820f, across / 400f, 7) * 0.72f
        var amp = 0.54f
        var freq = 1f / 265f
        for (o in 0 until 3) {
            val n = 1f - abs(noise((along * 0.34f + across * o * 0.07f) * freq, across * freq, 101 + o * 37))
            sum += (n * n - 0.45f) * amp
            amp *= 0.5f; freq *= 2.07f
        }
        sum += 0.33f * sin(across * 0.009f + sin(along * 0.0034f) * 0.75f)
        return sum * 0.9f
    }

    private fun noise(x: Float, y: Float, seed: Int): Float {
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        val fx = x - xi; val fy = y - yi
        val u = fx * fx * (3f - 2f * fx); val v = fy * fy * (3f - 2f * fy)
        fun lattice(i: Int, j: Int) = (hash(i * 73856093 xor j * 19349663 xor seed * 83492791) and 65535) / 32767.5f - 1f
        val a = lattice(xi, yi); val b = lattice(xi + 1, yi)
        val c = lattice(xi, yi + 1); val d = lattice(xi + 1, yi + 1)
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
    }

    private fun normals() {
        val g = grid
        val dx = (g.verts[2] - g.verts[0]) * 2f
        val dy = (g.y(1) - g.y(0)) * 2f
        for (j in 0..g.rows) for (i in 0..g.cols) {
            val at = g.index(i, j)
            val l = g.index((i - 1).coerceAtLeast(0), j); val r = g.index((i + 1).coerceAtMost(g.cols), j)
            val u = g.index(i, (j - 1).coerceAtLeast(0)); val d = g.index(i, (j + 1).coerceAtMost(g.rows))
            nX[at] = -(height[r] - height[l]) / dx * RELIEF
            nY[at] = -(height[d] - height[u]) / dy * RELIEF
        }
    }

    /** Marching squares separates minor contours from each fifth, heavier contour. */
    private fun contours() {
        val g = grid
        minorCount = 0; majorCount = 0
        for (j in 0 until g.rows) for (i in 0 until g.cols) {
            val a = g.index(i, j); val b = g.index(i + 1, j)
            val c = g.index(i + 1, j + 1); val d = g.index(i, j + 1)
            val za = height[a]; val zb = height[b]; val zc = height[c]; val zd = height[d]
            val lo = minOf(minOf(za, zb), minOf(zc, zd))
            val hi = maxOf(maxOf(za, zb), maxOf(zc, zd))
            var level = kotlin.math.ceil(lo / STEP).toInt()
            while (level * STEP <= hi) {
                val v = level * STEP
                var n = 0
                // Crossings on the four edges, in order; two or four of them.
                if ((za < v) != (zb < v)) { val t = (v - za) / (zb - za); edgePoint(g, a, b, t, n++) }
                if ((zb < v) != (zc < v)) { val t = (v - zb) / (zc - zb); edgePoint(g, b, c, t, n++) }
                if ((zc < v) != (zd < v)) { val t = (v - zc) / (zd - zc); edgePoint(g, c, d, t, n++) }
                if ((zd < v) != (za < v)) { val t = (v - zd) / (za - zd); edgePoint(g, d, a, t, n++) }
                if (n >= 2) {
                    emit(level, 0)
                    if (n == 4) emit(level, 2)
                }
                level++
            }
        }
    }

    private val ex = FloatArray(4)
    private val ey = FloatArray(4)

    private fun edgePoint(g: ShadedGrid, a: Int, b: Int, t: Float, n: Int) {
        ex[n] = g.verts[a * 2] + (g.verts[b * 2] - g.verts[a * 2]) * t
        ey[n] = g.verts[a * 2 + 1] + (g.verts[b * 2 + 1] - g.verts[a * 2 + 1]) * t
    }

    private fun emit(level: Int, from: Int) {
        val x0 = ex[from]; val y0 = ey[from]; val x1 = ex[from + 1]; val y1 = ey[from + 1]
        if (Math.floorMod(level, 5) == 0) {
            if (majorCount + 4 > major.size) return
            major[majorCount] = x0; major[majorCount + 1] = y0; major[majorCount + 2] = x1; major[majorCount + 3] = y1
            majorCount += 4
        } else {
            if (minorCount + 4 > minor.size) return
            minor[minorCount] = x0; minor[minorCount + 1] = y0; minor[minorCount + 2] = x1; minor[minorCount + 3] = y1
            minorCount += 4
        }
    }

    // endregion

    // region Drawing

    private fun shade(f: Frame, lx: Float, ly: Float, lz: Float, reveal: Float) {
        val g = grid
        val night = f.night
        val dark = if (night) mix(f.c0, Color.BLACK, 0.2f) else mix(f.c0, f.c1, 0.42f)
        val light = if (night) mix(f.c0, f.c1, 0.4f) else f.c0
        for (at in height.indices) {
            val nx = nX[at]; val ny = nY[at]
            val shade = ((nx * lx + ny * ly + lz) / sqrt(nx * nx + ny * ny + 1f)).coerceIn(0f, 1f)
            // Higher ground is slightly lighter, low ground pools darker.
            val lift = 0.82f + 0.18f * (height[at] * 0.5f + 0.5f).coerceIn(0f, 1f)
            val v = (shade.pow(1.35f) * lift * 1.15f).coerceIn(0f, 1f)
            val c = mix(dark, light, v)
            g.colors[at] = mix(if (night) f.c0 else f.c0, c, reveal)
        }
    }

    private fun etch(canvas: Canvas, f: Frame, lx: Float, ly: Float, reveal: Float) {
        val s = k.stroke
        val night = f.night
        s.strokeCap = Paint.Cap.BUTT
        // An engraved groove: dark on the side away from the light, a lit lip toward it.
        val ox = lx * 1.6f; val oy = ly * 1.6f
        for (pass in 0 until 2) {
            val lit = pass == 1
            if (lit) {
                if (night) k.luminous(s, true) else k.normal(s)
                s.color = if (night) mix(f.c1, f.c2, 0.35f) else Color.WHITE
            } else {
                if (night) k.normal(s) else k.blend(s, RenderKit.Mode.INK)
                s.color = if (night) Color.BLACK else f.c2
            }
            canvas.save()
            canvas.translate(if (lit) ox else -ox, if (lit) oy else -oy)
            s.strokeWidth = if (lit) 1f else 1.5f
            s.alpha = ((if (lit) (if (night) 54 else 150) else (if (night) 150 else 110)) * reveal).toInt()
            if (minorCount > 0) canvas.drawLines(minor, 0, minorCount, s)
            s.strokeWidth = if (lit) 1.5f else 2.6f
            s.alpha = ((if (lit) (if (night) 170 else 220) else (if (night) 210 else 200)) * reveal).toInt()
            if (majorCount > 0) canvas.drawLines(major, 0, majorCount, s)
            canvas.restore()
        }

        k.reset(s)
        s.strokeCap = Paint.Cap.ROUND
    }

    /** Mineral specks: dull flecks everywhere, glinting when the sun crosses their facet. */
    private fun glints(canvas: Canvas, f: Frame, az: Float, reveal: Float) {
        speckCount.fill(0)
        starCount = 0
        val count = (SPECKS * (0.35f + f.detail * 0.65f)).toInt()
        for (i in 0 until count) {
            val seed = i * 3301 + 41
            val x = fraction(seed) * 1080f - 40f + f.shiftX(0.92f)
            val y = fraction(seed + 1) * (f.h + 80f) - 40f + f.shiftY(0.92f)
            val facet = fraction(seed + 2) * 6.283f
            val c = cos(az - facet)
            var glint = if (c > 0f) c.pow(36f) else 0f
            glint *= 0.7f + 0.3f * sin(f.t * 1.1f + seed)

            val bucket = when { glint > 0.55f -> 2; glint > 0.18f -> 1; else -> 0 }
            val at = speckCount[bucket] * 2
            specks[bucket][at] = x; specks[bucket][at + 1] = y
            speckCount[bucket]++
            if (glint > 0.7f && starCount < MAX_STARS) {
                val len = 5f + glint.coerceAtMost(1.6f) * 9f
                val o = starCount * 8
                stars[o] = x - len; stars[o + 1] = y; stars[o + 2] = x + len; stars[o + 3] = y
                stars[o + 4] = x; stars[o + 5] = y - len; stars[o + 6] = x; stars[o + 7] = y + len
                starCount++
            }
        }
        val d = k.dots
        val night = f.night
        // Dull flecks first: dark grains by day, faint grey by night.
        k.blend(d, if (night) RenderKit.Mode.SCREEN else RenderKit.Mode.INK)
        d.color = if (night) f.c1 else f.c2
        d.strokeWidth = 1.8f; d.alpha = ((if (night) 60 else 120) * reveal).toInt()
        canvas.drawPoints(specks[0], 0, speckCount[0] * 2, d)
        if (night) k.additive(d, true) else k.normal(d)
        d.color = if (night) f.c2 else Color.WHITE
        d.strokeWidth = 2.4f; d.alpha = ((if (night) 150 else 200) * reveal).toInt()
        canvas.drawPoints(specks[1], 0, speckCount[1] * 2, d)
        d.color = Color.WHITE
        d.strokeWidth = 3.4f; d.alpha = (255 * reveal).toInt()
        canvas.drawPoints(specks[2], 0, speckCount[2] * 2, d)
        if (!night && speckCount[2] > 0) {
            // By day a bright glint needs an inked rim to read on pale stone.
            k.blend(d, RenderKit.Mode.INK)
            d.color = f.c2; d.strokeWidth = 5.2f; d.alpha = (90 * reveal).toInt()
            canvas.drawPoints(specks[2], 0, speckCount[2] * 2, d)
            k.normal(d)
            d.color = Color.WHITE; d.strokeWidth = 3f; d.alpha = (255 * reveal).toInt()
            canvas.drawPoints(specks[2], 0, speckCount[2] * 2, d)
        }
        k.reset(d)
        if (starCount > 0) {
            val s = k.stroke
            if (night) k.additive(s, true) else k.blend(s, RenderKit.Mode.INK)
            s.color = if (night) Color.WHITE else f.c2
            s.strokeWidth = 1f; s.alpha = ((if (night) 200 else 160) * reveal).toInt()
            canvas.drawLines(stars, 0, starCount * 8, s)
            k.reset(s)
        }
    }

    private companion object {
        const val RELIEF = 300f
        const val STEP = 0.055f
        const val MAX_SEGMENTS = 9000
        const val SPECKS = 900
        const val MAX_STARS = 40
    }
}
