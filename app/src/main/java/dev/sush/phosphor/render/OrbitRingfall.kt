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
            val cx = px + (r - R) * 0.3f + f.flowX * 0.015f * u
            val cy = py + (r - R) * 0.36f
            var ry = r * (0.95f - u * 0.32f)
            val omega = 30f * (520f / r).pow(1.3f) * (0.8f + fraction(s + 3) * 0.4f)
            val head = fraction(s + 2) * 360f + o.t * omega + o.spin * omega / 25f
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
            val fire = near > 0.5f && fraction(s + 7) < near * 0.35f
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
            val head = 270f + ((fraction(s + 7) * 120f - 60f + o.t * omega + o.spin * 0.4f) % 120f + 120f) % 120f - 60f
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
        k.glow(canvas, k.glowB, px + R * 1.0f, py + R * 0.2f, R * 0.6f, R * 0.75f,
            ((if (night) 40 else 30) * reveal * (1f + heat * 0.4f)).toInt(), if (night) RenderKit.Mode.SCREEN else RenderKit.Mode.INK)
        canvas.restoreToCount(saved)

        // A flare runs along the limb when a pulse reaches it; holding grows the burn.
        val flare = f.wave(px + R * 0.95f, py, 180f).coerceAtMost(1.4f) + heat * 0.6f
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
            limb(canvas, f.c2, 70f, 30, -30f, 120f, ignite, grow, flare)
            limb(canvas, f.c2, 30f, 90, -45f, 112f, ignite, grow, flare)
            limb(canvas, f.c2, 14f, 170, -55f, 100f, ignite, grow, flare)
            limb(canvas, mix(f.c2, Color.WHITE, 0.45f), 9f, 210, -85f, 65f, ignite, grow, flare)
            limb(canvas, Color.WHITE, 4.5f, 255, -105f, 35f, ignite, grow, flare)
            k.rect.set(px - R - 24f, py - R - 24f, px + R + 24f, py + R + 24f)
            limb(canvas, f.c1, 2f, 130, -110f, 90f, ignite, grow, flare)
        } else {
            limb(canvas, f.c2, 30f, 40, -40f, 120f, ignite, grow, flare)
            limb(canvas, f.c2, 9f, 190, -60f, 112f, ignite, grow, flare)
            k.rect.offset(3f, 2f)
            limb(canvas, f.c1, 2.4f, 200, -105f, 80f, ignite, grow, flare)
            k.rect.offset(-3f, -2f)
            k.normal(s)
            // The white-hot core is bare paper, knocked out of the red ink.
            limb(canvas, f.c0, 2.4f, 240, -95f, 40f, ignite, grow, flare)
        }
        k.reset(s)

        // Holding builds a sun about to rise behind the limb; release lets it flare.
        if (flare > 0.02f) {
            val sx = px + cos(-12f * DEG) * R
            val sy = py + sin(-12f * DEG) * R
            val mode = if (night) RenderKit.Mode.SCREEN else RenderKit.Mode.INK
            val v = flare.coerceAtMost(1.6f)
            k.glow(canvas, k.glowWhite, sx, sy, 60f + v * 120f, 60f + v * 120f, (v * 160f).toInt(), mode)
            k.glow(canvas, k.glowB, sx, sy, 140f + v * 200f, 140f + v * 200f, (v * 70f).toInt(), mode)
            // A soft anamorphic streak and a shorter vertical spike, like a lens catching the sun.
            k.glow(canvas, k.glowWhite, sx, sy, 160f + v * 220f, 7f + v * 5f, (v * 110f).toInt(), mode)
            k.glow(canvas, k.glowWhite, sx, sy, 5f + v * 3f, 50f + v * 90f, (v * 90f).toInt(), mode)
        }
    }

    private fun limb(canvas: Canvas, color: Int, width: Float, opacity: Int, from: Float, to: Float, ignite: Float, grow: Float, flare: Float) {
        val s = k.stroke
        val mid = (from + to) * 0.5f
        val half = (to - from) * 0.5f * ignite * grow
        s.color = color
        s.alpha = (opacity * (0.55f + ignite * 0.45f) + flare * 50f).toInt().coerceIn(0, 255)
        s.strokeWidth = width * (1f + flare.coerceAtMost(1f) * 0.25f)
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
     * A bank of clouds below the horizon. Each cloud is a cumulus mound of overlapping lobes,
     * backlit by the horizon: far rows glow silver, near rows are dark with bright linings.
     * Rows drift at their own speeds, far ones faster across the frame.
     */
    private fun clouds(canvas: Canvas, f: Frame, horizon: Float, reveal: Float) {
        val night = f.night
        val p = k.fill
        val s = k.stroke
        val rows = 6
        for (row in 0 until rows) {
            val z = row / (rows - 1f)
            val y = horizon + 22f + z.pow(1.4f) * (f.h - horizon) * 0.9f
            val size = 34f + z * 120f
            val pitch = size * 1.25f
            val n = (1300f / pitch).toInt() + 2
            val span = n * pitch
            val drift = o.t * (6f - z * 4f) + f.shiftX(0.3f + z)
            val light = (1f - z).pow(1.6f)
            for (i in 0 until n) {
                val seed = row * 97 + i
                var x = (i * pitch + drift + fraction(seed) * size * 0.6f) % span
                if (x < 0f) x += span
                x -= 150f
                val base = y + (fraction(seed + 5) - 0.5f) * size * 0.3f
                for (lobe in 0 until 4) {
                    val ls = seed * 5 + lobe
                    val r = size * (0.3f + fraction(ls + 1) * 0.45f) * (if (lobe == 1 || lobe == 2) 1.15f else 0.8f)
                    val lx = x + (lobe - 1.5f) * size * 0.45f + (fraction(ls + 2) - 0.5f) * size * 0.35f
                    val ly = base - r * 0.5f - (if (lobe == 1 || lobe == 2) size * 0.12f + fraction(ls + 3) * size * 0.25f else 0f)
                    p.shader = null
                    k.normal(p)
                    p.color = if (night) mix(f.c0, f.c1, 0.05f + light * 0.26f) else f.c0
                    canvas.drawCircle(lx, ly, r, p)
                    // The silver lining along the lobe's top, facing the light.
                    k.rect.set(lx - r, ly - r, lx + r, ly + r)
                    k.additive(s, night)
                    s.color = if (night) mix(f.c1, Color.WHITE, 0.6f) else f.c1
                    s.alpha = ((if (night) 10f + light * 110f else 18f + light * 70f) * reveal).toInt()
                    s.strokeWidth = 1f + light * 1.6f
                    canvas.drawArc(k.rect, 225f + fraction(ls + 4) * 20f, 70f + fraction(ls + 5) * 30f, false, s)
                    k.reset(s)
                    if (night && light > 0.2f) {
                        k.glow(canvas, k.glowWhite, lx, ly - r * 0.5f, r * 0.9f, r * 0.5f, (light * 40f * reveal).toInt(), RenderKit.Mode.SCREEN)
                    } else if (!night) {
                        // Riso clouds: paper bodies with blue ink pooled in their undersides.
                        k.glow(canvas, k.glowA, lx, ly + r * 0.5f, r * 0.9f, r * 0.45f, (30f * reveal).toInt(), RenderKit.Mode.INK)
                    }
                }
            }
            // Horizon light scattered through the row softens the lobes into one bank.
            if (night) k.glow(canvas, k.glowA, 520f, y - size * 0.3f, 1100f, size * 0.7f, (light * 50f * reveal).toInt(), RenderKit.Mode.SCREEN)
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
            crest.moveTo(-40f, h * 0.9f)
            crest.cubicTo(50f, h * 0.885f, 140f, h * 0.858f, 214f, h * 0.853f)
            crest.cubicTo(262f, h * 0.85f, 300f, h * 0.858f, 360f, h * 0.874f)
            crest.cubicTo(440f, h * 0.896f, 520f, h * 0.93f, 640f, h * 0.97f)
            crest.cubicTo(700f, h * 0.99f, 760f, h * 1.0f, 800f, h * 1.02f)
            ridge.set(crest)
            ridge.lineTo(800f, h + 40f)
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
        o.figure(canvas, f, 238f + dx, h * 0.8515f, 22f, -1.1f, 0.1f, heat)
    }

    private companion object {
        const val R = 440f
        const val HORIZON = 0.8f
    }
}
