package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Event horizon: hundreds of filaments fall down a gravity well and wind into a flattened
 * accretion disc around a dark eye, above the white-hot limb of a planet.
 *
 * Filaments sit at three focus depths: crisp ones on the focal plane, wide soft ones behind
 * and in front, so parallax separates each set of filaments.
 *
 * The eye and its flattened coils sit low in the frame, above the brighter planetary limb.
 */
internal class AbstractHorizon(private val k: RenderKit) {
    // Groups: 0 crisp (focal plane), 1 soft and far, 2 very soft and near, 3 copper accents.
    private val perGroup = intArrayOf(100, 46, 18, 10)
    private val total = perGroup.sum()

    // Per-filament shape, fixed at construction (all hash-derived, so deterministic).
    private val r0 = FloatArray(total)
    private val rIn = FloatArray(total)
    private val lift = FloatArray(total)
    private val spread = FloatArray(total)
    private val turns = FloatArray(total)
    private val phase = FloatArray(total)

    // Segment batches, one array per stretch of a filament (outer fall, middle, inner coil).
    private val lines = Array(RANGES) { FloatArray(total * PER_RANGE * 4) }
    private val px = FloatArray(SEG + 1)
    private val py = FloatArray(SEG + 1)

    private val motes = Array(3) { FloatArray(MOTES * 2) }
    private val moteCount = IntArray(3)
    private val stars = Array(3) { FloatArray(LANE_DOTS * 2) }
    private val starCount = IntArray(3)
    private var ox = 0f
    private var oy = 0f
    private var eyeX = 0f
    private var eyeY = 0f
    private var tilt = 0f

    private val firstOf = IntArray(GROUPS)

    init {
        for (g in 1 until GROUPS) firstOf[g] = firstOf[g - 1] + perGroup[g - 1]
        var i = 0
        for (g in 0 until GROUPS) for (j in 0 until perGroup[g]) {
            val s = g * 997 + j * 131 + 17
            val u = fraction(s + 1)
            // Larger orbits fall from higher up and further left: the funnel's outer wall.
            // Every filament enters the disc through the same narrow throat left of the eye,
            // then settles on its own ring: small and bright, or wide and faint.
            r0[i] = 375f + fraction(s + 2) * 125f
            rIn[i] = 41f + u.pow(1.2f) * 600f + if (g == 1 && fraction(s + 7) < 0.3f) 280f else 0f
            lift[i] = 0.39f + fraction(s + 3) * 0.25f
            spread[i] = -50f + fraction(s + 4) * 520f
            turns[i] = 1.2f + fraction(s + 5) * 0.9f
            phase[i] = (fraction(s + 6) - 0.5f) * 0.3f
            i++
        }
    }

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        val night = f.night
        val reveal = smooth(f.reveal)
        eyeX = 617f + sin(f.t * 0.21f) * 33f * f.amount
        eyeY = h * 0.666f
        tilt = -0.065f + f.composition * 0.50f + sin(f.t * 0.19f) * 0.05f * f.amount
        // Deep-space light: cold behind the funnel wall, a faint warm breath above the limb.
        val mode = k.modeFor(f)
        k.glow(canvas, k.glowA, 170f + f.shiftX(0.2f), h * 0.41f, 580f, h * 0.32f, if (night) 24 else 30, mode)
        k.glow(canvas, k.glowA, eyeX, eyeY, 695f, 116f, if (night) 53 else 33, mode)
        k.glow(canvas, k.glowB, 820f, h * 0.83f, 600f, 150f, if (night) 22 else 30, mode)

