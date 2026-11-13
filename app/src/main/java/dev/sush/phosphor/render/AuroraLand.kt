package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.hash
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Everything under and around the aurora: stars and meteors, three mountain ridges in
 * atmospheric perspective, and either a lake that breaks the aurora into ripples of light
 * (Northern veil) or a snowfield under a twilight horizon (Solstice). By day it also prints
 * the curtains as a two-ink halftone.
 */
internal class AuroraLand(private val k: RenderKit) {
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }

    // Stars: three tints, four brightness levels, batched as points.
    private val starPts = Array(12) { FloatArray(STARS * 2) }
    private val starCounts = IntArray(12)

    // Lake glints and snow sparkles, four brightness levels.
    private val glint = Array(4) { FloatArray(AuroraScene.BINS * 18 * 4) }
    private val glintCounts = IntArray(4)

    // Day halftone: one level per cell, then points regrouped by dot size.
    private val cellLevel = ByteArray(MAX_CELLS)
    private val cellX = FloatArray(MAX_CELLS)
    private val cellY = FloatArray(MAX_CELLS)
    private val dotPts = FloatArray(MAX_CELLS * 2)
    private val levelCount = IntArray(DOT_LEVELS + 1)
    private val levelStart = IntArray(DOT_LEVELS + 1)
    private val smoothBins = Array(3) { FloatArray(AuroraScene.BINS) }
    private val hueTmp = FloatArray(1)
    private val rowBody = FloatArray(3)

    private var key = 0L
    private val ridgeShaders = arrayOfNulls<Shader>(3)
    private var groundShader: Shader? = null
    private var snowShine: Shader? = null

    private fun prepare(f: Frame, solstice: Boolean) {
        val next = (((f.c0.toLong() * 31 + f.c1) * 31 + f.c2) * 31 + f.h.toLong()) * 4 + (if (f.night) 1 else 2) + if (solstice) 0 else 1000
        if (next == key && groundShader != null) return
        key = next
        val h = f.h
        if (f.night) {
            // Far ridges are hazed with the aurora's light; near ones are nearly black.
            ridgeShaders[0] = LinearGradient(0f, h * 0.62f, 0f, h * 0.72f, mix(f.c0, f.c1, 0.16f), mix(f.c0, f.c1, 0.07f), Shader.TileMode.CLAMP)
            ridgeShaders[1] = LinearGradient(0f, h * 0.66f, 0f, h * 0.75f, mix(f.c0, Color.BLACK, 0.25f), mix(f.c0, f.c1, 0.06f), Shader.TileMode.CLAMP)
            ridgeShaders[2] = LinearGradient(0f, h * 0.71f, 0f, h * 0.78f, mix(f.c0, Color.BLACK, 0.55f), mix(f.c0, Color.BLACK, 0.4f), Shader.TileMode.CLAMP)
            groundShader = if (solstice) {
                LinearGradient(0f, h * 0.74f, 0f, h, intArrayOf(mix(f.c0, mix(f.c1, f.c2, 0.4f), 0.3f), mix(f.c0, f.c1, 0.1f), mix(f.c0, Color.BLACK, 0.1f), mix(f.c0, Color.BLACK, 0.35f)),
                    floatArrayOf(0f, 0.18f, 0.45f, 1f), Shader.TileMode.CLAMP)
            } else {
                LinearGradient(0f, h * 0.745f, 0f, h, intArrayOf(mix(f.c0, f.c1, 0.1f), mix(f.c0, Color.BLACK, 0.1f), mix(f.c0, Color.BLACK, 0.4f)),
                    floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            }
            snowShine = LinearGradient(0f, h * 0.75f, 0f, h * 0.825f, alpha(mix(f.c1, Color.WHITE, 0.25f), 255), alpha(f.c1, 0), Shader.TileMode.CLAMP)
        } else {
            snowShine = LinearGradient(0f, h * 0.75f, 0f, h * 0.825f, alpha(f.c1, 255), alpha(f.c1, 0), Shader.TileMode.CLAMP)
            ridgeShaders[0] = LinearGradient(0f, h * 0.62f, 0f, h * 0.72f, alpha(f.c2, 70), alpha(f.c2, 20), Shader.TileMode.CLAMP)
            ridgeShaders[1] = LinearGradient(0f, h * 0.66f, 0f, h * 0.75f, alpha(f.c1, 70), alpha(f.c1, 26), Shader.TileMode.CLAMP)
            ridgeShaders[2] = LinearGradient(0f, h * 0.71f, 0f, h * 0.78f, alpha(mix(f.c1, f.c2, 0.4f), 96), alpha(mix(f.c1, f.c2, 0.4f), 60), Shader.TileMode.CLAMP)
            groundShader = LinearGradient(0f, h * 0.745f, 0f, h, intArrayOf(alpha(f.c2, 30), alpha(f.c2, 0), alpha(f.c2, 0)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
    }

    /** Airglow behind everything: a faint wash high up and a glowing band along the horizon. */
    fun sky(canvas: Canvas, f: Frame, solstice: Boolean) {
        prepare(f, solstice)
        val h = f.h
        val mode = k.modeFor(f)
        val reveal = smooth(f.reveal)
        k.glow(canvas, k.glowB, 520f, h * 0.32f, 900f, h * 0.2f, ((if (f.night) 34 else 26) * reveal).toInt(), mode)
        k.glow(canvas, k.glowA, 480f, h * 0.55f, 900f, h * 0.06f, ((if (f.night) 30 else 20) * reveal).toInt(), mode)
        if (solstice) {
            // The midsummer sun is just below the horizon: an amber band under the ridges.
            k.glow(canvas, k.glowB, 260f + f.shiftX(0.3f), h * 0.68f, 1100f, h * 0.07f, if (f.night) 170 else 110, mode)
            k.glow(canvas, k.glowWhite, 240f + f.shiftX(0.3f), h * 0.672f, 620f, h * 0.025f, if (f.night) 110 else 60, mode)
        } else {
            k.glow(canvas, k.glowA, 500f, h * 0.665f, 1000f, h * 0.035f, if (f.night) 50 else 30, mode)
        }
    }

    fun stars(canvas: Canvas, f: Frame) {
        val h = f.h
        val n = (STARS * (0.4f + 0.6f * f.detail)).toInt()
        starCounts.fill(0)
        val reveal = smooth(f.reveal)
        for (i in 0 until n) {
            val x = fraction(i * 17 + 3) * 1010f - 5f + f.shiftX(0.12f)
            val y = fraction(i * 23 + 5).let { it * (0.6f + 0.4f * it) } * h * 0.68f + f.shiftY(0.12f)
            val mag = fraction(i * 7 + 11).let { it * it * it }
            val twinkle = 0.7f + 0.3f * sin(f.sky * (1.1f + fraction(i + 99) * 2.6f) + i)
            // Stars are lit in random order as the sky wakes.
            if (fraction(i * 5 + 1) > reveal) continue
            val level = ((0.25f + 0.75f * mag) * twinkle * 4f).toInt().coerceIn(0, 3)
            val tint = hash(i * 3 + 7) % 3
            val b = tint * 4 + level
            val o = starCounts[b]
            starPts[b][o] = x; starPts[b][o + 1] = y
            starCounts[b] = o + 2
        }
        val p = dot
        k.blend(p, RenderKit.Mode.ADD)
        for (tint in 0 until 3) {
            p.color = when (tint) { 0 -> Color.WHITE; 1 -> mix(Color.WHITE, 0xFF9CC8FF.toInt(), 0.7f); else -> mix(Color.WHITE, 0xFFFFD2A0.toInt(), 0.6f) }
            for (level in 0 until 4) {
                val b = tint * 4 + level
                if (starCounts[b] == 0) continue
                p.strokeWidth = 1.5f + level * 0.75f
                p.alpha = 60 + level * 55
                canvas.drawPoints(starPts[b], 0, starCounts[b], p)
            }
        }
        k.reset(p)
        meteor(canvas, f)
    }

    /** Now and then a meteor scratches across the upper sky; deterministic in animation time. */
    private fun meteor(canvas: Canvas, f: Frame) {
        val period = 7.3f
        val n = floor(f.sky / period).toInt()
        val local = f.sky - n * period
        val life = 0.9f
        if (local > life || fraction(n * 13 + 1) < 0.35f) return
        val u = local / life
        val x0 = 120f + 760f * fraction(n * 5 + 2)
        val y0 = f.h * (0.05f + 0.24f * fraction(n * 7 + 3))
        val dir = if (fraction(n * 3 + 4) > 0.5f) 1f else -1f
        val dx = dir * 0.86f; val dy = 0.5f
        val travel = 420f * u
        val tail = 160f * sin(u * 3.1416f)
        val hx = x0 + dx * travel; val hy = y0 + dy * travel
        val s = line
        k.blend(s, RenderKit.Mode.ADD)
        s.color = Color.WHITE
        for (seg in 0 until 6) {
            val a0 = seg / 6f; val a1 = (seg + 1) / 6f
            s.alpha = ((1f - a0) * 200f * sin(u * 3.1416f)).toInt()
            s.strokeWidth = 2.4f * (1f - a0 * 0.7f)
            canvas.drawLine(hx - dx * tail * a0, hy - dy * tail * a0, hx - dx * tail * a1, hy - dy * tail * a1, s)
        }
        k.reset(s)
        k.glow(canvas, k.glowWhite, hx, hy, 14f, 14f, (180f * sin(u * 3.1416f)).toInt(), RenderKit.Mode.ADD)
    }

    private fun noise(x: Float, seed: Int): Float {
        val i = floor(x).toInt()
        val a = fraction(i * 157 + seed)
        val b = fraction((i + 1) * 157 + seed)
        return a + (b - a) * smooth(x - i)
    }

    /** A ridge line: broad massifs, then shoulders, then crags. */
    private fun ridgeY(x: Float, layer: Int, f: Frame): Float {
        val h = f.h
        return when (layer) {
            0 -> h * 0.69f - 110f * noise(x / 210f, 11).let { it * it } - 34f * noise(x / 63f, 17) - 10f * noise(x / 19f, 23)
            1 -> h * 0.722f - 80f * noise(x / 160f + 3f, 31).let { it * it } - 24f * noise(x / 47f, 37) - 8f * noise(x / 13f, 41)
            else -> h * 0.748f - 30f * noise(x / 120f + 7f, 53) - 12f * noise(x / 35f, 59) - 4f * noise(x / 11f, 61)
        }
    }

    fun ridges(canvas: Canvas, f: Frame, solstice: Boolean) {
        val h = f.h
        val p = k.fill
        for (layer in 0 until 3) {
            val depth = 0.45f + layer * 0.35f
            val sx = f.shiftX(depth)
            val sy = f.shiftY(depth * 0.6f)
            val path = k.path
            path.reset()
            path.moveTo(-40f, h + 40f)
            var x = -40f
            while (x <= 1044f) {
                path.lineTo(x + sx, ridgeY(x, layer, f) + sy)
                x += 6f
            }
            path.lineTo(1044f, h + 40f)
            path.close()
            p.shader = ridgeShaders[layer]
            if (!f.night) k.blend(p, RenderKit.Mode.INK)
            canvas.drawPath(path, p)
            k.reset(p)
            // Snow on the far crests catches the aurora.
            if (layer == 0) crest(canvas, f, path, sx, sy)
            if (layer == 2 && !solstice) pines(canvas, f, sx, sy)
        }
    }

    private fun crest(canvas: Canvas, f: Frame, path: android.graphics.Path, sx: Float, sy: Float) {
        path.reset()
        var x = -40f
        while (x <= 1044f) {
            val y = ridgeY(x, 0, f) + sy
            if (x == -40f) path.moveTo(x + sx, y) else path.lineTo(x + sx, y)
            x += 6f
        }
        val s = k.stroke
        k.additive(s, f.night)
        s.color = if (f.night) mix(f.c1, Color.WHITE, 0.4f) else f.c2
        s.alpha = if (f.night) 70 else 60
        s.strokeWidth = 1.3f
        canvas.drawPath(path, s)
        k.reset(s)
    }

    /** A fringe of conifers along the near shore, for scale. */
    private fun pines(canvas: Canvas, f: Frame, sx: Float, sy: Float) {
        val path = k.path
        path.reset()
        val n = (90 * (0.5f + 0.5f * f.detail)).toInt()
        for (i in 0 until n) {
            val x = fraction(i * 29 + 5) * 1040f - 20f
            val ground = ridgeY(x, 2, f) + sy + 4f
            val tall = 7f + 22f * fraction(i * 31 + 9).let { it * it }
            val w = tall * 0.22f
            path.moveTo(x + sx - w, ground)
            path.lineTo(x + sx, ground - tall)
            path.lineTo(x + sx + w, ground)
            path.close()
        }
        val p = k.fill
        p.color = if (f.night) mix(f.c0, Color.BLACK, 0.65f) else alpha(mix(f.c1, f.c2, 0.4f), 120)
        if (!f.night) k.blend(p, RenderKit.Mode.INK)
        canvas.drawPath(path, p)
        k.reset(p)
    }

    /** Light of all three curtains in a column, smoothed, nearest curtain weighted most. */
    private fun columnLight(scene: AuroraScene) {
        for (c in 0 until 3) {
            val src = scene.bins[c]
            val dst = smoothBins[c]
            // A five-tap blur: fold columns stay distinct but lose their hard bin edges.
            for (b in 0 until AuroraScene.BINS) {
                val l1 = src[max(b - 1, 0)]; val r1 = src[min(b + 1, AuroraScene.BINS - 1)]
                val l2 = src[max(b - 2, 0)]; val r2 = src[min(b + 2, AuroraScene.BINS - 1)]
                dst[b] = src[b] * 0.32f + (l1 + r1) * 0.24f + (l2 + r2) * 0.1f
            }
        }
    }

    /** Northern veil: still water that breaks the curtains into horizontal ripples of light. */
    fun lake(canvas: Canvas, f: Frame, scene: AuroraScene) {
        val h = f.h
        val shore = h * 0.752f + f.shiftY(1.15f)
        val p = k.fill
        p.shader = groundShader
        if (!f.night) k.blend(p, RenderKit.Mode.INK)
        canvas.drawRect(-10f, shore, 1010f, h + 10f, p)
        k.reset(p)
        k.glow(canvas, k.glowA, 500f, shore + 20f, 700f, 40f, if (f.night) 40 else 26, k.modeFor(f))

        columnLight(scene)
        glintCounts.fill(0)
        val rows = (16 * (0.5f + 0.5f * f.detail)).toInt()
        val depth = h * 0.84f - shore
        for (b in 0 until AuroraScene.BINS) {
            val light = smoothBins[2][b] + smoothBins[1][b] * 0.7f + smoothBins[0][b] * 0.45f
            if (light < 0.08f) continue
            val cx = b * AuroraScene.BIN_W + AuroraScene.BIN_W * 0.5f + f.shiftX(1.15f)
            for (r in 0 until rows) {
                val u = (r + 0.5f) / rows
                val y = shore + 6f + depth * u * u * 0.8f + depth * u * 0.2f
                // Wind on the water: each ripple flickers and drifts on its own.
                val ripple = 0.5f + 0.5f * sin(f.sky * (1.1f + fraction(r * 7 + b) * 1.4f) + r * 1.9f + b * 0.71f)
                val v = light * 0.42f * ripple * (0.35f + 0.65f * exp(-u * 2.2f)) + f.wave(cx, y, 60f) * 0.6f +
                    (if (f.trail > 0) f.trailInfluence(cx, y, 70f) * 0.8f else 0f)
                val level = (v * 4f).toInt()
                if (level <= 0) continue
                val l = min(level - 1, 3)
                val half = (2.5f + 5f * fraction(r * 131 + b * 7)) * (0.7f + u * 1.2f)
                val jx = sin(f.sky * 0.7f + r * 0.9f + b * 0.13f) * (2f + u * 6f)
                val arr = glint[l]
                val o = glintCounts[l]
                if (o + 4 > arr.size) continue
                arr[o] = cx + jx - half; arr[o + 1] = y; arr[o + 2] = cx + jx + half; arr[o + 3] = y
                glintCounts[l] = o + 4
            }
        }
        val s = line
        k.additive(s, f.night)
        for (l in 0 until 4) {
            if (glintCounts[l] == 0) continue
            s.color = if (f.night) mix(f.c1, Color.WHITE, 0.15f * l) else f.c1
            s.alpha = if (f.night) 55 + l * 50 else 40 + l * 34
            s.strokeWidth = 1.6f + l * 0.35f
            canvas.drawLines(glint[l], 0, glintCounts[l], s)
        }
        k.reset(s)
        // Long faint ripple lines, closer together toward the far shore.
        val rl = k.stroke
        k.additive(rl, f.night)
        rl.color = if (f.night) f.c1 else f.c2
        rl.strokeWidth = 1f
        for (r in 0 until 14) {
            val u = (r + 0.5f) / 14f
            val y = shore + 4f + (h * 0.9f - shore) * u * u
            val x0 = fraction(r * 13 + 1) * 400f - 100f + sin(f.sky * 0.2f + r) * 30f
            rl.alpha = if (f.night) 20 + (8 * (1f - u)).toInt() else 34
            canvas.drawLine(x0, y, x0 + 300f + fraction(r * 13 + 2) * 600f, y, rl)
        }
        k.reset(rl)
        // The waterline itself.
        val st = k.stroke
        k.additive(st, f.night)
        st.color = f.c1
        st.alpha = if (f.night) 70 else 50
        st.strokeWidth = 1.2f
        canvas.drawLine(-10f, shore, 1010f, shore, st)
        k.reset(st)
    }

    /** Solstice: a snowfield lit rose and amber, glinting, with one small watcher. */
    fun snow(canvas: Canvas, f: Frame, scene: AuroraScene) {
        val h = f.h
        val top = h * 0.75f + f.shiftY(1.15f)
        val path = k.path
        path.reset()
        path.moveTo(-40f, h + 40f)
        var x = -40f
        while (x <= 1044f) {
            path.lineTo(x + f.shiftX(1.15f), top + 14f * sin(x * 0.004f + 0.8f) + 6f * sin(x * 0.013f))
            x += 12f
        }
        path.lineTo(1044f, h + 40f)
        path.close()
        val p = k.fill
        p.shader = groundShader
        if (!f.night) k.blend(p, RenderKit.Mode.INK)
        canvas.drawPath(path, p)
        k.reset(p)
        val mode = k.modeFor(f)
        k.glow(canvas, k.glowA, 520f, top + 30f, 760f, 70f, if (f.night) 55 else 30, mode)
        k.glow(canvas, k.glowB, 260f, top + 10f, 600f, 40f, if (f.night) 70 else 36, mode)

        // Snow reflects the curtains diffusely: soft columns of their light under each fold.
        columnLight(scene)
        val sl = line
        k.additive(sl, f.night)
        sl.shader = snowShine
        sl.strokeWidth = AuroraScene.BIN_W * 1.3f
        for (b in 0 until AuroraScene.BINS) {
            val light = smoothBins[2][b] + smoothBins[1][b] * 0.7f + smoothBins[0][b] * 0.4f
            if (light < 0.1f) continue
            val cx = b * AuroraScene.BIN_W + AuroraScene.BIN_W * 0.5f + f.shiftX(1.15f)
            sl.alpha = (light / (1f + light) * if (f.night) 120f else 70f).toInt()
            canvas.drawLine(cx, top + 6f, cx, top + h * 0.075f, sl)
        }
        k.reset(sl)

        // Wind-carved drifts: long faint contour lines.
        val s = k.stroke
        k.additive(s, f.night)
        s.color = if (f.night) f.c1 else f.c2
        s.strokeWidth = 1.1f
        for (i in 0 until 9) {
            val y0 = top + 24f + i * i * 2.6f + i * 6f
            path.reset()
            x = -40f
            while (x <= 1044f) {
                val y = y0 + 9f * sin(x * (0.006f + i * 0.0007f) + i * 1.7f) + 4f * sin(x * 0.021f + i)
                if (x == -40f) path.moveTo(x, y) else path.lineTo(x, y)
                x += 16f
            }
            s.alpha = if (f.night) 46 - i * 3 else 40 - i * 2
            canvas.drawPath(path, s)
        }
        k.reset(s)

        // Glints: snow crystals catching the curtains.
        glintCounts.fill(0)
        val n = (360 * (0.4f + 0.6f * f.detail)).toInt()
        for (i in 0 until n) {
            val gx = fraction(i * 41 + 7) * 1000f
            val u = fraction(i * 43 + 9)
            val gy = top + 18f + u * u * (h * 0.85f - top)
            val b = (gx / AuroraScene.BIN_W).toInt().coerceIn(0, AuroraScene.BINS - 1)
            val light = 0.35f + smoothBins[2][b] * 0.4f + smoothBins[1][b] * 0.25f
            val sparkle = sin(f.sky * (0.9f + fraction(i + 5) * 2f) + i * 2.3f).coerceAtLeast(0f).let { it * it * it * it }
            val v = light * sparkle + f.wave(gx, gy, 70f) * 0.7f + (if (f.trail > 0) f.trailInfluence(gx, gy, 80f) else 0f)
            val level = (v * 4f).toInt()
            if (level <= 0) continue
            val l = min(level - 1, 3)
            val o = glintCounts[l]
            glint[l][o] = gx; glint[l][o + 1] = gy
            glintCounts[l] = o + 2
        }
        val d = dot
        k.additive(d, f.night)
        for (l in 0 until 4) {
            if (glintCounts[l] == 0) continue
            d.color = if (f.night) mix(f.c1, Color.WHITE, 0.3f + l * 0.15f) else f.c1
            d.alpha = 90 + l * 50
            d.strokeWidth = 1.6f + l * 0.6f
            canvas.drawPoints(glint[l], 0, glintCounts[l], d)
        }
        k.reset(d)

        // A small figure watching the sky, footprints trailing back toward the viewer.
        val fx = 655f + f.shiftX(1.2f)
        val fy = h * 0.818f + f.shiftY(1.2f)
        val ink = if (f.night) mix(f.c0, Color.BLACK, 0.7f) else alpha(mix(f.c1, Color.BLACK, 0.35f), 230)
        k.fill.color = ink
        for (i in 1..9) {
            val u = i / 9f
            val px = fx - 14f - u * 190f + (if (i % 2 == 0) 4f else -4f)
            val py = fy + 7f + u * u * h * 0.03f
            k.fill.alpha = (if (f.night) 200 else 120)
            if (!f.night) k.blend(k.fill, RenderKit.Mode.INK)
            canvas.drawOval(px - 2.4f - u * 1.5f, py - 1f - u * 0.5f, px + 2.4f + u * 1.5f, py + 1f + u * 0.5f, k.fill)
        }
        k.reset(k.fill)
        k.figure(canvas, fx, fy, 34f, ink, 0f, if (f.night) 0x66000000 else alpha(f.c1, 50))
    }

    /**
     * Day: the curtains printed as a halftone in two inks. [Frame.c1] carries the low, bright
     * part of each curtain and [Frame.c2] the high glow, on screens of different pitch and a
     * slight misregistration, like a riso print.
     */
    fun halftone(canvas: Canvas, f: Frame, scene: AuroraScene) {
        columnLight(scene)
        val reveal = smooth(f.reveal)
        for (ink in 0 until 2) {
            val pitch = (if (ink == 0) 9f else 10.5f) / sqrt(f.detail.coerceAtLeast(0.35f)).coerceAtLeast(0.6f)
            val offX = if (ink == 0) 0f else 2.6f
            val offY = if (ink == 0) 0f else -1.8f
            val y0 = f.h * 0.06f
            val y1 = f.h * 0.68f
            val rows = ((y1 - y0) / pitch).toInt()
            val cols = (1000f / pitch).toInt() + 2
            levelCount.fill(0)
            var cells = 0
            for (r in 0 until rows) {
                val y = y0 + r * pitch + offY
                val yf = y / f.h
                // Body profile of each curtain at this row; cells near a hem refine it below.
                for (c in 0 until 3) rowBody[c] = inkShare(scene.profile(c, yf, 2f, hueTmp), hueTmp[0], ink)
                val shift = if (r % 2 == 1 && ink == 0) pitch * 0.5f else 0f
                for (col in 0 until cols) {
                    val x = col * pitch + shift + offX
                    val b = (x / AuroraScene.BIN_W).toInt().coerceIn(0, AuroraScene.BINS - 1)
                    var v = 0f
                    for (c in 0 until 3) {
                        val light = smoothBins[c][b]
                        if (light <= 0.01f) continue
                        val hem = scene.binHem[c][b]
                        if (y > hem + f.h * 0.02f) continue
                        v += light * if (hem - y < f.h * 0.04f) inkShare(scene.profile(c, yf, hem / f.h, hueTmp), hueTmp[0], ink) else rowBody[c]
                    }
                    // Fine striation: the rays show through the screen as a gentle column texture.
                    v *= 0.55f * reveal * (0.78f + 0.22f * sin(x * 0.37f + sin(x * 0.051f + f.sky * 0.3f) * 4f))
                    val level = if (v > 0.012f) (sqrt(v.coerceAtMost(1f)) * DOT_LEVELS).toInt().coerceAtLeast(1) else 0
                    if (level <= 0 || cells >= MAX_CELLS) continue
                    cellLevel[cells] = level.toByte()
                    cellX[cells] = x; cellY[cells] = y
                    levelCount[level]++
                    cells++
                }
            }
            var acc = 0
            for (l in 1..DOT_LEVELS) { levelStart[l] = acc; acc += levelCount[l] * 2 }
            for (l in 1..DOT_LEVELS) levelCount[l] = levelStart[l]
            for (i in 0 until cells) {
                val l = cellLevel[i].toInt()
                val o = levelCount[l]
                dotPts[o] = cellX[i]; dotPts[o + 1] = cellY[i]
                levelCount[l] = o + 2
            }
            val d = dot
            d.color = if (ink == 0) f.c1 else f.c2
            k.blend(d, RenderKit.Mode.INK)
            d.alpha = 215
            for (l in 1..DOT_LEVELS) {
                val count = levelCount[l] - levelStart[l]
                if (count <= 0) continue
                d.strokeWidth = pitch * 1.05f * l / DOT_LEVELS
                canvas.drawPoints(dotPts, levelStart[l], count, d)
            }
            k.reset(d)
        }
    }

    /** How much of a profile sample each ink carries: ink 0 the low hue, ink 1 the high one. */
    private fun inkShare(opacity: Float, hue: Float, ink: Int): Float {
        if (opacity <= 0f) return 0f
        val hi = hue.coerceIn(0f, 1f)
        return opacity * if (ink == 0) 1f - hi else hi * if (hue > 1f) 0.5f else 1f
    }

    fun release() {
        key = 0
        groundShader = null
        snowShine = null
        ridgeShaders.fill(null)
    }

    private companion object {
        const val STARS = 460
        const val MAX_CELLS = 16000
        const val DOT_LEVELS = 7
    }
}
