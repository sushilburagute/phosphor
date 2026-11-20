package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A drifting lamp and diagonal light bands form a [GradientField]. Each glass facet
 * resamples that field at its own scale, offset and dispersion.
 *
 * - **Fluted glass**: every rib squeezes, shifts and shears a wider slice of the light, once
 *   per colour channel at slightly different strengths, so rib edges split into red and blue
 *   like real dispersion. Each rib also focuses a bright caustic line and catches a specular lip.
 * - **Soft refraction**: ball lenses flip and magnify the light (again per channel, so their
 *   rims fringe spectrally), cast a soft shadow and a caustic focus spot on the backdrop.
 */
internal class GlassScene(private val k: RenderKit) : GradientField.Source {
    private val field = GradientField()
    private val screen = GradientScreen()
    private var flute: Shader? = null
    private var rim: Shader? = null
    private var holo: Shader? = null
    private var key = 0L
    private val clip = Path()
    private var lensMode = false
    private var lampX = 0f
    private var lampY = 0f

    // Per-channel copies of the light, added back together (exactly the original when aligned).
    private val channels = Array(3) { c ->
        Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = LightingColorFilter(0xFF shl (16 - c * 8), 0)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
    }
    private val plain = Paint(Paint.FILTER_BITMAP_FLAG)

    // Palette as 0..1 channels.
    private val ground = FloatArray(3)
    private val ink1 = FloatArray(3)
    private val ink2 = FloatArray(3)
    private val core = FloatArray(3)

    private fun prepare(f: Frame) {
        split(f.c0, ground); split(f.c1, ink1); split(f.c2, ink2)
        split(if (f.night) mix(f.c1, Color.WHITE, 0.55f) else mix(f.c1, f.c2, 0.5f), core)
        val next = f.c0.toLong() * 31 + f.c2 + if (f.night) 1 else 2
        if (next == key && flute != null) return
        key = next
        val dark = if (f.night) 0x59000000 else alpha(mix(f.c2, Color.BLACK, 0.3f), 42)
        val light = if (f.night) 0x26FFFFFF else 0x73FFFFFF
        // Across one flute: shadowed left wall, clear middle, bright lip, then the hard edge.
        flute = LinearGradient(0f, 0f, 1f, 0f, intArrayOf(dark, 0x00000000, 0x00FFFFFF, light, 0x00FFFFFF, dark),
            floatArrayOf(0f, 0.22f, 0.62f, 0.84f, 0.92f, 1f), Shader.TileMode.CLAMP)
        // Ball-lens rim: clear centre, a Fresnel brightening toward the edge, a dark outer line.
        rim = RadialGradient(0f, 0f, 1f,
            intArrayOf(0x00FFFFFF, 0x00FFFFFF, if (f.night) 0x18FFFFFF else 0x40FFFFFF, if (f.night) 0x80FFFFFF.toInt() else 0xCCFFFFFF.toInt(), if (f.night) 0x99000000.toInt() else 0x50000000),
            floatArrayOf(0f, 0.72f, 0.9f, 0.975f, 1f), Shader.TileMode.CLAMP)
        // Thin-film colour on the rim, like the holographic edge of a real lens.
        val spectrum = intArrayOf(0xFFFF4D6D.toInt(), 0xFFFFB347.toInt(), 0xFFF8F06A.toInt(), 0xFF5BE3A0.toInt(),
            0xFF4CC9F0.toInt(), 0xFF7B6CFF.toInt(), 0xFFE05CFF.toInt(), 0xFFFF4D6D.toInt())
        holo = SweepGradient(0f, 0f, spectrum, null)
    }

    private fun split(color: Int, out: FloatArray) {
        out[0] = Color.red(color) / 255f; out[1] = Color.green(color) / 255f; out[2] = Color.blue(color) / 255f
    }

    fun draw(canvas: Canvas, f: Frame, lenses: Boolean) {
        lensMode = lenses
        prepare(f)
        val t = f.t * GRADIENT_PACE
        val a = f.amount
        lampX = if (lenses) 685f + sin(t * 0.41f) * 180f * a else 380f + sin(t * 0.5f) * 195f * a
        lampX += f.shiftX(0.45f)
        lampY = f.h * (if (lenses) 0.44f else 0.54f) + cos(t * 0.37f) * f.h * 0.12f * a + f.shiftY(0.45f)
        field.ensure(f, 40)
        field.fill(f, this)
        if (lenses) lenses(canvas, f) else fluted(canvas, f)
    }

    // region Light behind the glass

