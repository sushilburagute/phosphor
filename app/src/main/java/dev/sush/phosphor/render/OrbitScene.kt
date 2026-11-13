package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
 * Long-exposure light trails. Every trail is an arc of an ellipse travelling at a Keplerian
 * speed (inner orbits are faster), so the field shears and reforms instead of rotating rigidly.
 *
 * Night draws additive light, so crossing trails brighten like a real long exposure. Day draws
 * the same trails as two overprinted inks with a slight misregistration, like a riso print.
 */
internal class OrbitScene(private val k: RenderKit) {
    private val points = FloatArray(4096)

    fun draw(canvas: Canvas, f: Frame, variant: String) {
        when (variant) {
            "orbit-threshold" -> threshold(canvas, f)
            "orbit-ringfall" -> ringfall(canvas, f)
            else -> exposure(canvas, f)
        }
    }

    // region Long exposure

    private fun exposure(canvas: Canvas, f: Frame) {
        val h = f.h
        // Sits in the gap between typical icon rows, so no label row crosses the bright line.
        val horizon = h * 0.55f
        val reveal = smooth(f.reveal)
        stars(canvas, f, 150, 0f, horizon, 0.25f)

        // The upper system's center leans toward a held finger, as if pulled by gravity.
        val pull = f.drag * 0.22f
        val ux = 1180f + (f.dragX - 1180f) * pull + f.shiftX(0.6f)
        val uy = h * 0.4f + (f.dragY - h * 0.4f) * pull * 0.5f + f.shiftY(0.5f)
        val lx = 1230f + (f.dragX - 1230f) * pull * 0.6f + f.shiftX(1f)
        val ly = h * 0.69f + f.shiftY(0.8f)

        var saved = canvas.save()
        canvas.clipRect(-50f, -50f, 1050f, horizon)
        trails(canvas, f, ux, uy, 0.5f, -5f, (1500 * f.detail).toInt(), 11, 0.86f, 1500f)
        canvas.restoreToCount(saved)

        saved = canvas.save()
        canvas.clipRect(-50f, horizon, 1050f, h + 50f)
        trails(canvas, f, lx, ly, 0.34f, 3f, (1600 * f.detail).toInt(), 29, 0.8f, 1600f)
        canvas.restoreToCount(saved)

        horizonLine(canvas, f, horizon, reveal, 1f)
        val figureColor = if (f.night) 0xFF010204.toInt() else alpha(f.c1, 235)
        k.figure(canvas, 452f + f.shiftX(1f), horizon + h * 0.082f, 21f, figureColor, 0f,
            if (f.night) 0x99000000.toInt() else alpha(f.c1, 70))
    }

