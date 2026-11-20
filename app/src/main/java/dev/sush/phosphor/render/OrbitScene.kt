package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

internal class OrbitScene(k: RenderKit, cacheBands: Boolean) {
    private val kit = OrbitKit(k, cacheBands)
    private val exposure = OrbitExposure(kit, cacheBands)
    private val threshold = OrbitThreshold(kit)
    private val ringfall = OrbitRingfall(kit)
    private val firstLight = OrbitFirstLight(kit)
    private val nebula = OrbitNebula(kit)

    fun draw(canvas: Canvas, f: Frame, variant: String) {
        when (variant) {
            "orbit-threshold" -> threshold.draw(canvas, f)
            "orbit-ringfall" -> ringfall.draw(canvas, f)
            "orbit-first-light" -> firstLight.draw(canvas, f)
            "orbit-nebula" -> nebula.draw(canvas, f)
            else -> exposure.draw(canvas, f)
        }
    }

    fun release() {
        exposure.release()
        firstLight.release()
        nebula.release()
        kit.release()
    }
}

/** Shared seeded trail geometry, clip tests and sky layers. */
internal class OrbitKit(val k: RenderKit, private val cacheBands: Boolean) {
    private val trailSets = mutableMapOf<Int, TrailSet>()
    private val clip = Rect()
    private var minRadius = 0f
    private var maxRadius = 0f
    private var clipAngle = 0f
    private var clipHalfAngle = 180f

    /** Bound the clip in the circular coordinates of the tilted, flattened orbital plane. */
    private fun measureVisibleOrbits(canvas: Canvas, flat: Float): Boolean {
        if (!canvas.getClipBounds(clip)) return false
        // Allow for thick highlights, antialiasing and the offset second ink.
        val x0 = clip.left - 12f
        val x1 = clip.right + 12f
        val y0 = (clip.top - 12f) / flat
        val y1 = (clip.bottom + 12f) / flat
        minRadius = hypot(0f.coerceIn(x0, x1), 0f.coerceIn(y0, y1))
        maxRadius = hypot(maxOf(abs(x0), abs(x1)), maxOf(abs(y0), abs(y1)))
        if (minRadius == 0f) {
            clipHalfAngle = 180f
        } else {
            clipAngle = (atan2(y0 + y1, x0 + x1) * 180f / PI).toFloat()
            clipHalfAngle = maxOf(
                angleFromClip(x0, y0), angleFromClip(x1, y0),
                angleFromClip(x0, y1), angleFromClip(x1, y1),
            )
        }
        return true
    }

    private fun angleFromClip(x: Float, y: Float): Float =
        abs(wrappedDegrees((atan2(y, x) * 180f / PI).toFloat() - clipAngle))

    private fun wrappedDegrees(degrees: Float): Float {
        val turn = degrees % 360f
        return when {
            turn > 180f -> turn - 360f
            turn < -180f -> turn + 360f
            else -> turn
        }
    }

    private fun arcInClip(head: Float, sweep: Float): Boolean =
        clipHalfAngle >= 180f || abs(wrappedDegrees(head - sweep * 0.5f - clipAngle)) <= clipHalfAngle + sweep * 0.5f

    private class TrailSet(val count: Int, val night: Boolean, val c0: Int, val c1: Int, val c2: Int) {
        val radius = FloatArray(count)
        val angle = FloatArray(count)
        val velocity = FloatArray(count)
        val sweep = FloatArray(count)
        val width = FloatArray(count)
        val opacity = FloatArray(count)
        val color = IntArray(count)
        val hot = BooleanArray(count)
        var bands: List<OrbitBand>? = null
    }

    fun release() = trailSets.clear()

    private fun trailsFor(f: Frame, count: Int, seed: Int, reach: Float): TrailSet {
        val cached = trailSets[seed]
        if (cached != null && cached.count == count && cached.night == f.night &&
            cached.c0 == f.c0 && cached.c1 == f.c1 && cached.c2 == f.c2) return cached
        val data = TrailSet(count, f.night, f.c0, f.c1, f.c2)
        val hotLow = 0.23f + fraction(seed * 7) * 0.06f
        val hotHigh = 0.52f + fraction(seed * 11) * 0.06f
        for (i in 0 until count) {
            val s = i * 977 + seed * 131
            val u = fraction(s + 1).pow(1.25f)
            val r = 70f + u * reach
            val hot = abs(u - hotLow) < 0.016f || abs(u - hotHigh) < 0.008f
            val nearCore = (1f - u).pow(2.6f)
            val blueBand = (1f - abs(u - hotLow - 0.066f) / 0.077f).coerceAtLeast(0f)
            data.radius[i] = r
            data.angle[i] = fraction(s + 2) * 360f
            data.velocity[i] = 51.57f * (260f / r).pow(1.1f) * (0.8f + fraction(s + 3) * 0.4f)
            data.sweep[i] = 42f + fraction(s + 4).pow(0.7f) * 168f
            data.width[i] = 0.65f + fraction(s + 9).pow(2f) * 1.6f + nearCore * 0.95f + if (hot) 1.2f else 0f
            data.opacity[i] = (if (f.night) 13f else 27f) + nearCore * 83f + blueBand * 96f +
                (if (hot) 147f else 0f) + fraction(s + 8) * 42f
            data.hot[i] = hot
            data.color[i] = when {
                hot -> if (f.night) mix(f.c2, Color.WHITE, fraction(s + 10) * 0.3f) else f.c2
                f.night -> mix(f.c1, Color.WHITE, 0.53f * blueBand + fraction(s + 7) * 0.2f)
                else -> mix(f.c1, f.c0, fraction(s + 7) * 0.35f)
            }
        }
        trailSets[seed] = data
        return data
    }

