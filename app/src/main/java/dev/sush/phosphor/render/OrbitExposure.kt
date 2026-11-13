package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import dev.sush.phosphor.render.OrbitKit.Companion.BLUE
import dev.sush.phosphor.render.OrbitKit.Companion.DEG
import dev.sush.phosphor.render.OrbitKit.Companion.EMBER
import dev.sush.phosphor.render.OrbitKit.Companion.PALE
import dev.sush.phosphor.render.OrbitKit.Companion.RED
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Long exposure: a vortex of light pinched onto a razor-thin, white-hot horizon. Above it a
 * funnel of orbits opens toward the sky; below, its brighter, nearer reflection sweeps toward
 * the viewer. A figure stands just below the horizon, shadow cast away from the light.
 *
 * Every orbit is an ellipse resting on the horizon. Its far side is placed in a gap between
 * launcher label rows, so the long, flat stretches of light stack into bands between rows.
 */
internal class OrbitExposure(private val o: OrbitKit) {
    private val k = o.k

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        val horizon = h * HORIZON
        val reveal = smooth(f.reveal)
        val heat = o.heat(f)
        o.stars(f, 480, 0f, h, 0.2f)
        o.flushSpecks(canvas, f, reveal * 0.75f, 0.85f)
        haze(canvas, f, horizon, reveal, heat)

        val eyeUp = 930f + f.shiftX(0.5f)
        val eyeDown = 985f + f.shiftX(1f)
        funnel(canvas, f, horizon, true, (980 * f.detail).toInt(), 11, eyeUp)
        funnel(canvas, f, horizon, false, (1080 * f.detail).toInt(), 29, eyeDown)
        o.flushSpecks(canvas, f, (0.3f + reveal * 0.7f) * (0.6f + f.amount * 0.4f))

