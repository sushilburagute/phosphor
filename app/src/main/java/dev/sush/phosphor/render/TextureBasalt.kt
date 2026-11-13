package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.hash
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
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
 * other). Touch changes the terrain itself: a swipe etches a glowing fissure that cools, a
 * held finger heats a magma dome that rises through the contours, and its release cracks out.
 */
internal class TextureBasalt(private val k: RenderKit) {
    private val grid = ShadedGrid()
    private var base = FloatArray(0)
    private var height = FloatArray(0)
    private var nX = FloatArray(0)
    private var nY = FloatArray(0)
    private var baseKey = 0L
    private var shaped = false

    private val minor = FloatArray(MAX_SEGMENTS * 4)
    private val major = FloatArray(MAX_SEGMENTS / 3 * 4)
    private val hot = FloatArray(MAX_SEGMENTS / 3 * 4)
    private var minorCount = 0
    private var majorCount = 0
    private var hotCount = 0

    private val specks = Array(3) { FloatArray(SPECKS * 2) }
    private val speckCount = IntArray(3)
    private val stars = FloatArray(MAX_STARS * 8)
    private var starCount = 0

    private var magma: Shader? = null

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        val reveal = smooth(f.reveal)
        val cols = (24 + 52 * f.detail).toInt()
        val rows = (cols * h / 1000f).toInt()
        val rebuilt = grid.ensure(cols, rows, 1000f, h, 30f)
        val key = (cols.toLong() shl 20) + h.toLong()
        if (rebuilt || key != baseKey) {
            baseKey = key
            val n = (cols + 1) * (rows + 1)
            base = FloatArray(n); height = FloatArray(n); nX = FloatArray(n); nY = FloatArray(n)
            for (at in 0 until n) base[at] = terrain(grid.verts[at * 2], grid.verts[at * 2 + 1])
            shaped = false
        }
        // Terrain only changes under touch; otherwise the cached contours stand.
        val touched = f.busy
        if (touched || !shaped) {
            reshape(f)
            contours(f)
            shaped = !touched
        }
        // A low sun circling slowly.
        val az = 0.6f + f.t * 0.04f
        val el = 0.62f
        val lx = cos(az) * cos(el); val ly = sin(az) * cos(el); val lz = sin(el)
        shade(f, lx, ly, lz, reveal)
        val p = k.fill
        k.normal(p)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        grid.draw(canvas, p)
        etch(canvas, f, lx, ly, reveal)
        glints(canvas, f, az, reveal)
        touch(canvas, f, reveal)
    }

    // region Terrain

    /** Domain-warped value-noise relief, normalized to roughly -1..1. */
    private fun terrain(x: Float, y: Float): Float {
        val wx = x + 70f * noise(x / 380f + 3.1f, y / 380f, 11)
        val wy = y + 70f * noise(x / 380f, y / 380f + 7.7f, 23)
        // Broad swells, then ridged octaves: sharp crests like cooled lava flows.
        var sum = noise(wx / 700f, wy / 700f, 7) * 0.9f
        var amp = 0.75f
        var freq = 1f / 340f
        for (o in 0 until 4) {
            val n = 1f - abs(noise(wx * freq, wy * freq, 101 + o * 37))
            sum += (n * n - 0.45f) * amp
            amp *= 0.5f; freq *= 2.07f
        }
        // A gentle ridge running diagonally gives the map a structure.
        sum += 0.3f * sin((x * 0.6f + y * 0.8f) * 0.0026f)
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

    private fun reshape(f: Frame) {
        val g = grid
        for (at in base.indices) {
            var z = base[at]
            if (f.busy) {
                val x = g.verts[at * 2]; val y = g.verts[at * 2 + 1]
                // The fissure sinks a groove; the magma dome swells under a held finger.
                if (f.trail > 1) z -= 0.22f * f.trailInfluence(x, y, 26f)
                if (f.drag > 0.01f) {
                    val dx = x - f.dragX; val dy = y - f.dragY
                    val r = 120f + f.charge * 60f
                    z += f.drag * (0.08f + f.charge * 0.62f) * exp(-(dx * dx + dy * dy) / (r * r))
                }
                for (i in 0 until f.pulses) {
                    val dx = x - f.pulseX[i]; val dy = y - f.pulseY[i]
                    val q = (sqrt(dx * dx + dy * dy) - f.pulseR[i]) / 50f
                    if (q > -3f && q < 3f) z += exp(-q * q) * sin(q * 3f) * f.pulseA[i] * 0.16f
                }
            }
            height[at] = z
        }
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

    /** Marching squares over the height grid, into minor, major (every fifth) and hot batches. */
    private fun contours(f: Frame) {
        val g = grid
        minorCount = 0; majorCount = 0; hotCount = 0
        val heat = f.drag * f.charge
        val heatR2 = (200f + f.charge * 90f).let { it * it }
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
                    emit(level, 0, heat, heatR2, f)
                    if (n == 4) emit(level, 2, heat, heatR2, f)
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

    private fun emit(level: Int, from: Int, heat: Float, heatR2: Float, f: Frame) {
        val x0 = ex[from]; val y0 = ey[from]; val x1 = ex[from + 1]; val y1 = ey[from + 1]
        if (heat > 0.05f) {
            val dx = (x0 + x1) * 0.5f - f.dragX; val dy = (y0 + y1) * 0.5f - f.dragY
            if (dx * dx + dy * dy < heatR2 && hotCount + 4 <= hot.size) {
                hot[hotCount] = x0; hot[hotCount + 1] = y0; hot[hotCount + 2] = x1; hot[hotCount + 3] = y1
                hotCount += 4
            }
        }
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
        val dark = if (night) mix(f.c0, Color.BLACK, 0.3f) else mix(f.c0, f.c1, 0.68f)
        val light = if (night) mix(f.c0, f.c1, 0.5f) else mix(f.c0, Color.WHITE, 0.7f)
        for (at in height.indices) {
            val nx = nX[at]; val ny = nY[at]
            val shade = ((nx * lx + ny * ly + lz) / sqrt(nx * nx + ny * ny + 1f)).coerceIn(0f, 1f)
            // Higher ground is slightly lighter, low ground pools darker.
            val lift = 0.82f + 0.18f * (height[at] * 0.5f + 0.5f).coerceIn(0f, 1f)
            val v = (shade.pow(1.7f) * lift * 1.25f).coerceIn(0f, 1f)
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
                s.color = if (night) f.c2 else Color.WHITE
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
        if (hotCount > 0) {
            k.additive(s, night)
            val heat = f.drag * f.charge
            s.color = EMBER; s.strokeWidth = 3f; s.alpha = (heat * (if (night) 120 else 90)).toInt()
            canvas.drawLines(hot, 0, hotCount, s)
            s.color = if (night) HOT else EMBER; s.strokeWidth = 1.1f; s.alpha = (heat * 230).toInt()
            canvas.drawLines(hot, 0, hotCount, s)
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
            val x = fraction(seed) * 1000f
            val y = fraction(seed + 1) * f.h
            val facet = fraction(seed + 2) * 6.283f
            val c = cos(az - facet)
            var glint = if (c > 0f) c.pow(36f) else 0f
            glint *= 0.7f + 0.3f * sin(f.t * 1.1f + seed)
            if (f.busy) {
                glint += f.wave(x, y, 70f) * 1.1f
                if (f.drag > 0.01f) glint += f.dragInfluence(x, y, 130f + f.charge * 60f) * f.charge
                if (f.trail > 1) glint += f.trailInfluence(x, y, 40f) * 0.8f
            }
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

    private fun touch(canvas: Canvas, f: Frame, reveal: Float) {
        val night = f.night
        val mode = if (night) RenderKit.Mode.ADD else RenderKit.Mode.INK
        if (f.drag > 0.01f) {
            if (magma == null) magma = RadialGradient(0f, 0f, 1f,
                intArrayOf(HOT, EMBER, alpha(DEEP, 150), alpha(DEEP, 0)), floatArrayOf(0f, 0.18f, 0.5f, 1f), Shader.TileMode.CLAMP)
            val c = f.charge
            val r = 50f + c * 150f
            k.glow(canvas, magma, f.dragX, f.dragY, r, r, ((40 + c * 200) * f.drag * reveal).toInt(), mode)
            if (c > 0.2f) cracks(canvas, f, c)
        }
        if (f.trail > 1) fissure(canvas, f)
        val s = k.stroke
        for (i in 0 until f.pulses) {
            val a = f.pulseA[i].coerceAtMost(1.5f)
            if (a < 0.02f) continue
            // The shock ring: an ember front on a dark trough.
            k.additive(s, night)
            s.color = EMBER; s.strokeWidth = 3f + a * 3f; s.alpha = (a * 90).toInt().coerceAtMost(255)
            canvas.drawCircle(f.pulseX[i], f.pulseY[i], f.pulseR[i], s)
            s.color = if (night) HOT else DEEP; s.strokeWidth = 1.2f; s.alpha = (a * 120).toInt().coerceAtMost(255)
            canvas.drawCircle(f.pulseX[i], f.pulseY[i], f.pulseR[i], s)
        }
        k.reset(s)
    }

    /** Hairline cracks radiating from the magma, lengthening with charge. */
    private fun cracks(canvas: Canvas, f: Frame, c: Float) {
        val s = k.stroke
        k.additive(s, f.night)
        k.path.reset()
        for (n in 0 until 9) {
            val seed = n * 211 + 5
            var a = fraction(seed) * 6.283f
            var x = f.dragX; var y = f.dragY
            val reach = (40f + fraction(seed + 1) * 90f) * (c - 0.2f) / 0.8f * f.drag
            k.path.moveTo(x, y)
            var walked = 0f
            var step = 0
            while (walked < reach) {
                a += (fraction(seed + 7 + step) - 0.5f) * 0.9f
                x += cos(a) * 12f; y += sin(a) * 12f
                k.path.lineTo(x, y)
                walked += 12f; step++
            }
        }
        s.color = EMBER; s.strokeWidth = 4f; s.alpha = (c * 80 * f.drag).toInt()
        canvas.drawPath(k.path, s)
        s.color = if (f.night) HOT else DEEP; s.strokeWidth = 1.2f; s.alpha = (c * 230 * f.drag).toInt()
        canvas.drawPath(k.path, s)
        k.reset(s)
    }

    /** The swipe's fissure: jagged, white-hot where fresh, cooling through ember to dark red. */
    private fun fissure(canvas: Canvas, f: Frame) {
        val s = k.stroke
        val night = f.night
        for (pass in 0 until 3) {
            for (j in 1 until f.trail) {
                val heat = f.trailA[j]
                if (heat < 0.02f) continue
                val x0 = f.trailX[j - 1]; val y0 = f.trailY[j - 1]
                val x1 = f.trailX[j]; val y1 = f.trailY[j]
                val dx = x1 - x0; val dy = y1 - y0
                val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                val nx = -dy / len; val ny = dx / len
                // Jitter is keyed to the ground position so the crack holds still as the wake ages.
                val j0 = jag(x0, y0); val j1 = jag(x1, y1)
                val mx = (x0 + x1) * 0.5f + nx * jag(x0 + x1, y0 + y1) * 1.4f
                val my = (y0 + y1) * 0.5f + ny * jag(x0 + x1, y0 + y1) * 1.4f
                val ax = x0 + nx * j0; val ay = y0 + ny * j0
                val bx = x1 + nx * j1; val by = y1 + ny * j1
                when (pass) {
                    0 -> {
                        k.additive(s, night)
                        s.color = if (heat > 0.5f) EMBER else DEEP
                        s.strokeWidth = 4f + heat * 16f; s.alpha = (heat * (if (night) 70 else 60)).toInt()
                    }
                    1 -> {
                        if (night) k.normal(s) else k.blend(s, RenderKit.Mode.INK)
                        s.color = if (night) Color.BLACK else f.c2
                        s.strokeWidth = 3.2f; s.alpha = ((1f - heat * 0.5f) * (if (night) 200 else 200) * (0.3f + heat)).toInt().coerceAtMost(255)
                    }
                    else -> {
                        k.additive(s, night)
                        s.color = when { heat > 0.7f -> HOT; heat > 0.35f -> EMBER; else -> DEEP }
                        if (!night && heat > 0.7f) s.color = EMBER
                        s.strokeWidth = 0.8f + heat * 1.8f; s.alpha = (80 + heat * 175).toInt()
                    }
                }
                canvas.drawLine(ax, ay, mx, my, s)
                canvas.drawLine(mx, my, bx, by, s)
                // Occasional branch.
                if (pass != 1 && hash(j0.toInt() * 31 + j) % 4 == 0) {
                    val side = if (j0 > 0f) 1f else -1f
                    val bl = 10f + abs(j0) * 3f
                    canvas.drawLine(mx, my, mx + (dx / len * 0.6f + nx * side) * bl, my + (dy / len * 0.6f + ny * side) * bl, s)
                }
            }
        }
        k.reset(s)
    }

    private fun jag(x: Float, y: Float): Float =
        (fraction((x / 9f).toInt() * 7919 + (y / 9f).toInt() * 104729) - 0.5f) * 9f

    // endregion

    private companion object {
        const val RELIEF = 300f
        const val STEP = 0.055f
        const val MAX_SEGMENTS = 9000
        const val SPECKS = 900
        const val MAX_STARS = 40
        const val EMBER = 0xFFFF6A3A.toInt()
        const val HOT = 0xFFFFE6B0.toInt()
        const val DEEP = 0xFF8A1A10.toInt()
    }
}