    /**
     * Draw [count] trails orbiting ([cx], [cy]). [flat] squashes orbits vertically; [seed]
     * keeps the two systems distinct. Arcs get a faint long tail and a brighter short head.
     */
    fun trails(
        canvas: Canvas, f: Frame, cx: Float, cy: Float, flat: Float, tilt: Float,
        count: Int, seed: Int, brightness: Float, reach: Float,
    ) {
        val saved = canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(tilt)
        val reveal = smooth(f.reveal)
        val data = trailsFor(f, if (cacheBands) count else (count * f.detail).toInt(), seed, reach)
        val opacity = brightness * (0.55f + f.amount * 0.45f) * (0.25f + reveal * 0.75f)
        if (cacheBands) {
            val bands = data.bands ?: bakeTrails(data, reach).also { data.bands = it }
            canvas.scale(1f, flat)
            val paint = k.fill
            k.additive(paint, f.night)
            paint.alpha = (opacity * 255).toInt()
            for (i in bands.indices) bands[i].draw(canvas, f.t, paint)
            k.reset(paint)
            canvas.restoreToCount(saved)
            return
        }
        if (!measureVisibleOrbits(canvas, flat)) {
            canvas.restoreToCount(saved)
            return
        }
        val p = k.stroke
        p.shader = null
        k.additive(p, f.night)
        val length = 0.35f + reveal * 0.65f
        for (i in data.radius.indices) {
            val r = data.radius[i]
            if (r < minRadius || r > maxRadius) continue
            val headDeg = data.angle[i] + f.t * data.velocity[i]
            val sweep = data.sweep[i] * length
            if (!arcInClip(headDeg, sweep)) continue
            val a = data.opacity[i] * opacity
            val width = data.width[i]
            k.rect.set(-r, -r * flat, r, r * flat)
            p.color = data.color[i]
            p.alpha = a.toInt().coerceIn(0, 255)
            p.strokeWidth = width
            canvas.drawArc(k.rect, headDeg - sweep, sweep, false, p)
            if (data.hot[i]) {
                // A brighter head on hot trails reads as the leading edge of the exposure.
                p.alpha = (a * 0.8f).toInt().coerceIn(0, 255)
                p.strokeWidth = width * 1.6f
                if (arcInClip(headDeg, sweep * 0.2f)) canvas.drawArc(k.rect, headDeg - sweep * 0.2f, sweep * 0.2f, false, p)
                if (!f.night) {
                    // Second ink, offset like a riso misregistration.
                    canvas.save()
                    canvas.translate(2.6f, 1.8f)
                    p.color = f.c1
                    p.alpha = (a * 0.35f).toInt().coerceIn(0, 255)
                    p.strokeWidth = width
                    canvas.drawArc(k.rect, headDeg - sweep, sweep, false, p)
                    canvas.restore()
                }
            }
        }
        k.reset(p)
        canvas.restoreToCount(saved)
    }

    /** Angle runs across each texture and radius down it. A brighter tip marks the leading edge. */
    private fun bakeTrails(data: TrailSet, reach: Float): List<OrbitBand> {
        val width = 256
        val bands = 16
        val span = reach / bands
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
        k.additive(stroke, data.night)
        return List(bands) { band ->
            val inner = 70f + band * span
            val ink = createBitmap(width, ceil(span).toInt())
            val canvas = Canvas(ink)
            canvas.scale(1f, ink.height / span)
            for (i in data.radius.indices) {
                val y = data.radius[i] - inner
                if (y < -data.width[i] || y > span + data.width[i]) continue
                val head = data.angle[i] * width / 360f
                val sweep = data.sweep[i] * width / 360f
                for (wrap in 0..1) {
                    val x = head + wrap * width
                    stroke.color = data.color[i]
                    stroke.alpha = data.opacity[i].toInt()
                    stroke.strokeWidth = data.width[i]
                    canvas.drawLine(x - sweep, y, x, y, stroke)
                    if (data.hot[i]) {
                        stroke.alpha = (data.opacity[i] * 0.8f).toInt()
                        stroke.strokeWidth *= 1.6f
                        canvas.drawLine(x - sweep * 0.2f, y, x, y, stroke)
                    }
                }
            }
            OrbitBand(ink, inner, inner + span, 51.57f * (260f / (inner + span * 0.5f)).pow(1.1f))
        }
    }