        starfield(canvas, f, reveal)
        lanes(canvas, f, reveal)
        filaments(canvas, f, reveal, 1)
        filaments(canvas, f, reveal, 0)
        filaments(canvas, f, reveal, 3)
        particles(canvas, f, reveal)
        copperLine(canvas, f, reveal)
        eye(canvas, f, reveal)
        filaments(canvas, f, reveal, 2)
        limb(canvas, f, reveal)
    }

    // region Field

    /** Position along filament [i] at [s] (0 = top of the fall, 1 = innermost coil), into ox/oy. */
    private fun point(f: Frame, i: Int, s: Float, depth: Float) =
        point(f, i, s, depth, r0[i], rIn[i], lift[i], spread[i], turns[i], phase[i])

    private fun point(
        f: Frame, i: Int, s: Float, depth: Float,
        r0: Float, rIn: Float, lift: Float, spread: Float, turns: Float, phase: Float,
    ) {
        val breathe = 1f + sin(f.t * 0.23f + i * 0.7f) * 0.05f * f.amount
        val u = 1f - s
        val theta = PI.toFloat() + phase + sin(f.t * 0.17f + i * 1.3f) * 0.09f * f.amount -
            s * 2f * PI.toFloat() * turns
        val r = (rIn + (r0 - rIn) * u * u) * breathe
        val dx = cos(theta) * r
        val dy = sin(theta) * r * SQUASH
        val ct = cos(tilt); val st = sin(tilt)
        val u2 = u * u
        val x = eyeX + dx * ct - dy * st - spread * u2
        val y = eyeY + dx * st + dy * ct - f.h * lift * (1f + sin(f.t * 0.19f + i) * 0.04f * f.amount) * u2 * u2
        ox = x + f.shiftX(depth)
        oy = y + f.shiftY(depth)
    }

    private fun filaments(canvas: Canvas, f: Frame, reveal: Float, group: Int) {
        val count = (perGroup[group] * (0.45f + f.detail * 0.55f)).toInt()
        val depth = when (group) { 1 -> 0.45f; 2 -> 1.9f; else -> 1f }
        val first = firstOf[group]
        var c0 = 0; var c1 = 0; var c2 = 0
        // The field grows outward from the eye as the wallpaper wakes.
        val from = ((1f - reveal) * SEG).toInt()
        for (n in 0 until count) {
            val i = first + n
            for (step in 0..SEG) {
                point(f, i, step / SEG.toFloat(), depth)
                px[step] = ox; py[step] = oy
            }
            for (step in from until SEG) {
                val range = (step / PER_RANGE).coerceAtMost(RANGES - 1)
                val arr = lines[range]
                val at = when (range) { 0 -> c0; 1 -> c1; else -> c2 } * 4
                arr[at] = px[step]; arr[at + 1] = py[step]; arr[at + 2] = px[step + 1]; arr[at + 3] = py[step + 1]
                when (range) { 0 -> c0++; 1 -> c1++; else -> c2++ }

            }
        }
        val s = k.stroke
        s.strokeCap = Paint.Cap.BUTT
        k.additive(s, f.night)
        val night = f.night
        for (range in 0 until RANGES) {
            val segments = when (range) { 0 -> c0; 1 -> c1; else -> c2 }
            if (segments == 0) continue
            val inner = range / (RANGES - 1f)
            val copper = group == 3
            s.color = when {
                copper && night -> mix(mix(f.c2, f.c0, 0.35f), mix(f.c2, Color.WHITE, 0.45f), inner)
                copper -> f.c2
                night -> mix(mix(f.c1, f.c0, 0.3f), mix(f.c1, Color.WHITE, 0.62f), inner)
                else -> mix(f.c1, Color.BLACK, inner * 0.25f)
            }
            val a = when {
                copper && night -> 70f + inner * 120f
                copper -> 60f + inner * 80f
                night -> 52f + inner * 92f
                else -> 44f + inner * 64f
            }
            val gain = when (group) { 1 -> 0.24f; 2 -> 0.085f; else -> 1f }
            s.strokeWidth = when (group) { 1 -> 3.6f; 2 -> 13f; 3 -> 1.5f; else -> 1.1f }
            s.alpha = (a * gain * (0.65f + f.amount * 0.35f) * (0.3f + reveal * 0.7f)).toInt().coerceIn(1, 255)
            canvas.drawLines(lines[range], 0, segments * 4, s)
        }
        k.reset(s)
        s.strokeCap = Paint.Cap.ROUND
    }

    /** Sparks riding the filaments inward, and a few drifting off them like dust. */
    private fun particles(canvas: Canvas, f: Frame, reveal: Float) {
        moteCount.fill(0)
        val count = (MOTES * (0.4f + f.detail * 0.6f)).toInt()
        val crisp = perGroup[0]
        for (p in 0 until count) {
            val seed = p * 7919 + 3
            val i = if (p % 9 == 0) crisp + perGroup[1] + perGroup[2] + p % perGroup[3] else p % crisp
            val s = (fraction(seed) + f.t * (0.006f + fraction(seed + 1) * 0.016f)) % 1f
            if (s < 0.08f || s > 1f - (1f - reveal) * 0.9f && reveal < 1f) continue
            point(f, i, s, 1f)
            val bucket = when {
                i >= crisp -> 2
                fraction(seed + 2) < 0.62f -> 0
                fraction(seed + 3) < 0.6f -> 1
                else -> 2
            }
            val at = moteCount[bucket] * 2
            motes[bucket][at] = ox + (fraction(seed + 4) - 0.5f) * 5f
            motes[bucket][at + 1] = oy + (fraction(seed + 5) - 0.5f) * 5f
            moteCount[bucket]++
        }
        val d = k.dots
        k.additive(d, f.night)
        for (b in 0 until 3) {
            if (moteCount[b] == 0) continue
            d.color = when (b) {
                0 -> if (f.night) Color.WHITE else mix(f.c1, Color.BLACK, 0.3f)
                1 -> if (f.night) f.c1 else f.c1
                else -> if (f.night) mix(f.c2, Color.WHITE, 0.2f) else f.c2
            }
            d.strokeWidth = if (b == 0) 2.2f else 2.8f
            d.alpha = ((if (f.night) 190 else 150) * reveal).toInt()
            canvas.drawPoints(motes[b], 0, moteCount[b] * 2, d)
        }
        k.reset(d)
    }

    private fun starfield(canvas: Canvas, f: Frame, reveal: Float) {
        starCount.fill(0)
        val count = (STARS * (0.5f + f.detail * 0.5f)).toInt()
        for (i in 0 until count) {
            val seed = i * 4099 + 77
            val x = fraction(seed) * 1000f + f.shiftX(0.15f)
            val y = fraction(seed + 1) * f.h * 0.8f + f.shiftY(0.15f)
            val tw = 0.5f + 0.5f * sin(f.t * (0.3f + fraction(seed + 2) * 0.5f) + fraction(seed + 3) * 6.28f)
            val bucket = (tw * 2.99f).toInt()
            val at = starCount[bucket] * 2
            stars[bucket][at] = x; stars[bucket][at + 1] = y
            starCount[bucket]++
        }
        val d = k.dots
        k.additive(d, f.night)
        for (b in 0 until 3) {
            d.color = if (f.night) mix(f.c1, Color.WHITE, 0.6f) else f.c1
            d.strokeWidth = 1.4f + b * 0.5f
            d.alpha = ((if (f.night) 50 + b * 70 else 30 + b * 30) * reveal).toInt()
            canvas.drawPoints(stars[b], 0, starCount[b] * 2, d)
        }
        k.reset(d)
    }

    /** Dotted outer lanes of the disc, slowly orbiting: fine grain between the vortex and the limb. */
    private fun lanes(canvas: Canvas, f: Frame, reveal: Float) {
        val count = (LANE_DOTS * (0.4f + f.detail * 0.6f)).toInt()
        starCount.fill(0)
        val ct = cos(tilt); val st = sin(tilt)
        for (n in 0 until count) {
            val seed = n * 2741 + 5
            val lane = (fraction(seed) * 14f).toInt()
            val r = 560f + lane * 46f + fraction(seed + 1) * 10f
            // Outer lanes orbit slower (Kepler-ish), so the dots shear past each other.
            val a = fraction(seed + 2) * 6.283f + f.t * 0.05f * (700f / r).pow(1.5f)
            val dx = cos(a) * r
            val dy = sin(a) * r * SQUASH
            val x = eyeX + dx * ct - dy * st + f.shiftX(0.6f)
            val y = eyeY + dx * st + dy * ct + f.shiftY(0.6f)
            if (x < -10f || x > 1010f) continue
            ox = x; oy = y
            val bucket = (fraction(seed + 3) * 2.99f).toInt()
            val at = starCount[bucket] * 2
            if (at + 1 >= stars[bucket].size) continue
            stars[bucket][at] = ox; stars[bucket][at + 1] = oy
            starCount[bucket]++
        }
        val d = k.dots
        k.additive(d, f.night)
        for (b in 0 until 3) {
            d.color = when (b) { 0 -> f.c1; 1 -> if (f.night) mix(f.c1, Color.WHITE, 0.6f) else f.c1; else -> f.c2 }
            d.strokeWidth = 1.7f
            d.alpha = ((if (f.night) 120 else 90) * reveal).toInt()
            canvas.drawPoints(stars[b], 0, starCount[b] * 2, d)
        }
        k.reset(d)
    }

    // endregion

    // region Focal elements

    /** One filament runs hot: copper with bloom and a slight chromatic split along its edges. */
    private fun copperLine(canvas: Canvas, f: Frame, reveal: Float) {
        val path = k.path
        path.reset()
        val from = ((1f - reveal) * SEG * 2).toInt()
        // Its fall runs through the gap between the first and second icon columns.
        for (step in from..SEG * 2) {
            point(f, 0, step / (SEG * 2f), 1f, 372f, 150f, 0.44f, 30f, 1.6f, 0.0f)
            if (step == from) path.moveTo(ox, oy) else path.lineTo(ox, oy)
        }
        val s = k.stroke
        k.additive(s, f.night)
        val flicker = 0.85f + 0.15f * sin(f.t * 0.8f)
        if (f.night) {
            s.color = f.c2
            s.strokeWidth = 18f; s.alpha = (22 * reveal).toInt(); canvas.drawPath(path, s)
            s.strokeWidth = 6f; s.alpha = (55 * reveal * flicker).toInt(); canvas.drawPath(path, s)
            s.color = RED; s.strokeWidth = 1.2f; s.alpha = (130 * reveal).toInt()
            canvas.save(); canvas.translate(-2.4f, 1.6f); canvas.drawPath(path, s); canvas.restore()
            s.color = f.c1; s.alpha = (150 * reveal).toInt()
            canvas.save(); canvas.translate(2.4f, -1.6f); canvas.drawPath(path, s); canvas.restore()
            s.color = mix(f.c2, Color.WHITE, 0.45f); s.strokeWidth = 2f; s.alpha = (235 * reveal).toInt()
            canvas.drawPath(path, s)
        } else {
            s.color = f.c2
            s.strokeWidth = 7f; s.alpha = (34 * reveal).toInt(); canvas.drawPath(path, s)
            s.color = f.c1; s.strokeWidth = 1.1f; s.alpha = (150 * reveal).toInt()
            canvas.save(); canvas.translate(2.6f, -1.2f); canvas.drawPath(path, s); canvas.restore()
            s.color = f.c2; s.strokeWidth = 2f; s.alpha = (225 * reveal).toInt()
            canvas.drawPath(path, s)
        }
        k.reset(s)
    }

    /** The dark eye, its photon ring, and the lensed far side of the disc arching over it. */
    private fun eye(canvas: Canvas, f: Frame, reveal: Float) {
        val x = eyeX + f.shiftX(1f)
        val y = eyeY + f.shiftY(1f)
        val r = 86f * (0.6f + reveal * 0.4f)
        val p = k.fill
        val s = k.stroke
        val deg = tilt * 180f / PI.toFloat()
        // Lensed arc first, so the eye's shadow cuts it.
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(deg)
        k.additive(s, f.night)
        s.color = if (f.night) mix(f.c1, Color.WHITE, 0.55f) else f.c1
        for (pass in 0 until 2) {
            s.strokeWidth = if (pass == 0) 9f else 1.6f
            s.alpha = ((if (f.night) (if (pass == 0) 30 else 170) else (if (pass == 0) 26 else 150)) * reveal).toInt()
            k.rect.set(-r * 1.75f, -r * 1.62f, r * 1.75f, r * 1.25f)
            canvas.drawArc(k.rect, 192f, 156f, false, s)
            k.rect.set(-r * 1.45f, -r * 0.6f, r * 1.45f, r * 1.42f)
            canvas.drawArc(k.rect, 20f, 140f, false, s)
        }
        // The shadow.
        k.normal(p)
        p.style = Paint.Style.FILL
        p.color = if (f.night) mix(f.c0, Color.BLACK, 0.7f) else mix(f.c1, Color.BLACK, 0.55f)
        p.alpha = (255 * reveal).toInt()
        canvas.drawCircle(0f, 0f, r, p)
        // Photon ring: a thin white-hot circle with a copper fringe outside and blue inside.
        s.strokeWidth = 1.2f
        s.color = f.c1; s.alpha = (170 * reveal).toInt(); canvas.drawCircle(-0.6f, 0.6f, r + 1f, s)
        s.color = f.c2; s.alpha = (170 * reveal).toInt(); canvas.drawCircle(0.6f, -0.6f, r + 4f, s)
        s.color = if (f.night) Color.WHITE else mix(f.c1, Color.BLACK, 0.3f)
        s.strokeWidth = 1.8f; s.alpha = (230 * reveal).toInt(); canvas.drawCircle(0f, 0f, r + 2.5f, s)
        if (f.night) { s.strokeWidth = 12f; s.color = f.c1; s.alpha = (36 * reveal).toInt(); canvas.drawCircle(0f, 0f, r + 6f, s) }
        // The near side of the disc crosses in front of the shadow.
        k.rect.set(-r * 3.1f, -r * 0.5f, r * 3.1f, r * 0.62f)
        s.color = if (f.night) mix(f.c1, Color.WHITE, 0.7f) else mix(f.c1, Color.BLACK, 0.2f)
        s.strokeWidth = 2.2f; s.alpha = (210 * reveal).toInt()
        canvas.drawArc(k.rect, 0f, 180f, false, s)
        s.color = f.c2; s.strokeWidth = 1.2f; s.alpha = (160 * reveal).toInt()
        k.rect.set(-r * 2.6f, -r * 0.36f, r * 2.6f, r * 0.5f)
        canvas.drawArc(k.rect, 8f, 164f, false, s)
        canvas.restore()
        k.reset(p)
        k.reset(s)
    }

    /** The planet's limb: a dark body, a white-hot rim and a thick cold atmosphere with an ember edge. */
    private fun limb(canvas: Canvas, f: Frame, reveal: Float) {
        val h = f.h
        val cx = 840f + f.shiftX(0.7f)
        val cy = h * 0.812f + PLANET + f.shiftY(0.7f)
        val p = k.fill
        val s = k.stroke
        k.normal(p)
        p.style = Paint.Style.FILL
        p.color = if (f.night) mix(f.c0, Color.BLACK, 0.45f) else mix(f.c0, f.c1, 0.06f)
        canvas.drawCircle(cx, cy, PLANET, p)
        k.reset(p)
        // Atmosphere and ember as one smooth radial ramp around the planet, cached per palette.
        val night = f.night
        val key = (f.c0.toLong() * 31 + f.c1) * 31 + f.c2 + if (night) 1 else 0
        if (key != airKey || air == null) { airKey = key; air = atmosphere(f) }
        canvas.save()
        canvas.translate(cx, cy)
        p.shader = air
        p.alpha = (255 * reveal * (0.92f + 0.08f * sin(f.t * 0.5f))).toInt()
        k.additive(p, night)
        canvas.drawCircle(0f, 0f, PLANET + AIR, p)
        k.reset(p)
        // A crisp white-hot rim with a cold fringe just outside it.
        k.additive(s, night)
        s.color = f.c1; s.strokeWidth = 1.2f; s.alpha = (160 * reveal).toInt(); canvas.drawCircle(0f, 0f, PLANET + 5f, s)
        s.color = if (night) Color.WHITE else mix(f.c1, Color.BLACK, 0.45f)
        s.strokeWidth = 1.8f; s.alpha = (240 * reveal).toInt(); canvas.drawCircle(0f, 0f, PLANET + 0.5f, s)
        canvas.restore()
        k.reset(s)
        // Someone standing on the limb, for scale.
        val fx = 170f + f.shiftX(0.7f)
        val fy = cy - sqrt(PLANET * PLANET - (fx - cx) * (fx - cx))
        k.figure(canvas, fx, fy + 1f, 20f, if (night) 0xFF020306.toInt() else mix(f.c1, Color.BLACK, 0.4f), 0.3f)
    }

    private var airKey = 0L
    private var air: Shader? = null

    private fun atmosphere(f: Frame): Shader {
        val o = PLANET + AIR
        fun at(d: Float) = (PLANET + d) / o
        val night = f.night
        val core = if (night) Color.WHITE else mix(f.c1, Color.BLACK, 0.3f)
        val colors = intArrayOf(
            0, 0, alpha(f.c2, if (night) 34 else 40), alpha(f.c2, if (night) 190 else 200),
            alpha(if (night) mix(f.c2, Color.WHITE, 0.5f) else f.c2, 255), alpha(core, 255),
            alpha(if (night) mix(f.c1, Color.WHITE, 0.6f) else f.c1, if (night) 180 else 130),
            alpha(f.c1, if (night) 84 else 54), alpha(f.c1, if (night) 26 else 16), alpha(f.c1, 0),
        )
        val stops = floatArrayOf(0f, at(-80f), at(-30f), at(-7f), at(-1.5f), at(1.5f), at(9f), at(32f), at(84f), 1f)
        return RadialGradient(0f, 0f, o, colors, stops, Shader.TileMode.CLAMP)
    }

    // endregion

    private companion object {
        const val GROUPS = 4
        const val SEG = 57
        const val RANGES = 3
        const val PER_RANGE = 19
        const val SQUASH = 0.162f
        const val MOTES = 900
        const val STARS = 170
        const val LANE_DOTS = 1400
        const val PLANET = 2800f
        const val AIR = 170f
        const val RED = 0xFFFF3B2F.toInt()
    }
}
