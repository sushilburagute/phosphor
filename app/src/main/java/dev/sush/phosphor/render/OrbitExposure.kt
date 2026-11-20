package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.abs
import kotlin.math.sin

internal class OrbitExposure(private val orbit: OrbitKit, private val cacheBands: Boolean) {
    private val k = orbit.k
    private var bands: List<OrbitBand> = emptyList()
    private var coolColor = 0
    private var warmColor = 0

    private data class Ring(
        val radius: Float,
        val phase: Float,
        val sweep: Float,
        val width: Float,
        val opacity: Float,
        val heat: Float,
        val frost: Float,
    )

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        // Sits in the gap between typical icon rows, so no label row crosses the bright line.
        val horizon = h * (0.55f - 0.30f * f.composition)
        val reveal = smooth(f.reveal)
        orbit.stars(canvas, f, 150, 0f, horizon, 0.25f)

        orbit.meteor(canvas, f, 17f, 4f, 0f, horizon * 0.85f)

        // Follow one tilted plane across the frame. The compressed reflection repeats its bands.
        val drift = sin(f.t * 0.15f) * f.amount
        val cx = 878f + drift * 40f + f.shiftX(0.72f)
        val cy = horizon - minOf(h * 0.14f, 262f) + drift * 22f + f.shiftY(0.5f)
        val tilt = 18f + drift * 5f + f.shiftX(1f) * 0.065f
        val flat = 0.33f + drift * 0.041f + f.shiftY(1f) * 0.0014f
        k.glow(canvas, k.glowA, 631f, cy, 575f, h * 0.29f, (38 * reveal).toInt(), RenderKit.Mode.SCREEN)

        var saved = canvas.save()
        canvas.clipRect(0f, 0f, 1000f, horizon)
        drawRings(canvas, f, cx, cy, flat, tilt, 1f, reveal)
        canvas.restoreToCount(saved)

        saved = canvas.save()
        canvas.clipRect(0f, horizon, 1000f, h)
        canvas.scale(1f, -0.78f, cx, horizon)
        drawRings(canvas, f, cx + f.shiftX(0.5f), cy, flat, tilt, 0.53f, reveal)
        canvas.restoreToCount(saved)