        strata(canvas, f, horizon, reveal, heat)
        horizonLine(canvas, f, horizon, eyeUp, reveal, heat)
        if (f.trail > 1) {
            val above = f.trailY[f.trail / 2] < horizon
            o.wake(canvas, f, eyeUp, horizon, 0.3f, if (above) -1f else 1f)
        }
        o.gather(canvas, f, 0.32f)
        o.shockRings(canvas, f, 0.3f)
        o.flushSpecks(canvas, f, 1f)
        // The light pours from the eye on the right, so the shadow stretches left.
        o.figure(canvas, f, 318f + f.shiftX(1f), horizon + h * 0.03f, 22f, -1.7f, 0.04f, heat)
    }

    /**
     * One funnel of [count] orbits resting on the horizon, opening up ([up]) or down. The eye of
     * the vortex sits near the right edge at [eyeX], so each orbit turns around inside the frame.
     */
    private fun funnel(canvas: Canvas, f: Frame, horizon: Float, up: Boolean, count: Int, seed: Int, eyeX: Float) {
        val h = f.h
        val dir = if (up) -1f else 1f
        // Upper orbits run so their top edge travels left; the reflection counter-rotates.
        val turn = if (up) -1f else 1f
        val night = f.night
        val reveal = smooth(f.reveal)
        val gain = (0.55f + f.amount * 0.45f) * (0.25f + reveal * 0.75f)
        val smear = 1f + (f.flowSpeed / 2600f).coerceAtMost(1.3f)
        val spun = o.spin * -turn
        val share = if (up) UP_SHARE else DOWN_SHARE
        val lows = if (up) UP_LO else DOWN_LO
        val highs = if (up) UP_HI else DOWN_HI
        val p = k.stroke
        p.shader = null
        k.additive(p, night)
        for (i in 0 until count) {
            val s = seed * 7919 + i * 131
            val pick = fraction(s + 1)
            var tier = 0
            while (tier < share.size - 1 && pick > share[tier]) tier++
            var v = fraction(s + 2)
            // Ringlets: orbits bunch into fine bands, like the divisions of a planet's rings.
            v = (v + 0.05f * sin(v * 37f + seed)).coerceIn(0f, 1f)
            val far = h * (lows[tier] + v * (highs[tier] - lows[tier]))
            val gap = if (tier == 0) 1.5f + fraction(s + 3) * 5f else 2f + fraction(s + 3).pow(2f) * 16f
            val near = horizon + dir * gap
            var ry = abs(far - near) * 0.5f
            val cy = near + dir * ry
            var rx = (if (up) 230f + ry * 2.3f else 270f + ry * 3.1f) * (0.86f + fraction(s + 4) * 0.28f)
            // Swipes shear the field: the wide outer orbits slide further than the tight inner ones.
            val cx = eyeX + (fraction(s + 5) - 0.5f) * 110f + f.flowX * 0.02f * (0.3f + v + tier * 0.2f)
            // The funnel leans: each orbit is skewed sideways about the point where it touches the
            // horizon, wider orbits more, so heights (and the gaps between label rows) are kept.
            val lean = (if (up) LEAN_UP else LEAN_DOWN) * (0.5f + (ry / 400f).coerceAtMost(1.2f))

            val size = ry + rx * 0.2f
            val omega = 40f * (220f / (size + 40f)).pow(0.9f) * (0.75f + fraction(s + 6) * 0.5f)
            val head = fraction(s + 7) * 360f + turn * f.t * omega + spun * omega / 30f
            val sweep = (36f + fraction(s + 8).pow(0.7f) * 170f) * (0.3f + reveal * 0.7f) * smear

            var boost = 0f
            if (f.busy) {
                val hx = cx + cos(head * DEG) * rx
                val hy = cy + sin(head * DEG) * ry
                val wave = f.wave(hx, hy, 110f)
                val held = f.dragInfluence(hx, hy, 230f)
                boost = (wave + held * 0.6f).coerceAtMost(1.6f)
                ry += wave * 34f + held * 16f
                rx += wave * 50f
            }

            val e = fraction(s + 11)
            val ember = (if (up) (tier == 1 && v in 0.6f..0.78f) || (tier == 0 && v > 0.86f)
            else (tier == 1 && v in 0.16f..0.36f) || (tier == 2 && v in 0.55f..0.67f)) || e < 0.025f
            val inner = tier == 0
            val deep = !inner && !ember && fraction(s + 9) < 0.22f
            val violet = !inner && !ember && !deep && fraction(s + 10) < 0.1f
            val presence = when (tier) {
                0 -> 1f
                1 -> 0.8f
                2 -> 0.62f
                3 -> 0.55f
                else -> 0.3f
            } * (if (up) 1f else 1.08f)
            val r1 = fraction(s + 12)
            val r2 = fraction(s + 13)
            val color: Int
            var a: Float
            if (night) {
                // Colors come in a few fixed steps so each has one cached lane shader.
                val step = (r1 * 4f).toInt().coerceAtMost(3) / 3f
                color = when {
                    ember -> if (r1 < 0.1f) mix(f.c2, Color.WHITE, 0.6f) else mix(f.c2, Color.WHITE, 0.06f)
                    inner -> mix(f.c1, Color.WHITE, 0.15f + step * 0.55f)
                    deep -> mix(f.c1, f.c0, 0.4f)
                    violet -> mix(f.c1, f.c2, 0.45f)
                    else -> mix(f.c1, Color.WHITE, step * step * 0.45f)
                }
                a = when {
                    ember -> 130f + r2 * 90f
                    inner -> 50f + r2 * 80f
                    deep -> 50f + r2 * 45f
                    else -> 45f + r2 * 95f
                }
            } else {
                color = when {
                    ember -> f.c2
                    violet -> mix(f.c2, f.c1, 0.4f)
                    else -> f.c1
                }
                a = when {
                    ember -> 120f + r2 * 80f
                    inner -> 70f + r2 * 80f
                    deep -> 26f + r2 * 26f
                    else -> 38f + r2 * 80f
                }
            }
            a = a * presence * gain + boost * 140f
            val width = when {
                deep -> 2f + r1 * 3f
                ember -> 1.2f + r2 * 2f
                else -> 0.7f + fraction(s + 14).pow(2f) * 2f
            } * (if (up) 1f else 1.25f)

            if (!deep && fraction(s + 15) < 0.24f) {
                // Strobed exposures: the same orbit as a line of dots with stray colored specks.
                val cls = when { ember -> if (r1 < 0.5f) EMBER else RED; inner -> PALE; else -> BLUE }
                o.dottedArc(f, cx, cy, rx, ry, head, sweep * turn, 5.5f + r2 * 5f, s, cls, !up,
                    if (inner) 0.06f else 0.14f, lean, near)
                continue
            }
            val start = if (turn < 0f) head else head - sweep
            val saved = canvas.save()
            canvas.translate(0f, near)
            canvas.skew(lean, 0f)
            canvas.translate(0f, -near)
            if (ember) {
                // Bloom: a wide, faint stroke under each ember trail so the band glows as one.
                k.rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
                p.shader = o.lane(f, f.c2)
                p.alpha = (a * if (night) 0.16f else 0.1f).toInt().coerceIn(0, 255)
                p.strokeWidth = width * 6f
                canvas.drawArc(k.rect, start, sweep, false, p)
            }
            k.rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
            p.shader = o.lane(f, color)
            p.alpha = a.toInt().coerceIn(0, 255)
            p.strokeWidth = width
            canvas.drawArc(k.rect, start, sweep, false, p)
            if (ember || (inner && r1 > 0.6f) || boost > 0.3f) {
                // A brighter leading edge reads as the head of the exposure.
                val hs = sweep * 0.22f
                p.alpha = (a * 0.85f).toInt().coerceIn(0, 255)
                p.strokeWidth = width * 1.7f
                canvas.drawArc(k.rect, if (turn < 0f) head else head - hs, hs, false, p)
            }
            if ((ember || inner) && fraction(s + 16) < 0.3f) {
                // Chromatic siblings: hot trails split into red and blue edges, a lens fringe at
                // night and a two-ink misregistration by day.
                if (night) {
                    k.rect.set(cx - rx - 3f, cy - ry - 2.2f, cx + rx + 3f, cy + ry + 2.2f)
                    p.shader = o.lane(f, f.c2)
                    p.alpha = (a * 0.45f).toInt().coerceIn(0, 255)
                    p.strokeWidth = width * 0.8f
                    canvas.drawArc(k.rect, start, sweep, false, p)
                    k.rect.set(cx - rx + 3f, cy - ry + 2.2f, cx + rx - 3f, cy + ry - 2.2f)
                    p.shader = o.lane(f, f.c1)
                    canvas.drawArc(k.rect, start, sweep, false, p)
                } else {
                    k.rect.offset(2.6f, 1.8f)
                    p.shader = o.lane(f, if (ember) f.c1 else f.c2)
                    p.alpha = (a * 0.35f).toInt().coerceIn(0, 255)
                    p.strokeWidth = width * 0.9f
                    canvas.drawArc(k.rect, start, sweep, false, p)
                }
            }
            canvas.restoreToCount(saved)
        }
        k.reset(p)
    }

    /** Atmosphere around the horizon: ember haze above, blue haze below, a deep glow behind the eye. */
    private fun haze(canvas: Canvas, f: Frame, horizon: Float, reveal: Float, heat: Float) {
        val mode = if (f.night) RenderKit.Mode.SCREEN else RenderKit.Mode.INK
        val r = reveal * (1f + heat * 0.6f)
        if (f.night) {
            k.glow(canvas, k.glowA, 900f, horizon, 900f, 420f, (26 * r).toInt(), mode)
            k.glow(canvas, k.glowB, 760f, horizon - 22f, 1150f, 64f * (1f + heat * 0.4f), (110 * r).toInt(), mode)
            k.glow(canvas, k.glowA, 600f, horizon + 34f, 1150f, 80f, (70 * r).toInt(), mode)
        } else {
            k.glow(canvas, k.glowB, 760f, horizon - 16f, 1100f, 40f * (1f + heat * 0.4f), (58 * r).toInt(), mode)
            k.glow(canvas, k.glowA, 600f, horizon + 26f, 1100f, 62f, (40 * r).toInt(), mode)
        }
    }

    /**
     * Fine horizontal lines piling onto the horizon from both sides, densest at the line itself:
     * the far edge of every orbit seen almost exactly side-on.
     */
    private fun strata(canvas: Canvas, f: Frame, horizon: Float, reveal: Float, heat: Float) {
        val p = k.stroke
        p.shader = null
        k.additive(p, f.night)
        val n = (90 * (0.5f + f.detail * 0.5f)).toInt()
        for (i in 0 until n) {
            val up = i % 2 == 0
            val u = fraction(i * 37 + 5)
            val d = 5f + u.pow(1.8f) * 92f
            val y = horizon + if (up) -d else d
            val close = (1f - d / 100f).pow(1.5f)
            // Each stratum breathes on its own slow phase, like heat shimmer over the line.
            val shimmer = 0.65f + 0.35f * sin(f.t * (0.3f + fraction(i * 3) * 0.9f) + i * 2.3f)
            p.color = if (f.night) {
                if (up) mix(f.c2, f.c1, u * 1.4f) else mix(f.c1, Color.WHITE, (1f - u) * 0.35f)
            } else if (up && u < 0.35f) f.c2 else f.c1
            p.alpha = ((if (f.night) 120f else 90f) * close * reveal * shimmer * (1f + heat * 0.5f))
                .toInt().coerceIn(0, 255)
            p.strokeWidth = 0.8f + fraction(i * 19) * 1.6f
            canvas.drawLine(-20f, y, 1020f, y, p)
        }
        k.reset(p)
    }

    /** The razor horizon: ember above, a white-hot core, pale blue below, and a lens streak. */
    private fun horizonLine(canvas: Canvas, f: Frame, y: Float, eyeX: Float, reveal: Float, heat: Float) {
        // It ignites from the eye outward as the wallpaper wakes.
        val left = eyeX - (eyeX + 40f) * reveal
        val right = 1040f
        val hot = 1f + heat
        val p = k.stroke
        if (f.night) {
            k.glow(canvas, k.glowWhite, eyeX - 80f, y, 460f * (1f + heat * 0.4f), 15f * hot, (200 * reveal).toInt(), RenderKit.Mode.SCREEN)
            k.glow(canvas, k.glowWhite, 520f, y, 1400f, 3.5f * hot, (150 * reveal * hot).toInt(), RenderKit.Mode.SCREEN)
            if (heat > 0.01f) {
                k.glow(canvas, k.glowB, eyeX - 200f, y - 8f, 900f, 46f * heat, (heat * 140f).toInt(), RenderKit.Mode.SCREEN)
            }
            k.additive(p, true)
            p.color = mix(f.c2, Color.WHITE, 0.2f); p.alpha = (130 * reveal * hot).toInt().coerceIn(0, 255); p.strokeWidth = 5f
            canvas.drawLine(left, y - 4.5f, right, y - 4.5f, p)
            p.color = Color.WHITE; p.alpha = (235 * reveal).toInt(); p.strokeWidth = 2.2f * hot
            canvas.drawLine(left, y, right, y, p)
            p.color = mix(f.c1, Color.WHITE, 0.5f); p.alpha = (140 * reveal).toInt(); p.strokeWidth = 3f
            canvas.drawLine(left, y + 3.8f, right, y + 3.8f, p)
            k.reset(p)
        } else {
            k.glow(canvas, k.glowB, eyeX - 120f, y - 4f, 700f, 14f * hot, (110 * reveal).toInt(), RenderKit.Mode.INK)
            k.blend(p, RenderKit.Mode.INK)
            p.color = f.c2; p.alpha = (200 * reveal).toInt(); p.strokeWidth = 3f * hot
            canvas.drawLine(left, y - 3f, right, y - 3f, p)
            p.color = f.c1; p.alpha = (190 * reveal).toInt(); p.strokeWidth = 1.4f
            canvas.drawLine(left, y + 3.2f, right, y + 3.2f, p)
            // The white-hot core by day is bare paper knocked out of the converging ink.
            k.normal(p)
            p.color = f.c0; p.alpha = (235 * reveal).toInt(); p.strokeWidth = 1.5f
            canvas.drawLine(left, y, right, y, p)
            k.reset(p)
        }
    }

    private companion object {
        const val HORIZON = 0.552f
        const val LEAN_UP = 0.32f
        const val LEAN_DOWN = -0.22f
        // Tiers for the far side of each orbit, as fractions of the height, chosen to fall between
        // label rows: just off the horizon, between rows, above the top row, under the clock (or
        // past the dock), and a few faint strays anywhere.
        val UP_SHARE = floatArrayOf(0.2f, 0.55f, 0.78f, 0.92f, 1f)
        val UP_LO = floatArrayOf(0.518f, 0.392f, 0.278f, -0.12f, 0.06f)
        val UP_HI = floatArrayOf(0.541f, 0.474f, 0.352f, 0.05f, 0.5f)
        val DOWN_SHARE = floatArrayOf(0.2f, 0.52f, 0.76f, 0.92f, 1f)
        val DOWN_LO = floatArrayOf(0.563f, 0.632f, 0.752f, 0.95f, 0.6f)
        val DOWN_HI = floatArrayOf(0.588f, 0.714f, 0.852f, 1.3f, 0.94f)
    }
}