    override fun shade(f: Frame, x: Float, y: Float, out: GradientField) {
        val sx = x - f.shiftX(0.3f)
        val sy = y - f.shiftY(0.3f)
        val v = y / f.h
        val t = f.t * GRADIENT_PACE
        val base = if (f.night) 0.14f + 0.22f * smooth((v - 0.1f) / 0.32f) - 0.2f * smooth((v - 0.84f) / 0.08f)
            else 0.45f + 0.3f * smooth((v - 0.1f) / 0.4f)
        val ex = (x - lampX) / 360f
        val ey = (y - lampY) / 520f
        val lamp = exp(-(ex * ex + ey * ey))
        var w1: Float
        var w2: Float
        var hot: Float
        if (lensMode) {
            // Two soft lights and a long diagonal streak of light, as in a lens-flare photograph.
            val bx = sx - 226f - sin(t * 0.23f) * 120f
            val by = sy - f.h * 0.67f - cos(t * 0.19f) * 90f
            val second = exp(-(bx * bx) / (560f * 560f) - (by * by) / (420f * 420f))
            val d = (sx * 0.50f - sy * 0.87f + f.h * 0.31f + sin(t * 0.17f) * 80f) / 175f
            val streak = exp(-d * d) * (0.6f + 0.4f * sin(sx * 0.004f + t * 0.3f))
            w1 = lamp * base * 1.3f + streak * base * 0.45f
            w2 = second * base * 1.22f + (1f - lamp) * base * 0.12f
            hot = lamp * lamp * base * 0.48f + streak * streak * base * 0.38f
        } else {
            // A violet fold crosses one continuous wash of apricot light.
            val fold = (sy - sx * 0.69f - f.h * 0.34f + sin(sx * 0.004f + t * 0.32f) * 105f) / 255f
            val crease = exp(-fold * fold)
            val edge = fold - 1.03f
            val warm = exp(-edge * edge * 0.45f)
            w1 = (lamp * 0.76f + warm * 1.05f) * base * (1f - crease * 0.77f)
            w2 = (crease * 0.93f + (1f - warm) * 0.36f) * base
            hot = warm * lamp * base * 0.68f
        }
        val brightness = when {
            !f.night -> 0.9f
            lensMode -> 0.82f
            else -> 0.74f
        }
        val exposure = smooth(f.reveal * 1.3f) * brightness
        w1 = (w1 * exposure).coerceIn(0f, 1f); w2 = (w2 * exposure).coerceIn(0f, 1f); hot = (hot * exposure).coerceIn(0f, 1f)
        if (f.night) {
            out.r = ground[0] + ink1[0] * w1 + ink2[0] * w2 + core[0] * hot
            out.g = ground[1] + ink1[1] * w1 + ink2[1] * w2 + core[1] * hot
            out.b = ground[2] + ink1[2] * w1 + ink2[2] * w2 + core[2] * hot
        } else {
            out.r = ground[0] * (1f - w1 * (1f - ink1[0])) * (1f - w2 * (1f - ink2[0])) * (1f - hot * 0.6f * (1f - core[0]))
            out.g = ground[1] * (1f - w1 * (1f - ink1[1])) * (1f - w2 * (1f - ink2[1])) * (1f - hot * 0.6f * (1f - core[1]))
            out.b = ground[2] * (1f - w1 * (1f - ink1[2])) * (1f - w2 * (1f - ink2[2])) * (1f - hot * 0.6f * (1f - core[2]))
        }
        out.cover = (w1 * 0.7f + hot).coerceIn(0f, 1f)
    }

    /**
     * Draw the light squeezed by [scale] around [cx] (sampling around [cx] + [shift]), sheared by
     * [shear], with [spread] extra squeeze per colour channel. Callers clip to the facet first.
     */
    private fun refract(canvas: Canvas, f: Frame, cx: Float, cy: Float, scaleX: Float, scaleY: Float, shift: Float, shear: Float, spread: Float) {
        k.fill.color = Color.BLACK
        k.normal(k.fill)
        canvas.drawRect(cx - 600f, -10f, cx + 600f, f.h + 10f, k.fill)
        for (c in 0 until 3) {
            val s = 1f + spread * (c - 1)
            val saved = canvas.save()
            canvas.translate(cx, cy)
            canvas.skew(0f, shear)
            canvas.scale(scaleX * s, scaleY * (1f + (s - 1f) * 0.5f))
            canvas.translate(-(cx + shift), -cy)
            field.drawClamped(canvas, channels[c], cx + shift - 1400f, -400f, cx + shift + 1400f, f.h + 400f)
            canvas.restoreToCount(saved)
        }
    }