        strata(canvas, f, horizon, reveal)
        orbit.horizonLine(canvas, f, horizon, reveal, 1f)
        val crossing = 500f + sin(f.t * 0.38f) * 440f * f.amount + f.shiftX(0.9f)
        k.glow(canvas, k.glowB, crossing, horizon, 115f, 13f, (95 * reveal).toInt(), k.modeFor(f))
        k.figure(canvas, 452f + f.shiftX(1f), horizon + h * 0.082f, 21f, 0xFF010204.toInt(), shadow = 0x99000000.toInt())
    }

    private fun drawRings(
        canvas: Canvas, f: Frame, cx: Float, cy: Float, flat: Float, tilt: Float, brightness: Float, reveal: Float,
    ) {
        val saved = canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(tilt)
        if (cacheBands) {
            canvas.scale(1f, flat)
            drawBands(canvas, f, brightness, reveal)
            canvas.restoreToCount(saved)
            return
        }
        val stroke = k.stroke
        k.blend(stroke, RenderKit.Mode.SCREEN)
        val count = (92 + (tracks.size - 92) * f.detail).toInt().coerceAtMost(tracks.size)
        val light = brightness * (0.63f + f.amount * 0.37f) * (0.12f + reveal * 0.88f)
        val ice = mix(f.c1, Color.WHITE, 0.71f)
        for (index in 0 until count) {
            val ring = tracks[index * tracks.size / count]
            val x = ring.radius
            val y = ring.radius * flat
            val phase = ring.phase + f.t * (4.1f + 2100f / x)
            val drift = 0.72f + sin(f.t * 0.36f + ring.phase) * 0.28f * f.amount
            val opacity = ring.opacity * light * drift
            stroke.color = mix(mix(f.c1, ice, ring.frost), f.c2, ring.heat)
            stroke.strokeWidth = ring.width
            stroke.alpha = opacity.toInt()
            // Kilometric bands are nearly continuous, with short brighter streaks circulating.
            k.rect.set(-x, -y, x, y)
            canvas.drawArc(k.rect, phase, ring.sweep, false, stroke)
            if (ring.heat + ring.frost > 0.62f) {
                stroke.alpha = (opacity * 0.46f).toInt()
                val litArc = ring.sweep * 0.28f
                canvas.drawArc(k.rect, phase + ring.sweep - litArc, litArc, false, stroke)
            }
        }
        k.reset(stroke)
        canvas.restoreToCount(saved)
    }

    // Bake the ink in polar coordinates. Each narrow strip wraps around a circular mesh and
    // rotates at its own orbital speed. The reflection reuses the very same textures.
    private fun drawBands(canvas: Canvas, f: Frame, brightness: Float, reveal: Float) {
        if (bands.isEmpty() || coolColor != f.c1 || warmColor != f.c2) {
            coolColor = f.c1
            warmColor = f.c2
            bands = (tracks.indices step TRACKS_PER_BAND).map { first -> createBand(first, f) }
        }
        val light = brightness * (0.63f + f.amount * 0.37f) * (0.12f + reveal * 0.88f)
        val paint = k.fill
        k.blend(paint, RenderKit.Mode.SCREEN)
        for (index in bands.indices) {
            val band = bands[index]
            paint.alpha = (255 * light * (0.72f + sin(f.t * 0.36f + index) * 0.28f * f.amount)).toInt()
            band.draw(canvas, f.t, paint)
        }
        k.reset(paint)
    }

    private fun createBand(first: Int, f: Frame): OrbitBand {
        val end = minOf(first + TRACKS_PER_BAND, tracks.size)
        val spacing = tracks[1].radius - tracks[0].radius
        val inner = tracks[first].radius - spacing * 0.5f
        val outer = tracks[end - 1].radius + spacing * 0.5f
        val ink = createBitmap(ANGLE_TEXELS, (end - first) * 3)
        val canvas = Canvas(ink)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
        val ice = mix(f.c1, Color.WHITE, 0.71f)
        for (i in first until end) {
            val ring = tracks[i]
            val y = (i - first + 0.5f) * 3
            stroke.color = mix(mix(f.c1, ice, ring.frost), f.c2, ring.heat)
            // Compression of the orbital plane brings neighboring fine strokes together.
            stroke.strokeWidth = ring.width * 3f
            val start = ring.phase * ANGLE_TEXELS / 360f
            val sweep = ring.sweep * ANGLE_TEXELS / 360f
            for (wrap in 0..1) {
                val x = start - wrap * ANGLE_TEXELS
                stroke.alpha = ring.opacity.toInt()
                canvas.drawLine(x, y, x + sweep, y, stroke)
                if (ring.heat + ring.frost > 0.62f) {
                    stroke.alpha = (ring.opacity * 0.46f).toInt()
                    canvas.drawLine(x + sweep * 0.72f, y, x + sweep, y, stroke)
                }
            }
        }
        return OrbitBand(ink, inner, outer, 4.1f + 2100f / ((inner + outer) * 0.5f))
    }

    fun release() {
        bands = emptyList()
    }

    /** Distant edge-on trails shimmer in the narrow gap on either side of the horizon. */
    private fun strata(canvas: Canvas, f: Frame, horizon: Float, reveal: Float) {
        val p = k.stroke
        k.luminous(p, f.night)
        val lines = (8 + 24 * f.detail).toInt()
        for (i in 0 until lines) {
            val distance = fraction(i * 37 + 5)
            val direction = if (i % 2 == 0) 1f else -1f
            val y = horizon + direction * (4f + distance * distance * 46f)
            val shimmer = 0.72f + sin(f.t * (0.24f + distance * 0.42f) + i * 2.3f) * 0.28f
            p.color = mix(f.c1, f.c2, distance)
            p.alpha = (48f * (1f - distance) * reveal * shimmer).toInt()
            p.strokeWidth = 0.9f + distance
            canvas.drawLine(0f, y, 1000f, y, p)
        }
        k.reset(p)
    }

    private companion object {
        const val TRACKS_PER_BAND = 40
        const val ANGLE_TEXELS = 192
        val tracks = Array(620) { index ->
            val distance = index.toFloat() / 619
            val seed = index * 31 + 157
            val heat = maxOf(band(distance, 0.56f, 0.046f), 0.85f * band(distance, 0.78f, 0.026f))
            val frost = maxOf(band(distance, 0.16f, 0.15f), band(distance, 0.41f, 0.091f))
            val divisions = maxOf(band(distance, 0.29f, 0.019f), band(distance, 0.49f, 0.023f),
                band(distance, 0.66f, 0.024f), band(distance, 0.83f, 0.017f))
            Ring(
                radius = 132f + distance * 1280f,
                phase = fraction(seed) * 360f,
                sweep = 120f + fraction(seed + 1) * 202f,
                width = 0.66f + fraction(seed + 2) * 0.62f + heat * 0.88f + frost * 0.68f,
                opacity = (22f + fraction(seed + 3) * 33f + heat * 104f + frost * 63f) *
                    (1f - divisions * 0.99f) * (1f - distance * 0.53f),
                heat = heat,
                frost = frost,
            )
        }

        fun band(distance: Float, center: Float, width: Float): Float =
            smooth(1f - abs(distance - center) / width)
    }
}