    fun horizonLine(canvas: Canvas, f: Frame, y: Float, reveal: Float, strength: Float) {
        val span = 560f * (0.15f + reveal * 0.85f)
        if (f.night) {
            k.glow(canvas, k.glowB, 500f, y - 6f, span * 1.7f, 46f * strength, (150 * strength).toInt(), RenderKit.Mode.SCREEN)
            k.glow(canvas, k.glowA, 500f, y + 18f, span * 1.8f, 70f * strength, (90 * strength).toInt(), RenderKit.Mode.SCREEN)
            val p = k.stroke
            k.additive(p, true)
            p.color = mix(f.c2, Color.WHITE, 0.25f); p.alpha = (200 * strength).toInt(); p.strokeWidth = 3f
            canvas.drawLine(500f - span * 1.2f, y - 3f, 500f + span * 1.2f, y - 3f, p)
            p.color = Color.WHITE; p.alpha = (220 * strength).toInt(); p.strokeWidth = 1.6f
            canvas.drawLine(500f - span * 1.2f, y, 500f + span * 1.2f, y, p)
            p.color = f.c1; p.alpha = (150 * strength).toInt(); p.strokeWidth = 2f
            canvas.drawLine(500f - span * 1.2f, y + 4f, 500f + span * 1.2f, y + 4f, p)
            k.reset(p)
        } else {
            k.glow(canvas, k.glowB, 500f, y, span * 1.6f, 30f, (70 * strength).toInt(), RenderKit.Mode.INK)
            val p = k.stroke
            k.blend(p, RenderKit.Mode.INK)
            p.color = f.c2; p.alpha = (190 * strength).toInt(); p.strokeWidth = 2.6f
            canvas.drawLine(500f - span * 1.2f, y - 2f, 500f + span * 1.2f, y - 2f, p)
            p.color = f.c1; p.alpha = (170 * strength).toInt(); p.strokeWidth = 1.4f
            canvas.drawLine(500f - span * 1.2f, y + 2.5f, 500f + span * 1.2f, y + 2.5f, p)
            k.reset(p)
        }
    }

    /** Pinpoint stars, each twinkling on its own phase. Day mode prints them as fine ink specks. */
    fun stars(canvas: Canvas, f: Frame, count: Int, top: Float, bottom: Float, depth: Float) {
        val p = k.fill
        p.shader = null
        k.normal(p)
        val n = (count * (0.5f + f.detail * 0.5f)).toInt()
        val reveal = smooth(f.reveal * 1.4f)
        for (i in 0 until n) {
            val x = fraction(i * 13 + 7) * 1040f - 20f + f.shiftX(depth)
            val y = top + fraction(i * 29 + 3) * (bottom - top) + f.shiftY(depth)
            val twinkle = 0.55f + 0.45f * sin(f.t * (1.2f + fraction(i + 99) * 2.5f) + i)
            val size = 0.7f + fraction(i * 5 + 1).pow(3f) * 2.4f
            val tint = fraction(i * 3 + 11)
            p.color = if (f.night) when {
                tint < 0.08f -> f.c2
                tint < 0.22f -> f.c1
                else -> Color.WHITE
            } else if (tint < 0.15f) f.c2 else f.c1
            p.alpha = ((if (f.night) 200f else 120f) * twinkle * reveal).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, size, p)
        }
        p.alpha = 255
    }

    fun meteor(canvas: Canvas, f: Frame, period: Float, offset: Float, top: Float, bottom: Float) {
        val t = f.t * 0.44f
        val cycle = ((t + offset) / period).toInt()
        val p = ((t + offset) % period) / 0.22f
        if (t <= 0f || p >= 1f) return
        val sx = 120f + fraction(cycle * 31 + 5) * 760f
        val sy = top + fraction(cycle * 17 + 2) * (bottom - top)
        val dir = if (fraction(cycle * 7) < 0.5f) -1f else 1f
        val travel = 260f * p
        val hx = sx + dir * travel * 0.92f
        val hy = sy + travel * 0.38f
        val life = sin(p * PI.toFloat())
        val s = k.stroke
        k.additive(s, f.night)
        s.color = if (f.night) mix(f.c1, Color.WHITE, 0.5f) else f.c1
        s.alpha = (life * 110f).toInt()
        s.strokeWidth = 2.6f
        canvas.drawLine(hx - dir * 90f * 0.92f, hy - 90f * 0.38f, hx, hy, s)
        s.color = if (f.night) Color.WHITE else f.c2
        s.alpha = (life * 220f).toInt()
        s.strokeWidth = 1.1f
        canvas.drawLine(hx - dir * 40f * 0.92f, hy - 40f * 0.38f, hx, hy, s)
        k.reset(s)
    }
}