    // endregion

    // region Fluted glass

    private fun fluted(canvas: Canvas, f: Frame) {
        val h = f.h
        val t = f.t * GRADIENT_PACE
        val a = f.amount
        val p = k.fill
        val s = k.stroke
        val glassShift = f.shiftX(1.2f)
        // Fine flutes narrow from left to right, as in a pane photographed at an angle.
        var x = -72f + glassShift
        var index = 0
        while (x < 1014f) {
            val width = 22f + 20f * (1f - (x / 1000f).coerceIn(0f, 1f))
            val cx = x + width / 2f
            // Each flute sweeps a slightly different slice of the lamp as it passes behind the pane.
            val sweep = sin(t * 0.66f + index * 0.43f) * a
            val shift = (cx - 500f) * 0.13f + sin(index * 1.7f) * 16f + sweep * 26f
            val shear = 0.10f * sin(index * 0.9f + t * 0.38f)
            val scale = 0.32f + sweep * 0.055f
            val spread = 0.032f + sweep * 0.009f
            val saved = canvas.save()
            canvas.clipRect(x, 0f, x + width + 0.5f, h)
            refract(canvas, f, cx, h * 0.5f, scale, 1f, shift, shear, spread)

            // Caustic: the rib focuses a narrow band of light into a bright thread.
            val focusX = x + width * (0.58f + 0.22f * sin(index * 1.3f + t * 0.23f) * 0.5f + 0.12f * ((lampX - cx) / 1000f).coerceIn(-1f, 1f))
            val cs = canvas.save()
            canvas.clipRect(focusX - 1.5f, 0f, focusX + 1.5f, h)
            canvas.translate(focusX, 0f)
            canvas.scale(0.06f, 1f)
            canvas.translate(-(focusX + shift * 1.4f), 0f)
            plain.alpha = if (f.night) 80 else 75
            k.blend(plain, if (f.night) RenderKit.Mode.ADD else RenderKit.Mode.SCREEN)
            field.drawClamped(canvas, plain, focusX + shift * 1.4f - 600f, -10f, focusX + shift * 1.4f + 600f, h + 10f)
            k.normal(plain)
            canvas.restoreToCount(cs)

            // Shading across the flute, then the specular lip.
            val shade = canvas.save()
            canvas.translate(x, 0f)
            canvas.scale(width, 1f)
            p.shader = flute
            canvas.drawRect(0f, 0f, 1f, h, p)
            canvas.restoreToCount(shade)
            p.shader = null
            canvas.restoreToCount(saved)

            k.luminous(s, f.night)
            s.color = Color.WHITE
            s.alpha = min255((if (f.night) 23f else 75f) + field.sample(cx, lampY) * 55f)
            s.strokeWidth = 0.9f
            canvas.drawLine(x + width * 0.88f, -10f, x + width * 0.88f, h + 10f, s)
            k.normal(s)
            x += width
            index++
        }
        k.reset(p)
        k.reset(s)
    }

    // endregion

    // region Soft refraction

    private fun lenses(canvas: Canvas, f: Frame) {
        val h = f.h
        val t = f.t
        val a = f.amount
        // The backdrop: soft light printed through a halftone, so the lenses have detail to bend.
        k.reset(plain)
        field.draw(canvas, f, plain)
        printScreen(canvas, f)

        // Cropped, overlapping lenses leave the rim and its shadow as the main forms.
        lens(canvas, f, 673f + sin(t * 0.38f) * 78f * a + f.shiftX(0.65f),
            h * 0.62f + cos(t * 0.29f) * 70f * a + f.shiftY(0.65f), 370f)
        lens(canvas, f, 875f + cos(t * 0.33f) * 105f * a + f.shiftX(1f),
            h * 0.39f + sin(t * 0.31f) * 89f * a + f.shiftY(1f), 415f)
        lens(canvas, f, 82f + sin(t * 0.34f) * 112f * a + f.shiftX(1.4f),
            h * 0.50f + cos(t * 0.28f) * 99f * a + f.shiftY(1.4f), 452f)
    }

