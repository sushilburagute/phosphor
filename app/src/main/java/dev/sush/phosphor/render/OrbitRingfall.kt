package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import dev.sush.phosphor.render.OrbitKit.Companion.BLUE
import dev.sush.phosphor.render.OrbitKit.Companion.DEG
import dev.sush.phosphor.render.OrbitKit.Companion.EMBER
import dev.sush.phosphor.render.OrbitKit.Companion.PALE
import dev.sush.phosphor.render.OrbitKit.Companion.RED
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Ringfall: a dark planet whose limb burns from white to red, swirling trails around it, and far
 * below a glowing atmospheric horizon over a bank of clouds, with a figure on a ridge.
 *
 * The planet is placed so its burning limb crosses the top label row in the gap between two
 * icon columns, and its disc keeps the clock zone dark.
 */
internal class OrbitRingfall(private val o: OrbitKit) {
    private val k = o.k
    private val ridge = Path()
    private val crest = Path()
    private var ridgeKey = 0f

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        val reveal = smooth(f.reveal)
        val heat = o.heat(f)
        val px = 380f + f.shiftX(0.35f)
        val py = h * 0.25f + f.shiftY(0.35f)
        val horizon = h * HORIZON

        o.stars(f, 380, 0f, horizon, 0.15f)
        o.flushSpecks(canvas, f, reveal * 0.8f, 0.85f)
        o.meteor(canvas, f, 19f, 4f, h * 0.5f, h * 0.7f)
        swirl(canvas, f, px, py, horizon)
        streaks(canvas, f, horizon)
        o.flushSpecks(canvas, f, (0.3f + reveal * 0.7f) * (0.6f + f.amount * 0.4f))
        planet(canvas, f, px, py, reveal, heat)
        atmosphere(canvas, f, horizon, reveal, heat)
        clouds(canvas, f, horizon, reveal)
        ridge(canvas, f, horizon, heat)
        if (f.trail > 1) o.wake(canvas, f, px, py, 0.9f, 1f)
        o.gather(canvas, f, 0.85f)
        o.shockRings(canvas, f, 0.85f)
        o.flushSpecks(canvas, f, 1f)
    }

    /**
     * Trails circling the planet. Wider orbits drift down and right and flatten a little, so
     * the field reads as a vortex leaning toward the horizon. Orbits close to the burning limb
     * catch its fire.
     */
    private fun swirl(canvas: Canvas, f: Frame, px: Float, py: Float, horizon: Float) {
        val night = f.night
        val reveal = smooth(f.reveal)
        val gain = (0.55f + f.amount * 0.45f) * (0.25f + reveal * 0.75f)
        val count = (1350 * f.detail).toInt()
        val smear = 1f + (f.flowSpeed / 2600f).coerceAtMost(1.3f)
        val p = k.stroke
        p.shader = null
        k.additive(p, night)
        for (i in 0 until count) {
            val s = i * 131 + 53 * 7919
            val u = fraction(s + 1).pow(1.6f)
            var r = R + 14f + u * 1250f
            val cx = px + (r - R) * 0.16f + f.flowX * 0.015f * u
            val cy = py + (r - R) * 0.22f
            var ry = r * (0.94f - u * 0.18f)
            val omega = 30f * (520f / r).pow(1.3f) * (0.8f + fraction(s + 3) * 0.4f)
            val head = fraction(s + 2) * 360f + f.t * omega + o.spin * omega / 25f
            val sweep = (24f + fraction(s + 4).pow(0.7f) * 120f) * (0.3f + reveal * 0.7f) * smear

            var boost = 0f
            if (f.busy) {
                val hx = cx + cos(head * DEG) * r
                val hy = cy + sin(head * DEG) * ry
                val wave = f.wave(hx, hy, 100f)
                val held = f.dragInfluence(hx, hy, 220f)
                boost = (wave + held * 0.6f).coerceAtMost(1.6f)
                r += wave * 30f + held * 14f
                ry += wave * 30f + held * 14f
            }
            val r1 = fraction(s + 5)
            val r2 = fraction(s + 6)
            val near = 1f - smooth(u * 9f)
            // Orbits near the lit limb (lower right) catch its fire.
            val fire = near > 0.3f && fraction(s + 7) < near * 0.8f
            val step = (r1 * 4f).toInt().coerceAtMost(3) / 3f
            val color = if (night) when {
                fire -> if (r1 < 0.3f) mix(f.c2, Color.WHITE, 0.55f) else f.c2
                r2 < 0.18f -> mix(f.c1, f.c0, 0.4f)
                fraction(s + 8) < 0.06f -> mix(f.c1, f.c2, 0.45f)
                else -> mix(f.c1, Color.WHITE, step * step * 0.45f)
            } else when {
                fire -> f.c2
                else -> f.c1
            }
            var a = if (night) (40f + r2 * 90f + near * 90f) else (34f + r2 * 70f + near * 60f)
            // Far orbits sink toward the horizon and dim, so the clock area stays quiet.
            a = a * gain * (1f - u * 0.45f) + boost * 140f
            val width = 0.7f + fraction(s + 9).pow(2f) * 2.2f + near * 1.2f

            if (fraction(s + 10) < 0.22f) {
                o.dottedArc(f, cx, cy, r, ry, head, sweep, 6f + r2 * 6f, s, if (fire) EMBER else if (step > 0.6f) PALE else BLUE, false)
                continue
            }
            k.rect.set(cx - r, cy - ry, cx + r, cy + ry)
            p.shader = o.lane(f, color)
            p.alpha = a.toInt().coerceIn(0, 255)
            p.strokeWidth = width
            canvas.drawArc(k.rect, head - sweep, sweep, false, p)
            if (fire || boost > 0.3f) {
                p.alpha = (a * 0.8f).toInt().coerceIn(0, 255)
                p.strokeWidth = width * 1.8f
                canvas.drawArc(k.rect, head - sweep * 0.2f, sweep * 0.2f, false, p)
            }
        }
        k.reset(p)
    }

    /**
     * Below the planet the ring plane is seen nearly edge-on: long, almost straight streaks
     * stacked toward the horizon, in the gaps between the lower label rows.
     */
    private fun streaks(canvas: Canvas, f: Frame, horizon: Float) {
        val h = f.h
        val reveal = smooth(f.reveal)
        val night = f.night
        val count = (260 * f.detail).toInt()
        val p = k.stroke
        p.shader = null
        k.additive(p, night)
        for (i in 0 until count) {
            val s = i * 211 + 17
            val band = fraction(s + 1)
            // Far edges of the ring plane settle between the lower label rows, densest at the horizon.
            val top = if (band < 0.35f) h * (0.632f + fraction(s + 2) * 0.08f)
            else h * (0.752f + fraction(s + 2).pow(0.5f) * (HORIZON - 0.756f))
            val rx = 1300f + fraction(s + 3) * 1600f
            val ry = 40f + fraction(s + 4) * 160f
            val cx = 500f + (fraction(s + 5) - 0.5f) * 900f + f.flowX * 0.03f
            val cy = top + ry
            val omega = 6f * (1600f / rx) * (0.7f + fraction(s + 6) * 0.6f)
            val head = 270f + ((fraction(s + 7) * 120f - 60f + f.t * omega + o.spin * 0.4f) % 120f + 120f) % 120f - 60f
            val sweep = (8f + fraction(s + 8) * 26f) * (1f + (f.flowSpeed / 2600f).coerceAtMost(1.3f))
            val close = (top / h - 0.6f) / (HORIZON - 0.6f)
            val wave = if (f.pulses > 0) f.wave(cx + cos(head * DEG) * rx, top, 90f) else 0f
            val color = if (night) when {
                fraction(s + 9) < 0.14f -> f.c2
                fraction(s + 9) < 0.45f -> mix(f.c1, Color.WHITE, 0.45f)
                else -> f.c1
            } else if (fraction(s + 9) < 0.14f) f.c2 else f.c1
            k.rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
            p.shader = o.lane(f, color)
            p.alpha = (((if (night) 50f else 40f) + close * 120f) * reveal * (0.5f + fraction(s + 10) * 0.5f) + wave * 150f)
                .toInt().coerceIn(0, 255)
            p.strokeWidth = 0.8f + fraction(s + 11) * 1.8f
            canvas.drawArc(k.rect, head - sweep, sweep, false, p)
        }
        k.reset(p)
    }

    /** The occluding disc, a thin atmosphere around it, and the limb burning white to red. */
    private fun planet(canvas: Canvas, f: Frame, px: Float, py: Float, reveal: Float, heat: Float) {
        val night = f.night
        val p = k.fill
        p.shader = null
        k.normal(p)
        p.color = if (night) mix(f.c0, Color.BLACK, 0.3f) else f.c0
        canvas.drawCircle(px, py, R, p)
        if (!night) {
            // By day the disc is a pale tint of blue ink, darker toward the unlit side.
            k.glow(canvas, k.glowA, px - R * 0.35f, py - R * 0.3f, R * 1.25f, R * 1.25f, 70, RenderKit.Mode.INK)
        }
        // Light wrapping around the lit side, inside the disc.
        val saved = canvas.save()
        k.path.reset()
        k.path.addCircle(px, py, R, Path.Direction.CW)
        canvas.clipPath(k.path)
        k.glow(canvas, k.glowB, px + R * 0.95f, py + R * 0.45f, R * 0.7f, R * 0.7f,
            ((if (night) 40 else 30) * reveal * (1f + heat)).toInt(), if (night) RenderKit.Mode.SCREEN else RenderKit.Mode.INK)
        canvas.restoreToCount(saved)

        // A flare runs along the limb when a pulse reaches it; holding grows the burn.
        val flare = f.wave(px + R * 0.8f, py + R * 0.55f, 180f).coerceAtMost(1.4f) + heat * 0.6f
        val grow = 1f + flare * 0.25f
        val s = k.stroke
        k.additive(s, night)
        s.shader = null
        // Atmosphere: a thin blue rim all around, brightest where it meets the lit limb.
        k.rect.set(px - R - 5f, py - R - 5f, px + R + 5f, py + R + 5f)
        s.color = f.c1; s.alpha = ((if (night) 70 else 60) * reveal).toInt(); s.strokeWidth = 3f
        canvas.drawOval(k.rect, s)
        k.rect.set(px - R - 16f, py - R - 16f, px + R + 16f, py + R + 16f)
        s.alpha = ((if (night) 26 else 20) * reveal).toInt(); s.strokeWidth = 18f
        canvas.drawOval(k.rect, s)

        // The burning limb: wide red haze, red, ember-white and a white-hot core, each with its
        // own extent so the crescent tapers and runs from white (top) to red (bottom).
        k.rect.set(px - R, py - R, px + R, py + R)
        val ignite = 0.2f + reveal * 0.8f
        if (night) {
            limb(canvas, f.c2, 46f, 34, -30f, 150f, ignite, grow, flare)
            limb(canvas, f.c2, 18f, 110, -40f, 140f, ignite, grow, flare)
            limb(canvas, mix(f.c2, Color.WHITE, 0.45f), 8f, 190, -55f, 105f, ignite, grow, flare)
            limb(canvas, Color.WHITE, 3.2f, 255, -68f, 70f, ignite, grow, flare)
            k.rect.set(px - R - 24f, py - R - 24f, px + R + 24f, py + R + 24f)
            limb(canvas, f.c1, 2f, 130, -60f, 130f, ignite, grow, flare)
        } else {
            limb(canvas, f.c2, 30f, 40, -30f, 150f, ignite, grow, flare)
            limb(canvas, f.c2, 9f, 190, -45f, 140f, ignite, grow, flare)
            k.rect.offset(3f, 2f)
            limb(canvas, f.c1, 2.4f, 200, -66f, 120f, ignite, grow, flare)
            k.rect.offset(-3f, -2f)
            k.normal(s)
            // The white-hot core is bare paper, knocked out of the red ink.
            limb(canvas, f.c0, 2.4f, 240, -60f, 80f, ignite, grow, flare)
        }
        k.reset(s)

        // Holding builds a sun about to rise behind the limb; release lets it flare.
        if (flare > 0.02f) {
            val sx = px + cos(18f * DEG) * R
            val sy = py + sin(18f * DEG) * R
            val mode = if (night) RenderKit.Mode.SCREEN else RenderKit.Mode.INK
            val v = flare.coerceAtMost(1.6f)
            k.glow(canvas, k.glowWhite, sx, sy, 60f + v * 120f, 60f + v * 120f, (v * 160f).toInt(), mode)
            k.glow(canvas, k.glowB, sx, sy, 140f + v * 200f, 140f + v * 200f, (v * 70f).toInt(), mode)
            k.glow(canvas, k.glowWhite, sx, sy, 300f + v * 500f, 4f + v * 3f, (v * 200f).toInt(), mode)
            k.glow(canvas, k.glowWhite, sx, sy, 3f + v * 2f, 80f + v * 160f, (v * 150f).toInt(), mode)
        }
    }

    private fun limb(canvas: Canvas, color: Int, width: Float, opacity: Int, from: Float, to: Float, ignite: Float, grow: Float, flare: Float) {
        val s = k.stroke
        val mid = (from + to) * 0.5f
        val half = (to - from) * 0.5f * ignite * grow
        s.color = color
        s.alpha = (opacity * (0.55f + ignite * 0.45f) + flare * 50f).toInt().coerceIn(0, 255)
        s.strokeWidth = width * (1f + flare * 0.5f)
        canvas.drawArc(k.rect, mid - half, half * 2f, false, s)
    }

    /** The glowing atmospheric horizon: a white line over blue haze, a red glint along it. */
    private fun atmosphere(canvas: Canvas, f: Frame, y: Float, reveal: Float, heat: Float) {
        val hot = 1f + heat * 0.6f
        val p = k.stroke
        p.shader = null
        if (f.night) {
            k.glow(canvas, k.glowA, 500f, y, 1100f, 60f * hot, (120 * reveal).toInt(), RenderKit.Mode.SCREEN)
            k.glow(canvas, k.glowWhite, 620f, y, 900f, 10f * hot, (200 * reveal).toInt(), RenderKit.Mode.SCREEN)
            k.glow(canvas, k.glowB, 700f, y + 18f, 700f, 14f, (110 * reveal * hot).toInt(), RenderKit.Mode.SCREEN)
            k.additive(p, true)
            p.color = Color.WHITE; p.alpha = (220 * reveal).toInt(); p.strokeWidth = 2f
            canvas.drawLine(-20f, y, 1020f, y, p)
            p.color = mix(f.c1, Color.WHITE, 0.5f); p.alpha = (110 * reveal).toInt(); p.strokeWidth = 4f
            canvas.drawLine(-20f, y - 5f, 1020f, y - 5f, p)
        } else {
            k.glow(canvas, k.glowA, 500f, y, 1100f, 40f * hot, (60 * reveal).toInt(), RenderKit.Mode.INK)
            k.glow(canvas, k.glowB, 700f, y + 14f, 700f, 10f, (90 * reveal * hot).toInt(), RenderKit.Mode.INK)
            k.blend(p, RenderKit.Mode.INK)
            p.color = f.c1; p.alpha = (170 * reveal).toInt(); p.strokeWidth = 1.6f
            canvas.drawLine(-20f, y - 3f, 1020f, y - 3f, p)
            p.color = f.c2; p.alpha = (150 * reveal).toInt(); p.strokeWidth = 1.4f
            canvas.drawLine(-20f, y + 3f, 1020f, y + 3f, p)
        }
        k.reset(p)
    }

    /**
     * A bank of clouds below the horizon: rows of soft puffs, larger as they come closer, each a
     * dark body with a top lit by the horizon. Far rows drift faster than near ones.
     */
    private fun clouds(canvas: Canvas, f: Frame, horizon: Float, reveal: Float) {
        val night = f.night
        val p = k.fill
        val rows = 7
        val mode = if (night) RenderKit.Mode.SCREEN else RenderKit.Mode.INK
        for (row in 0 until rows) {
            val z = row / (rows - 1f)
            val y = horizon + 14f + z.pow(1.5f) * (f.h - horizon) * 0.95f
            val radius = 16f + z * 90f
            val n = (1300f / (radius * 1.25f)).toInt() + 2
            val drift = f.t * (5f - z * 3f) + f.shiftX(0.4f + z)
            val light = 1f - z * 0.75f
            for (i in 0 until n) {
                val seed = row * 97 + i
                val span = n * radius * 1.25f
                var x = (i * radius * 1.25f + drift + fraction(seed) * radius) % span
                if (x < 0f) x += span
                x -= 150f
                val r = radius * (0.7f + fraction(seed + 3) * 0.6f)
                val cy = y + (fraction(seed + 5) - 0.5f) * radius * 0.5f
                p.shader = null
                k.normal(p)
                p.color = if (night) mix(f.c0, f.c1, 0.07f + light * 0.06f) else f.c0
                canvas.drawCircle(x, cy, r, p)
                if (night) {
                    k.glow(canvas, k.glowWhite, x, cy - r * 0.45f, r * 0.95f, r * 0.55f, (light * light * 46f * reveal).toInt(), mode)
                    k.glow(canvas, k.glowA, x, cy - r * 0.2f, r * 1.1f, r * 0.8f, (light * 30f * reveal).toInt(), mode)
                } else {
                    // Riso clouds: paper bodies with blue ink pooled in their undersides.
                    k.glow(canvas, k.glowA, x, cy + r * 0.45f, r * 0.95f, r * 0.55f, (34f * reveal).toInt(), mode)
                    if (z < 0.2f && fraction(seed + 7) < 0.3f) {
                        k.glow(canvas, k.glowB, x, cy - r * 0.5f, r * 0.7f, r * 0.25f, (40f * reveal).toInt(), mode)
                    }
                }
            }
        }
        k.reset(p)
    }

    /** A ridge rising out of the clouds at lower left, rim-lit, with the figure on its crest. */
    private fun ridge(canvas: Canvas, f: Frame, horizon: Float, heat: Float) {
        val h = f.h
        val dx = f.shiftX(1.3f)
        if (ridgeKey != h) {
            ridgeKey = h
            crest.reset()
            crest.moveTo(-40f, h * 0.875f)
            crest.cubicTo(60f, h * 0.86f, 150f, h * 0.838f, 238f, h * 0.834f)
            crest.cubicTo(300f, h * 0.832f, 360f, h * 0.848f, 470f, h * 0.872f)
            crest.cubicTo(560f, h * 0.89f, 640f, h * 0.9f, 760f, h * 0.93f)
            crest.cubicTo(820f, h * 0.945f, 900f, h * 0.97f, 1040f, h * 1.0f)
            ridge.set(crest)
            ridge.lineTo(1040f, h + 40f)
            ridge.lineTo(-40f, h + 40f)
            ridge.close()
        }
        val saved = canvas.save()
        canvas.translate(dx, 0f)
        val p = k.fill
        p.shader = null
        k.normal(p)
        p.color = if (f.night) mix(f.c0, Color.BLACK, 0.5f) else alpha(mix(f.c0, f.c1, 0.22f), 255)
        canvas.drawPath(ridge, p)
        val s = k.stroke
        k.additive(s, f.night)
        s.color = if (f.night) mix(f.c1, Color.WHITE, 0.4f) else f.c1
        s.alpha = if (f.night) 120 else 150
        s.strokeWidth = 1.6f
        canvas.drawPath(crest, s)
        k.reset(s)
        canvas.restoreToCount(saved)
        o.figure(canvas, f, 238f + dx, h * 0.834f, 24f, -1.1f, 0.1f, heat)
    }

    private companion object {
        const val R = 440f
        const val HORIZON = 0.8f
    }
}
