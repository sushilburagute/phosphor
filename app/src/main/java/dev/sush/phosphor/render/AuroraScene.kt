package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Aurora as it actually looks: three curtains at different distances, each a ribbon of fine
 * rays hanging from a bright lower hem. The ribbon loops in depth as it travels, so its hem
 * curls, and where it is seen edge-on the rays pile up into bright folds (additive overlap does
 * that for free). Color shifts with altitude: magenta fringe under a white-hot hem, the
 * [Frame.c1] body, [Frame.c2] tops.
 *
 * Rays are batched `drawLines` sharing screen-space vertical gradients, one per hem height
 * class. Each gradient has a fixed dim notch where a launcher label row crosses the curtain,
 * so the bright hem and the upper glow live in the gaps between rows.
 *
 * Night is emitted light; day is the same sky printed as a two-ink halftone on paper.
 */
/**
 * Aurora clock: tuned when animation time ran at a third of today's rate, and aurora should
 * drift rather than dance.
 */
internal val Frame.sky: Float get() = t * 0.34f

internal class AuroraScene(private val k: RenderKit) {
    /**
     * One curtain: a ribbon whose hem moves between [lo] and [hi] (fractions of h; nearer parts
     * sit higher), rays reaching [reach] of h. [ys]/[opacity]/[hues] is the fixed altitude
     * profile above the hot hem zone, top to bottom; its hot zone never rises past [notch].
     */
    private class Curtain(
        val lo: Float,
        val hi: Float,
        val notch: Float,
        val reach: Float,
        val rays: Int,
        val width: Float,
        val depth: Float,
        val fold: Float,
        val folds: Float,
        val speed: Float,
        val phase: Float,
        val strength: Float,
        val ys: FloatArray,
        val opacity: FloatArray,
        val hues: FloatArray,
    ) {
        val shaders = arrayOfNulls<Shader>(CLASSES * LENGTHS)
        fun hemOf(cls: Int) = lo + (hi - lo) * cls / (CLASSES - 1f)
    }

    private val curtains = arrayOf(
        // Far: low on the sky, compressed, partly hidden by the far ridge.
        Curtain(0.643f, 0.70f, 0.632f, 0.14f, 320, 1.6f, 0.35f, 150f, 2.1f, 0.21f, 2.1f, 0.62f,
            floatArrayOf(0.535f, 0.565f, 0.59f, 0.60f, 0.625f, 0.632f),
            floatArrayOf(0f, 0.34f, 0.46f, 0.24f, 0.24f, 0.45f),
            floatArrayOf(1f, 0.9f, 0.7f, 0.5f, 0.3f, 0.2f)),
        // Middle: hangs in the gap above the third label row.
        Curtain(0.535f, 0.594f, 0.512f, 0.19f, 420, 2.2f, 0.6f, 190f, 1.6f, -0.17f, 4.4f, 0.95f,
            floatArrayOf(0.40f, 0.44f, 0.47f, 0.49f, 0.505f, 0.512f),
            floatArrayOf(0f, 0.38f, 0.5f, 0.27f, 0.26f, 0.45f),
            floatArrayOf(1f, 0.9f, 0.7f, 0.5f, 0.4f, 0.3f)),
        // Near: overhead, the tallest, its red-violet tops glowing under the clock.
        Curtain(0.418f, 0.474f, 0.392f, 0.40f, 520, 2.8f, 1f, 230f, 1.35f, 0.13f, 0.7f, 1f,
            floatArrayOf(0.06f, 0.16f, 0.25f, 0.29f, 0.325f, 0.355f, 0.362f, 0.385f, 0.392f),
            floatArrayOf(0f, 0.12f, 0.34f, 0.6f, 0.52f, 0.3f, 0.27f, 0.27f, 0.46f),
            floatArrayOf(1f, 1f, 1f, 0.95f, 0.8f, 0.6f, 0.55f, 0.45f, 0.35f)),
    )