    private fun lens(canvas: Canvas, f: Frame, x: Float, y: Float, r: Float) {
        val night = f.night
        val mode = if (night) RenderKit.Mode.ADD else RenderKit.Mode.INK
        // Light arrives from the lamp; the shadow falls away from it, the caustic beyond it.
        val dx = x - lampX
        val dy = y - lampY
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val ux = dx / d; val uy = dy / d
        val sx = x + ux * r * 0.32f
        val sy = y + uy * r * 0.32f + r * 0.18f
        shadow(canvas, f, sx, sy, r)
        val light = field.sample(x, y)
        val spot = r * (0.27f - 0.08f * light)
        val cx = x + ux * r * 0.55f
        val cy = y + uy * r * 0.55f + r * 0.12f
        val power = 0.35f + light
        k.glow(canvas, k.glowA, cx, cy, spot * 2.6f, spot * 2.2f, min255(70f * power), mode)
        k.glow(canvas, if (night) k.glowWhite else k.glowB, cx, cy, spot, spot * 0.85f, min255(150f * power), mode)

        val saved = canvas.save()
        clip.reset()
        clip.addCircle(x, y, r, Path.Direction.CW)
        canvas.clipPath(clip)
        // Fresnel reflections and a magnified, inverted image distinguish a lens from a flat disc.
        val m = 1.4f + light * 0.24f
        refract(canvas, f, x, y, -m, -m, 0f, 0f, 0.027f)
        val flipped = canvas.save()
        canvas.translate(x, y)
        canvas.scale(-m, -m)
        canvas.translate(-x, -y)
        printScreen(canvas, f)
        // A lens gathers light: its body glows a little brighter than the world around it.
        plain.alpha = if (night) 33 else 35
        k.blend(plain, if (night) RenderKit.Mode.ADD else RenderKit.Mode.INK)
        field.drawClamped(canvas, plain, x - 1000f, y - 1000f, x + 1000f, y + 1000f)
        k.reset(plain)
        canvas.restoreToCount(flipped)
        canvas.translate(x, y)
        canvas.save()
        canvas.rotate(f.t * GRADIENT_PACE * 9f + x * 0.2f)
        k.stroke.shader = holo
        k.stroke.strokeWidth = r * 0.055f
        k.stroke.alpha = if (night) 42 else 38
        k.blend(k.stroke, if (night) RenderKit.Mode.SCREEN else RenderKit.Mode.INK)
        canvas.drawCircle(0f, 0f, r * 0.93f, k.stroke)
        k.reset(k.stroke)
        k.stroke.strokeWidth = 1f
        canvas.restore()
        canvas.scale(r, r)
        k.fill.shader = rim
        canvas.drawCircle(0f, 0f, 1f, k.fill)
        k.reset(k.fill)
        canvas.restoreToCount(saved)

        // Specular: a soft window reflection and a thin crescent on the far rim.
        val s = k.stroke
        k.glow(canvas, k.glowWhite, x - r * 0.38f, y - r * 0.42f, r * 0.26f, r * 0.15f, if (night) 120 else 200,
            if (night) RenderKit.Mode.SCREEN else RenderKit.Mode.NORMAL)
        k.luminous(s, night)
        s.color = if (night) mix(f.c2, Color.WHITE, 0.4f) else f.c2
        s.alpha = 150
        s.strokeWidth = 2.4f
        k.rect.set(x - r * 0.9f, y - r * 0.9f, x + r * 0.9f, y + r * 0.9f)
        canvas.drawArc(k.rect, 20f, 110f, false, s)
        s.color = if (night) mix(f.c1, Color.WHITE, 0.3f) else f.c1
        s.alpha = 120
        s.strokeWidth = 1.6f
        k.rect.set(x - r * 0.96f, y - r * 0.96f, x + r * 0.96f, y + r * 0.96f)
        canvas.drawArc(k.rect, 200f, 70f, false, s)
        k.reset(s)
    }

    private fun printScreen(canvas: Canvas, f: Frame) {
        val gap = if (f.night) mix(f.c0, Color.BLACK, 0.35f) else mix(f.c0, Color.WHITE, 0.6f)
        screen.draw(canvas, f, field, gap, if (f.night) 0.39f else 0.34f, 10f / f.detail.coerceAtLeast(0.35f), 30f, lines = false)
    }

    private fun shadow(canvas: Canvas, f: Frame, x: Float, y: Float, r: Float) {
        // A soft contact shadow: dark at night, a pale ink by day.
        k.glow(canvas, k.glowGround, x, y, r * 1.1f, r * 1.0f, if (f.night) 150 else 60,
            if (f.night) RenderKit.Mode.NORMAL else RenderKit.Mode.INK)
    }

    // endregion

    private fun min255(v: Float) = v.toInt().coerceIn(0, 255)

    fun release() {
        flute = null; rim = null; holo = null; key = 0
        field.release()
        screen.release()
    }

}