    /**
     * Draw [count] trails orbiting ([cx], [cy]). [flat] squashes orbits vertically; [seed]
     * keeps the two systems distinct. Arcs get a faint long tail and a brighter short head.
     */
    private fun trails(
        canvas: Canvas, f: Frame, cx: Float, cy: Float, flat: Float, tilt: Float,
        count: Int, seed: Int, brightness: Float, reach: Float,
    ) {
        val saved = canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(tilt)
        // Pulses and drag are evaluated in this rotated frame.
        val rad = (-tilt * PI / 180f).toFloat()
        val cr = cos(rad); val sr = sin(rad)
        val p = k.stroke
        p.shader = null
        k.additive(p, f.night)
        val reveal = smooth(f.reveal)
        val ink = !f.night
        val hotLow = 0.30f + fraction(seed * 7) * 0.05f
        val hotHigh = 0.56f + fraction(seed * 11) * 0.04f
        for (i in 0 until count) {
            val s = i * 977 + seed * 131
            val u = fraction(s + 1).pow(1.25f)
            var r = 70f + u * reach
            val w0 = fraction(s + 2) * 2f * PI.toFloat()
            // Kepler-ish: angular speed falls with radius^1.5, gently.
            val omega = 0.9f * (260f / r).pow(1.1f) * (0.8f + fraction(s + 3) * 0.4f)
            val head = w0 + f.t * omega
            var sweep = (30f + fraction(s + 4).pow(0.7f) * 150f) * (0.35f + reveal * 0.65f)

            // Local position of the trail's head, for touch response.
            val hx = cos(head) * r
            val hy = sin(head) * r * flat
            var boost = 0f
            if (f.busy) {
                val wx = cx + hx * cr + hy * sr  // approximate world position (rotation is small)
                val wy = cy - hx * sr + hy * cr
                val wave = f.wave(wx, wy, 90f)
                val held = f.dragInfluence(wx, wy, 260f)
                boost = wave + held * 0.8f
                r += wave * 38f + held * 22f
                sweep += wave * 26f
            }

            val hot = (u in hotLow..hotLow + 0.03f) || (u in hotHigh..hotHigh + 0.02f) || fraction(s + 5) < 0.03f
            val base = when {
                hot -> if (!ink && fraction(s + 10) < 0.3f) mix(f.c2, Color.WHITE, 0.0f) else if (ink) f.c2 else mix(f.c2, Color.WHITE, fraction(s + 10) * 0.35f)
                u < 0.1f -> if (ink) f.c1 else mix(f.c1, Color.WHITE, 0.5f - u * 3f)
                fraction(s + 6) < 0.16f -> mix(f.c1, f.c2, 0.4f)  // violet crossovers
                else -> if (ink) mix(f.c1, f.c0, fraction(s + 7) * 0.35f) else mix(f.c1, Color.WHITE, fraction(s + 7) * 0.45f)
            }
            val nearCore = (1f - u).pow(2.6f)
            var a = if (ink) {
                (46f + nearCore * 110f + (if (hot) 110f else 0f) + fraction(s + 8) * 50f) * brightness
            } else {
                (26f + nearCore * 105f + (if (hot) 110f else 0f) + fraction(s + 8) * 60f) * brightness
            }
            a = a * (0.55f + f.amount * 0.45f) * (0.25f + reveal * 0.75f) + boost * 140f
            val width = (if (hot) 1.9f else 0.8f) + fraction(s + 9).pow(2f) * 2.2f + nearCore * 1.4f

            k.rect.set(-r, -r * flat, r, r * flat)
            val headDeg = (head * 180f / PI).toFloat()
            p.color = base
            p.alpha = a.toInt().coerceIn(0, 255)
            p.strokeWidth = width
            canvas.drawArc(k.rect, headDeg - sweep, sweep, false, p)
            if (hot) {
                // A brighter head on hot trails reads as the leading edge of the exposure.
                p.alpha = (a * 0.8f).toInt().coerceIn(0, 255)
                p.strokeWidth = width * 1.6f
                canvas.drawArc(k.rect, headDeg - sweep * 0.2f, sweep * 0.2f, false, p)
                if (ink) {
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

    private fun horizonLine(canvas: Canvas, f: Frame, y: Float, reveal: Float, strength: Float) {
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
    private fun stars(canvas: Canvas, f: Frame, count: Int, top: Float, bottom: Float, depth: Float) {
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
            val boost = if (f.busy) f.wave(x, y, 70f) else 0f
            p.alpha = ((if (f.night) 200f else 120f) * twinkle * reveal + boost * 150f).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, size * (1f + boost * 0.8f), p)
        }
        p.alpha = 255
    }

    // endregion

    // region Threshold

    private fun threshold(canvas: Canvas, f: Frame) {
        val h = f.h
        val horizon = h * 0.79f
        val reveal = smooth(f.reveal)
        stars(canvas, f, 260, 0f, horizon - h * 0.25f, 0.2f)

        // A pale bowl of light rising from the horizon behind the figure; the floor stays dark.
        val sky = canvas.save()
        canvas.clipRect(-50f, -50f, 1050f, horizon)
        if (f.night) {
            k.glow(canvas, k.glowA, 500f, horizon, 820f, h * 0.26f, (95 * reveal).toInt(), RenderKit.Mode.SCREEN)
            k.glow(canvas, k.glowWhite, 500f, horizon, 380f, h * 0.06f, (150 * reveal).toInt(), RenderKit.Mode.SCREEN)
        } else {
            k.glow(canvas, k.glowA, 500f, horizon, 820f, h * 0.28f, (90 * reveal).toInt(), RenderKit.Mode.INK)
        }
        canvas.restoreToCount(sky)
        if (f.night) k.glow(canvas, k.glowA, 500f, horizon, 700f, h * 0.035f, (60 * reveal).toInt(), RenderKit.Mode.SCREEN)

        // Rings of dots sag toward the horizon, wider and slower as they rise.
        val rings = (44 * (0.6f + f.detail * 0.4f)).toInt()
        val p = k.dots
        k.additive(p, f.night)
        val cx = 500f + f.shiftX(0.7f)
        for (ring in 0 until rings) {
            val q = ring / (rings - 1f)
            val y0 = horizon - 14f - q.pow(1.3f) * h * 0.55f + f.shiftY(0.5f * q)
            val rx = 560f + q * 820f
            val sag = 30f + q * 210f
            val speed = 0.06f + (1f - q) * 0.22f
            val dotsOnRing = (PI.toFloat() * rx / (if (f.detail > 0.6f) 10f else 18f)).toInt()
            var bright = 0
            var dim = 0
            var hot = 0
            val gap = PI.toFloat() / dotsOnRing
            val phase = f.t * speed * (if (ring % 2 == 0) 1f else -1f)
            for (d in 0 until dotsOnRing) {
                val ang = d * gap + (phase % gap)
                val x = cx + cos(ang) * rx
                if (x < -20f || x > 1020f) continue
                val y = y0 + sin(ang) * sag - (1f - sin(ang)) * 0f
                val seed = ring * 389 + d
                // Dots blink in and out like distant traffic.
                if (fraction(seed + (f.t * 0.8f + fraction(seed) * 9f).toInt() * 7919) < 0.12f) continue
                val wave = if (f.busy) f.wave(x, y, 70f) + f.dragInfluence(x, y, 200f) else 0f
                val chance = fraction(seed * 3 + 1)
                when {
                    wave > 0.25f || chance < 0.05f -> if (bright < 400) { points[bright * 2] = x; points[bright * 2 + 1] = y; bright++ }
                    chance < 0.11f -> if (hot < 300) { points[1600 + hot * 2] = x; points[1600 + hot * 2 + 1] = y; hot++ }
                    else -> if (dim < 600) { points[2800 + dim * 2] = x; points[2800 + dim * 2 + 1] = y; dim++ }
                }
            }
            val nearFade = smooth(q * 4f)  // closest rings melt into the glow
            val ringAlpha = ((if (f.night) 120f + (1f - q) * 135f else 110f + (1f - q) * 120f) * reveal * (0.55f + f.amount * 0.45f) * (0.4f + nearFade * 0.6f)).toInt()
            // Thumbnails draw fewer, larger dots so the rings still read at gallery size.
            p.strokeWidth = (2.1f + (1f - q) * 2.2f) * (1.9f - f.detail * 0.9f)
            p.color = mix(f.c1, if (f.night) Color.WHITE else f.c1, 0.3f + (1f - q) * 0.4f)
            p.alpha = ringAlpha
            if (dim > 0) canvas.drawPoints(points, 2800, dim * 2, p)
            p.color = f.c2
            p.alpha = (ringAlpha * 1.2f).toInt().coerceAtMost(255)
            if (hot > 0) canvas.drawPoints(points, 1600, hot * 2, p)
            p.color = if (f.night) Color.WHITE else f.c1
            p.alpha = (ringAlpha * 1.5f).toInt().coerceIn(0, 255)
            p.strokeWidth *= 1.25f
            if (bright > 0) canvas.drawPoints(points, 0, bright * 2, p)
            // A faint continuous trace under the dots gives each ring its line.
            if (ring % 3 == 0) {
                k.stroke.color = f.c1
                k.additive(k.stroke, f.night)
                k.stroke.alpha = (ringAlpha * 0.3f).toInt()
                k.stroke.strokeWidth = 1.2f
                k.rect.set(cx - rx, y0 - sag, cx + rx, y0 + sag)
                canvas.drawArc(k.rect, 0f, 180f, false, k.stroke)
                k.reset(k.stroke)
            }
        }
        k.reset(p)

        horizonLine(canvas, f, horizon, reveal, 0.85f)
        floor(canvas, f, horizon)
        // Each tap sends a shockwave across the ground plane, foreshortened by perspective.
        if (f.pulses > 0) {
            val s = k.stroke
            k.additive(s, f.night)
            for (i in 0 until f.pulses) {
                val r = f.pulseR[i] * 0.9f
                k.rect.set(f.pulseX[i] - r, horizon + 30f - r * 0.12f, f.pulseX[i] + r, horizon + 30f + r * 0.12f)
                s.color = f.c1; s.strokeWidth = 5f; s.alpha = (f.pulseA[i] * 200f).toInt().coerceIn(0, 255)
                canvas.drawOval(k.rect, s)
                s.color = if (f.night) Color.WHITE else f.c2; s.strokeWidth = 1.6f
                canvas.drawOval(k.rect, s)
            }
            k.reset(s)
        }
        val stride = sin(f.t * 3.1f) * if (f.amount > 0f && f.t != 0f) 1f else 0f
        k.figure(canvas, 500f + f.shiftX(1f), horizon + 1f, 28f,
            if (f.night) 0xFF000000.toInt() else alpha(f.c1, 240), stride)
    }

    /** A perspective field of dots below the horizon, drifting slowly toward the viewer. */
    private fun floor(canvas: Canvas, f: Frame, horizon: Float) {
        val p = k.dots
        k.additive(p, f.night)
        val travel = (f.t * 0.12f) % 1f
        val rows = 14
        for (row in 0 until rows) {
            val z = (row + 1f - travel) / rows
            val y = horizon + z.pow(1.9f) * (f.h - horizon) * 1.05f
            if (y > f.h + 10f) continue
            val spread = 40f + z * 520f
            var n = 0
            for (col in -14..14) {
                val x = 500f + col * spread * 0.16f + f.shiftX(1.2f) * z
                if (x < -10f || x > 1010f) continue
                points[n * 2] = x; points[n * 2 + 1] = y; n++
            }
            p.strokeWidth = 1.4f + z * 5.5f
            p.color = if (row % 4 == 1) f.c2 else f.c1
            // Keep the dock area calm: the floor dims as it approaches the viewer.
            p.alpha = ((if (f.night) 120f else 80f) * (1f - z * 0.75f) * smooth(f.reveal)).toInt()
            canvas.drawPoints(points, 0, n * 2, p)
        }
        k.reset(p)
    }

    // endregion

    // region Ringfall

    private fun ringfall(canvas: Canvas, f: Frame) {
        val h = f.h
        val reveal = smooth(f.reveal)
        stars(canvas, f, 200, 0f, h, 0.2f)
        val cx = 420f + f.shiftX(0.6f)
        val cy = h * 0.47f + f.shiftY(0.6f)
        val radius = 300f
        // Drag tilts the ring plane.
        val tilt = -16f + (f.dragX - 500f) / 500f * 14f * f.drag
        val flat = 0.82f - (f.dragY / h - 0.5f) * 0.3f * f.drag
        trails(canvas, f, cx, cy, flat, tilt, (1500 * f.detail).toInt(), 53, 0.9f, 1500f)

        // The planet itself: an occluding disc of ground with a faintly lit face.
        val p = k.fill
        p.shader = null
        k.normal(p)
        p.color = f.c0
        canvas.drawCircle(cx, cy, radius, p)
        k.glow(canvas, k.glowA, cx + radius * 0.5f, cy - radius * 0.1f, radius * 0.9f, radius * 0.9f,
            if (f.night) 16 else 26, if (f.night) RenderKit.Mode.SCREEN else RenderKit.Mode.INK)

        // The burning rim: layered arcs from wide haze to a white-hot core.
        val flare = f.wave(cx + radius, cy, 160f).coerceAtMost(1.2f)
        val sweep = 150f * (0.2f + reveal * 0.8f)
        val start = -70f + (150f - sweep) / 2f
        k.rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val s = k.stroke
        k.additive(s, f.night)
        val layers = if (f.night) arrayOf(
            Triple(f.c1, 42f, 26), Triple(f.c2, 20f, 70), Triple(f.c2, 9f, 150), Triple(mix(f.c2, Color.WHITE, 0.55f), 4f, 210), Triple(Color.WHITE, 1.8f, 255),
        ) else arrayOf(
            Triple(f.c2, 16f, 50), Triple(f.c2, 5f, 150), Triple(f.c1, 1.6f, 200),
        )
        for ((color, width, alphaValue) in layers) {
            s.color = color
            s.alpha = (alphaValue * (0.6f + reveal * 0.4f) + flare * 60f).toInt().coerceIn(0, 255)
            s.strokeWidth = width * (1f + flare * 0.6f)
            canvas.drawArc(k.rect, start, sweep, false, s)
        }
        k.reset(s)

        // A thin planetary limb near the bottom, kept dim so the dock stays readable.
        k.rect.set(-900f, h * 0.885f, 1900f, h * 0.885f + 2600f)
        s.color = if (f.night) f.c1 else f.c1
        k.additive(s, f.night)
        s.alpha = (if (f.night) 110 else 120)
        s.strokeWidth = 1.6f
        canvas.drawArc(k.rect, 205f, 130f, false, s)
        k.reset(s)
    }

    // endregion
}