    private val land = AuroraLand(k)
    private val ray = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }

    // Batched geometry, refilled every frame: ray lines per hem class and brightness level.
    private val rayGeom = FloatArray(MAX_RAYS * 4)
    private val rayBucket = ByteArray(MAX_RAYS)
    private val sorted = FloatArray(MAX_RAYS * 4)
    private val counts = IntArray(BUCKETS)
    private val starts = IntArray(BUCKETS)
    private val haze = Array(2) { FloatArray(MAX_RAYS * 2) }
    private val hazeCounts = IntArray(2)
    private val hemB = FloatArray(MAX_RAYS)
    private val hemX = FloatArray(MAX_RAYS)
    private val hemY = FloatArray(MAX_RAYS)
    private var hemCount = 0
    private val hotLines = FloatArray(MAX_RAYS * 4)
    private var hotCount = 0
    private val stopY = FloatArray(BODY_STOPS + 8)
    private val hueTmp = FloatArray(1)
    private val bodyHue = FloatArray(1)

    /** Per-column light of each curtain and its highest hem, for the lake and the day halftone. */
    val bins = Array(3) { FloatArray(BINS) }
    val binHem = Array(3) { FloatArray(BINS) }

    private var key = 0L
    private val sunlight = SolsticeLight(k)

    private fun prepare(f: Frame) {
        val next = ((f.c0.toLong() * 31 + f.c1) * 31 + f.c2) * 31 + f.h.toLong() + if (f.night) 1 else 2
        if (next == key && curtains[0].shaders[0] != null) return
        key = next
        for ((index, c) in curtains.withIndex()) {
            for (cls in 0 until CLASSES) {
                val hem = c.hemOf(cls)
                val hot = hotLength(c, hem)
                for (len in 0 until LENGTHS) {
                    // Each length class fades out over the upper part of its own rays.
                    val top = hem - c.reach * LENGTH[len]
                    val fade = (hem - top) * 0.5f
                    var n = 0
                    val body = hem - hot - 0.003f
                    for (i in 0 until BODY_STOPS) stopY[n++] = top + (body - top) * i / (BODY_STOPS - 1f)
                    for (d in HOT_STOPS) stopY[n++] = hem - d * hot
                    stopY[n++] = hem - 0.008f
                    stopY[n++] = hem
                    stopY[n++] = hem + 0.006f
                    stopY[n++] = hem + 0.018f
                    val bottom = stopY[n - 1]
                    val colors = IntArray(n)
                    val stops = FloatArray(n)
                    for (i in 0 until n) {
                        val o = profile(index, stopY[i], hem, hueTmp) * smooth((stopY[i] - top) / fade)
                        colors[i] = alpha(hue(f, hueTmp[0]), (o * 255f).toInt())
                        stops[i] = ((stopY[i] - top) / (bottom - top)).coerceIn(0f, 1f)
                    }
                    c.shaders[cls * LENGTHS + len] = LinearGradient(0f, top * f.h, 0f, bottom * f.h, colors, stops, Shader.TileMode.CLAMP)
                }
            }
        }
    }

    private fun hotLength(c: Curtain, hem: Float) = minOf(0.034f, hem - c.notch - 0.004f).coerceAtLeast(0.012f)

    /**
     * Opacity (returned) and hue (into [hueOut]) of curtain [c] at height [y] when its hem is
     * at [hem], all fractions of h. Shared by the ray gradients and the day halftone.
     */
    fun profile(c: Int, y: Float, hem: Float, hueOut: FloatArray): Float {
        val cur = curtains[c]
        val hot = hotLength(cur, hem)
        val d = hem - y
        val opacity = when {
            d < -0.018f -> { hueOut[0] = 1.6f; 0f }
            d < -0.006f -> { hueOut[0] = 1.6f; 0.45f * (d + 0.018f) / 0.012f }
            d < 0f -> { val u = (d + 0.006f) / 0.006f; hueOut[0] = 1.6f - 2.6f * u; 0.45f + 0.55f * u }
            d < 0.008f -> { val u = d / 0.008f; hueOut[0] = -1f + u; 1f - 0.05f * u }
            d < hot -> {
                // The hot zone eases into the body profile at its top.
                val u = (d - 0.008f) / (hot - 0.008f).coerceAtLeast(0.001f)
                val body = bodyAt(cur, hem - hot, bodyHue)
                hueOut[0] = bodyHue[0] * u
                0.95f + (body - 0.95f) * u * u
            }
            else -> bodyAt(cur, y, hueOut)
        }
        // Feather the body of the curtain where bright rays overlap a launcher label row.
        val rowDistance = minOf(abs(y - 0.3725f), abs(y - 0.4925f), abs(y - 0.6125f))
        return opacity * (0.60f + 0.40f * smooth((rowDistance - 0.0125f) / 0.018f))
    }

    private fun bodyAt(c: Curtain, y: Float, hueOut: FloatArray): Float {
        val ys = c.ys
        val last = ys.size - 1
        if (y <= ys[0]) { hueOut[0] = c.hues[0]; return 0f }
        if (y >= ys[last]) { hueOut[0] = c.hues[last]; return c.opacity[last] }
        var i = 1
        while (ys[i] < y) i++
        val u = (y - ys[i - 1]) / (ys[i] - ys[i - 1])
        hueOut[0] = c.hues[i - 1] + (c.hues[i] - c.hues[i - 1]) * u
        return c.opacity[i - 1] + (c.opacity[i] - c.opacity[i - 1]) * u
    }

    /** Below 0: white-hot hem toward [Frame.c1]; 0..1: [Frame.c1] toward [Frame.c2]; above 1: the fringe. */
    private fun hue(f: Frame, h: Float): Int = when {
        h < 0f -> if (f.night) mix(f.c1, Color.WHITE, -h * 0.62f) else mix(f.c1, Color.BLACK, -h * 0.25f)
        h <= 1f -> mix(f.c1, f.c2, h)
        else -> mix(mix(f.c1, f.c2, 0.6f), FRINGE, (h - 1f) * if (f.night) 0.8f else 0.4f)
    }

    fun draw(canvas: Canvas, f: Frame, solstice: Boolean) {
        val sunX = 610f + sin(f.t * 0.17f) * 72f * f.amount + f.shiftX(0.45f)
        land.sky(canvas, f, solstice, sunX)
        if (f.night) land.stars(canvas, f)
        if (solstice) {
            sunlight.draw(canvas, f, sunX)
            land.ridges(canvas, f, true)
            land.snow(canvas, f, sunX)
        } else {
            prepare(f)
            for (b in bins) b.fill(0f)
            for (b in binHem) b.fill(f.h)
            for (i in curtains.indices) curtain(canvas, f, i)
            if (!f.night) land.halftone(canvas, f, this)
            land.ridges(canvas, f, false)
            land.lake(canvas, f, this)
        }
    }

    private fun curtain(canvas: Canvas, f: Frame, index: Int) {
        val c = curtains[index]
        val h = f.h
        val t = f.sky
        val n = (c.rays * (0.45f + 0.55f * f.detail)).toInt().coerceAtMost(MAX_RAYS)
        val reveal = smooth(f.reveal)
        val shift = f.shiftX(c.depth)
        val mid = (c.lo + c.hi) * 0.5f * h
        val half = (c.hi - c.lo) * 0.5f * h
        // The overhead vanishing point leans the far rays toward the near curtain.
        val vx = 500f + f.shiftX(0.2f)
        val vy = -h * (1.3f + (1f - c.depth))
        var rays = 0
        hazeCounts.fill(0)
        val nominal = 1300f / n
        var prevX = Float.NaN
        val binsOut = bins[index]
        val hemOut = binHem[index]
        val levelScale = LEVELS / (0.75f + 0.25f * f.amount)
        hemCount = 0
        hotCount = 0

        for (j in 0 until n) {
            val s = j / (n - 1f) * 1.3f - 0.15f
            val u = s * 2f * PI.toFloat() * c.folds / (1f + f.composition * 0.85f)
            // The ribbon loops in depth as it travels: X across, "near" toward the viewer. Where
            // its sideways slope reverses, the curtain folds back over itself and rays pile up.
            // The fold amplitude itself swells and relaxes, so curls wind up and unfurl.
            val a1 = u - t * c.speed + c.phase
            val a2 = u * 0.43f + t * c.speed * 0.37f + c.phase * 1.7f
            val a3 = u * 2.7f - t * c.speed * 1.9f + c.phase * 0.3f
            val fold = c.fold * (0.45f + 0.55f * (0.5f + 0.5f * sin(s * 2.3f + t * 0.06f + c.phase * 2f)))
            val x = s * 1300f - 150f + fold * sin(a1) + c.fold * 0.5f * sin(a2) + fold * 0.12f * sin(a3) +
                40f * sin(t * 0.05f + c.phase) + shift
            val near = (0.62f * cos(a1) + 0.26f * cos(a2 * 1.3f + 0.5f) + 0.12f * cos(a3)).coerceIn(-1f, 1f)
            val y = mid - near * half + (fraction(j * 19 + index) - 0.5f) * 5f

            // Each ray brightens and dims on its own slow clock; patches pulse along the curtain.
            val clock = t * (0.6f + fraction(j * 7 + index) * 0.9f) + fraction(j * 13 + index * 5) * 9f
            val step = floor(clock).toInt()
            val f0 = fraction(j * 31 + step * 977 + index)
            val flick = f0 + (fraction(j * 31 + (step + 1) * 977 + index) - f0) * smooth(clock - step)
            val patch = 0.62f + 0.38f * sin(s * 23f - t * 1.3f + c.phase) * sin(s * 7.1f + t * 0.47f)
            // Long stretches of the curtain fade out entirely, so it hangs in pieces.
            val envelope = smooth(0.7f + 0.8f * sin(s * 3.3f + t * 0.09f + c.phase) * sin(s * 1.3f - t * 0.05f + c.phase * 2f))
            // Where the ribbon is seen edge-on its rays crowd together. Each crowded ray is dimmed
            // a little so the fold glows brighter without collapsing into a pillar.
            val spacing = if (prevX.isNaN()) nominal else abs(x - prevX)
            prevX = x
            val crowd = (spacing / nominal).coerceIn(0.2f, 1.6f).let { sqrt(it) }
            val bright = c.strength * (0.35f + 0.65f * flick) * patch * (0.75f + 0.25f * near) * (0.3f + 0.7f * envelope) * crowd

            // Reveal ignites the curtain from its middle outward, rays growing from the hem.
            if (reveal < 1f && abs(s - 0.5f) * 1.6f > reveal * 1.25f) continue
            hemX[hemCount] = x; hemY[hemCount] = y; hemB[hemCount] = bright; hemCount++
            val reach = h * c.reach * (0.62f + 0.38f * fraction(j * 3 + index * 11)) * (0.75f + 0.25f * patch) *
                (0.7f + 0.3f * near) * (0.6f + 0.4f * envelope) * reveal

            val level = (bright * levelScale).toInt()
            if (level <= 0) continue
            // Quantize to a hem class and a length class: their gradient carries the ray's fade.
            val cls = ((y / h - c.lo) / (c.hi - c.lo) * (CLASSES - 1) + 0.5f).toInt().coerceIn(0, CLASSES - 1)
            val ratio = reach / (h * c.reach)
            val len = if (ratio < 0.62f) 0 else if (ratio < 0.87f) 1 else 2
            val top = (c.hemOf(cls) - c.reach * LENGTH[len] * (if (ratio > 1f) ratio else 1f)) * h
            val slope = (vx - x) / (y - vy) - 0.23f + 0.14f * near
            val topX = x + slope * (y - top)
            val o = rays * 4
            rayGeom[o] = x; rayGeom[o + 1] = y + h * 0.018f; rayGeom[o + 2] = topX; rayGeom[o + 3] = top
            rayBucket[rays] = ((cls * LENGTHS + len) * LEVELS + (level - 1).coerceAtMost(LEVELS - 1)).toByte()
            rays++
            if (j % 2 == 0) {
                // The soft body under the striations: two strengths, so bright stretches glow fuller.
                val hz = if (level >= 3) 1 else 0
                val ho = hazeCounts[hz]
                val hazeTop = (c.hemOf(CLASSES / 2) - c.reach) * h
                haze[hz][ho] = x; haze[hz][ho + 1] = y - h * 0.004f
                haze[hz][ho + 2] = x + slope * (y - hazeTop); haze[hz][ho + 3] = hazeTop
                hazeCounts[hz] = ho + 4
            }
            // The brightest folds burn white at their lower edge.
            if (bright > 1.05f && hotCount + 4 <= hotLines.size) {
                hotLines[hotCount] = x; hotLines[hotCount + 1] = y
                hotLines[hotCount + 2] = x + slope * reach * 0.35f; hotLines[hotCount + 3] = y - reach * 0.35f
                hotCount += 4
            }
            val bin = (x / BIN_W).toInt()
            if (bin in 0 until BINS) {
                binsOut[bin] += bright.coerceAtMost(2.5f)
                if (y < hemOut[bin]) hemOut[bin] = y
            }
        }

        // Group rays by bucket (counting sort) so each bucket is one drawLines call.
        counts.fill(0)
        for (r in 0 until rays) counts[rayBucket[r].toInt()]++
        var acc = 0
        for (b in 0 until BUCKETS) { starts[b] = acc; acc += counts[b] * 4; counts[b] = starts[b] }
        for (r in 0 until rays) {
            val b = rayBucket[r].toInt()
            System.arraycopy(rayGeom, r * 4, sorted, counts[b], 4)
            counts[b] += 4
        }
        val p = ray
        k.additive(p, f.night)
        for (b in 0 until BUCKETS) {
            val count = counts[b] - starts[b]
            if (count == 0) continue
            val level = b % LEVELS
            p.shader = c.shaders[b / LEVELS]
            p.strokeWidth = c.width * (0.8f + level * 0.12f)
            p.alpha = ((if (f.night) LEVEL_NIGHT[level] else LEVEL_DAY[level]) * (0.52f + 0.25f * c.strength)).toInt()
            canvas.drawLines(sorted, starts[b], count, p)
        }
        // Soft body: wide, faint strokes give the curtain volume around the fine rays.
        p.shader = c.shaders[(CLASSES / 2) * LENGTHS + LENGTHS - 1]
        for (hz in 0 until 2) {
            if (hazeCounts[hz] == 0) continue
            p.strokeWidth = c.width * (7f + hz * 2f)
            p.alpha = ((if (f.night) 29 + hz * 24 else 18 + hz * 14) * c.strength).toInt()
            canvas.drawLines(haze[hz], 0, hazeCounts[hz], p)
        }
        if (hotCount > 0) {
            p.shader = null
            p.color = hue(f, -1f)
            p.alpha = if (f.night) 70 else 50
            p.strokeWidth = c.width * 1.4f
            canvas.drawLines(hotLines, 0, hotCount, p)
        }
        k.reset(p)
        hem(canvas, f, c)
    }

    /** The bright lower hem. Where the ribbon folds back, its curls overlap and brighten. */
    private fun hem(canvas: Canvas, f: Frame, c: Curtain) {
        if (hemCount < 2) return
        val path = k.path
        path.reset()
        // Only the lit stretches of the hem glow; dark gaps break the line.
        var on = false
        var j = 0
        while (j < hemCount) {
            val lit = hemB[j] > 0.3f
            if (lit && on) path.lineTo(hemX[j], hemY[j]) else if (lit) path.moveTo(hemX[j], hemY[j])
            on = lit
            j += 2
        }
        val s = k.stroke
        k.additive(s, f.night)
        s.color = hue(f, 0f)
        s.alpha = ((if (f.night) 22 else 14) * c.strength).toInt()
        s.strokeWidth = 18f * c.depth + 8f
        canvas.drawPath(path, s)
        s.color = hue(f, -1f)
        s.alpha = ((if (f.night) 34 else 30) * c.strength).toInt()
        s.strokeWidth = 4f * c.depth + 2f
        canvas.drawPath(path, s)
        k.reset(s)
    }

    fun release() {
        for (c in curtains) c.shaders.fill(null)
        sunlight.release()
        key = 0
        land.release()
    }

    companion object {
        const val MAX_RAYS = 540
        const val CLASSES = 5
        const val LEVELS = 4
        const val LENGTHS = 3
        const val BUCKETS = CLASSES * LENGTHS * LEVELS
        private const val BODY_STOPS = 24
        /** Length classes as fractions of a curtain's reach. */
        private val LENGTH = floatArrayOf(0.5f, 0.75f, 1f)
        const val BINS = 125
        const val BIN_W = 8f
        /** Hot-zone stops as fractions of its length, top first. */
        private val HOT_STOPS = floatArrayOf(1f, 0.6f, 0.3f)
        private val FRINGE = Color.rgb(255, 70, 170)
        private val LEVEL_NIGHT = intArrayOf(60, 100, 150, 210)
        private val LEVEL_DAY = intArrayOf(45, 75, 105, 135)
    }
}
