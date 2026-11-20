package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

internal class OrbitRingfall(private val orbit: OrbitKit) {
    private val k = orbit.k
    private val ridge = Path()
    private val crest = Path()
    private var ridgeKey = 0f

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        val reveal = smooth(f.reveal)
        orbit.stars(canvas, f, 200, 0f, h, 0.2f)
        val cx = 72f + sin(f.t * 0.17f) * 38f * f.amount + f.shiftX(0.7f)
        val cy = h * 0.554f + f.shiftY(0.6f)
        val radius = 526f
        val tilt = 31f + sin(f.t * 0.22f) * 7f * f.amount + f.shiftX(1f) * 0.08f
        val flat = 0.78f + cos(f.t * 0.25f) * 0.09f * f.amount + f.shiftY(1f) * 0.0015f
        orbit.trails(canvas, f, cx - 46f, cy - 28f, flat, tilt, 1500, 53, 0.65f, 1470f)

        // The planet itself: an occluding disc of ground with a faintly lit face.
        val p = k.fill
        p.shader = null
        k.normal(p)
        p.color = f.c0
        canvas.drawCircle(cx, cy, radius, p)
        k.glow(canvas, k.glowA, cx + radius * 0.5f, cy - radius * 0.1f, radius * 0.9f, radius * 0.9f,
            if (f.night) 16 else 26, if (f.night) RenderKit.Mode.SCREEN else RenderKit.Mode.INK)

        // The burning rim: layered arcs from wide haze to a white-hot core.
        val flare = (0.5f + 0.5f * sin(f.t * 0.45f)) * f.amount * 0.5f
        val sweep = 171f * (0.2f + reveal * 0.8f)
        val start = -96f + (171f - sweep) / 2f
        k.rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val s = k.stroke
        k.additive(s, f.night)
        val layers = if (f.night) arrayOf(
            Triple(f.c1, 76f, 32), Triple(f.c1, 35f, 63), Triple(f.c2, 16f, 105), Triple(mix(f.c2, Color.WHITE, 0.55f), 6f, 195), Triple(Color.WHITE, 2.8f, 255),
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

        // A small moon crosses in front of the planet and its bright rings.
        val transit = f.t * 0.23f + 0.7f
        val moonX = cx + radius + 78f + sin(transit) * 123f + f.shiftX(1f)
        val moonY = cy - 182f + cos(transit) * 84f - sin(transit) * 55f + f.shiftY(0.7f)
        p.color = if (f.night) mix(f.c0, Color.BLACK, 0.5f) else f.c0
        val moonRadius = 19f
        canvas.drawCircle(moonX, moonY, moonRadius, p)
        s.color = if (f.night) mix(f.c2, Color.WHITE, 0.5f) else f.c2
        s.strokeWidth = 2.2f
        k.rect.set(moonX - moonRadius, moonY - moonRadius, moonX + moonRadius, moonY + moonRadius)
        canvas.drawArc(k.rect, -82f, 142f, false, s)

        val horizon = h * 0.84f
        atmosphere(canvas, f, horizon, reveal, flare)
        clouds(canvas, f, horizon, reveal)
        ridge(canvas, f)
        k.reset(s)
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
        val rows = (3 + 3 * f.detail).toInt()
        for (row in 0 until rows) {
            val z = row / (rows - 1f)
            val y = horizon + 22f + z.pow(1.4f) * (f.h - horizon) * 0.9f
            val size = 34f + z * 120f
            val pitch = size * 1.25f
            val n = (1300f / pitch).toInt() + 2
            val span = n * pitch
            val drift = f.t * 0.44f * (6f - z * 4f) + f.shiftX(0.3f + z)
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
    private fun ridge(canvas: Canvas, f: Frame) {
        val h = f.h
        val dx = f.shiftX(1.3f)
        if (ridgeKey != h) {
            ridgeKey = h
            crest.reset()
            crest.moveTo(-90f, h * 0.9f)
            crest.cubicTo(50f, h * 0.885f, 140f, h * 0.858f, 214f, h * 0.853f)
            crest.cubicTo(262f, h * 0.85f, 300f, h * 0.858f, 360f, h * 0.874f)
            crest.cubicTo(440f, h * 0.896f, 520f, h * 0.93f, 640f, h * 0.97f)
            crest.cubicTo(700f, h * 0.99f, 760f, h * 1.0f, 800f, h * 1.02f)
            ridge.set(crest)
            ridge.lineTo(800f, h + 40f)
            ridge.lineTo(-90f, h + 40f)
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
        k.figure(canvas, 238f + dx, h * 0.8515f, 22f, if (f.night) Color.BLACK else f.c1)
    }

}
