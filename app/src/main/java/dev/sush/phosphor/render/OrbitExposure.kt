package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import androidx.core.graphics.withSave
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.abs
import kotlin.math.sin

internal class OrbitExposure(private val orbit: OrbitKit) {
    private val k = orbit.k
    private class Trails {
        val tail = Path()
        val head = Path()
    }
    private var groups = emptyArray<Trails>()
    private var trackCount = 0

    private data class Ring(
        val radius: Float,
        val phase: Float,
        val sweep: Float,
        val opacity: Float,
        val heat: Float,
        val frost: Float,
    )

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        val horizon = h * (0.71f - 0.26f * f.composition)
        val reveal = smooth(f.reveal)
        // A camera pointed at the celestial pole records nested, incomplete star trails.
        val drift = sin(f.t * 0.13f) * f.amount
        val cx = 382f + drift * 12f + f.shiftX(0.22f)
        val cy = horizon - minOf(h * 0.25f, 480f) + f.shiftY(0.22f)
        val flat = 0.91f
        orbit.stars(canvas, f, 95, 0f, horizon, 0.14f)
        k.glow(canvas, k.glowA, cx, cy, 590f, minOf(h * 0.30f, 535f), (54 * reveal).toInt(), RenderKit.Mode.SCREEN)
        k.glow(canvas, k.glowB, 720f, horizon - 38f, 710f, minOf(235f, h * 0.14f), (106 * reveal).toInt(), RenderKit.Mode.SCREEN)

        var saved = canvas.save()
        canvas.clipRect(0f, 0f, 1000f, horizon)
        drawRings(canvas, f, cx, cy, flat, -13f, 1f, reveal)
        k.glow(canvas, k.glowWhite, cx, cy, 5f, 5f, (185 * reveal).toInt(), RenderKit.Mode.SCREEN)
        canvas.restoreToCount(saved)

        saved = canvas.save()
        canvas.clipRect(0f, horizon, 1000f, h)
        canvas.scale(1f, -0.34f, cx, horizon)
        drawRings(canvas, f, cx + f.shiftX(0.24f), cy, flat, -13f, 0.20f, reveal)
        canvas.restoreToCount(saved)

        val rise = minOf(h * 0.13f, 190f)
        ridge(canvas, f, horizon, rise, far = true)
        ridge(canvas, f, horizon, rise, far = false)
        water(canvas, f, horizon, reveal)
    }

    private fun drawRings(
        canvas: Canvas, f: Frame, cx: Float, cy: Float, flat: Float, tilt: Float, brightness: Float, reveal: Float,
    ) {
        val count = if (f.detail < 0.4f) 140 else if (f.detail < 0.75f) 208 else tracks.size
        if (count != trackCount) prepareTrails(count)
        val light = brightness * (0.63f + f.amount * 0.37f) * (0.12f + reveal * 0.88f)
        val stroke = k.stroke
        k.blend(stroke, RenderKit.Mode.SCREEN)
        canvas.withSave {
            translate(cx, cy)
            rotate(tilt)
            scale(1f, flat)
            rotate(f.t * SKY_RATE)
            for (i in groups.indices) {
                val level = i % 4
                val hue = when (i / 4) {
                    0 -> f.c1
                    1 -> mix(f.c1, Color.WHITE, 0.57f)
                    else -> mix(f.c2, Color.WHITE, 0.17f)
                }
                val pulse = 0.85f + sin(f.t * 0.36f + i) * 0.15f * f.amount
                val opacity = ((42 + 38 * level) * light * pulse).toInt()
                stroke.color = hue
                stroke.alpha = opacity
                stroke.strokeWidth = 0.85f + level * 0.36f
                drawPath(groups[i].tail, stroke)
                stroke.alpha = opacity / 2
                drawPath(groups[i].head, stroke)
            }
        }
        k.reset(stroke)
    }

    // Cache the actual circular arcs. The sky and its reflection use the same paths,
    // so fine trails stay continuous on thumbnail-sized surfaces as well as on the launcher.
    private fun prepareTrails(count: Int) {
        if (groups.isEmpty()) groups = Array(12) { Trails() }
        for (group in groups) {
            group.tail.rewind()
            group.head.rewind()
        }
        for (i in 0 until count) {
            val ring = tracks[i * tracks.size / count]
            val ink = when {
                ring.heat > 0.45f -> 2
                ring.frost > 0.43f -> 1
                else -> 0
            }
            val level = ((ring.opacity - 32f) / 42f).toInt().coerceIn(0, 3)
            val group = groups[4 * ink + level]
            val r = ring.radius
            group.tail.addArc(-r, -r, r, r, ring.phase, ring.sweep)
            val head = ring.sweep * 0.28f
            group.head.addArc(-r, -r, r, r, ring.phase + ring.sweep - head, head)
        }
        trackCount = count
    }

    fun release() {
        groups = emptyArray()
        trackCount = 0
    }

    /** Two ridges meet at the shore. Their peaks form a saddle around the last light. */
    private fun ridge(canvas: Canvas, f: Frame, horizon: Float, rise: Float, far: Boolean) {
        val path = k.path
        val shift = f.shiftX(if (far) 0.44f else 1.05f)
        val lift = f.shiftY(if (far) 0.42f else 0.76f)
        path.rewind()
        path.moveTo(-100f, horizon + 4f)
        for (i in 0..150) {
            val x = i * 8f - 100f
            val u = (x - shift) / 1000f
            val main = if (far) 1.05f - abs(u - 0.25f) * 3.8f else 0.80f - abs(u - 0.93f) * 3.2f
            val flank = if (far) 0.72f - abs(u - 0.53f) * 4f else 0.56f - abs(u - 0.08f) * 2.6f
            val ridge = maxOf(0.04f, main, flank) + 0.054f * sin(u * 52f) + 0.022f * sin(u * 119f)
            path.lineTo(x, minOf(horizon + 1f, horizon - rise * (ridge + if (far) 0.12f else 0f) + lift))
        }
        path.lineTo(1100f, horizon + 4f)
        path.close()
        k.fill.color = if (far) mix(f.c0, f.c1, 0.12f) else mix(f.c0, Color.BLACK, 0.3f)
        canvas.drawPath(path, k.fill)
    }

    private fun water(canvas: Canvas, f: Frame, horizon: Float, reveal: Float) {
        val p = k.stroke
        k.luminous(p, f.night)
        val lines = (56 + 70 * f.detail).toInt()
        for (i in 0 until lines) {
            val distance = fraction(i * 37 + 5)
            val y = horizon + 5f + distance * distance * (f.h - horizon)
            val ripple = sin(f.t * 0.38f + i * 2.3f) * f.amount
            val x = fraction(i * 23 + 89) * 1050f + ripple * (3f + distance * 11f) + f.shiftX(0.6f)
            val warm = smooth(1f - abs(x - 710f) / 290f)
            val span = (3f + distance * 24f) * (0.35f + fraction(i + 23))
            p.color = mix(f.c1, f.c2, warm)
            p.alpha = ((13f + warm * 51f) * (1f - distance * 0.8f) * reveal * (0.73f + ripple * 0.27f)).toInt()
            p.strokeWidth = 0.9f + distance
            canvas.drawLine(x - span, y, x + span, y, p)
        }
        k.reset(p)
    }

    private companion object {
        const val SKY_RATE = 3.4f
        val tracks = Array(286) { index ->
            val distance = index.toFloat() / 285
            val seed = index * 31 + 157
            val heat = smooth((fraction(seed + 5) - 0.65f) * 5f)
            val frost = fraction(seed + 8) * 0.8f
            Ring(
                radius = 12f + distance * 1290f,
                phase = fraction(seed) * 360f,
                sweep = 26f + fraction(seed + 1) * 129f,
                opacity = (52f + fraction(seed + 3) * 145f) * (1f - distance * 0.40f),
                heat = heat,
                frost = frost,
            )
        }

    }
}
