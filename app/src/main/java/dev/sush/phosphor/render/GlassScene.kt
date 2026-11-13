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
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Glass in front of light. The light is a [GradientField] (soft lamps, bands, and the finger's
 * wake as a streak of light); the glass redraws it per facet:
 *
 * - **Fluted glass**: every rib squeezes, shifts and shears a wider slice of the light, once
 *   per colour channel at slightly different strengths, so rib edges split into red and blue
 *   like real dispersion. Each rib also focuses a bright caustic line and catches a specular lip.
 * - **Soft refraction**: ball lenses flip and magnify the light (again per channel, so their
 *   rims fringe spectrally), cast a soft shadow and a caustic focus spot on the backdrop.
 *
 * A swipe drags the lamp and leaves a refraction scar; holding focuses the light (the nearest
 * lens burns a point); release blooms it out across the glass.
 */
internal class GlassScene(private val k: RenderKit) : GradientField.Source {
    private val field = GradientField()
    private val screen = GradientScreen()
    private val lensSpring = Spring(LENSES)
    private var grabbed = -1
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
    private val lines = FloatArray(LENSES * 4)

    // Palette as 0..1 channels.
    private val ground = FloatArray(3)
    private val ink1 = FloatArray(3)
    private val ink2 = FloatArray(3)
    private val core = FloatArray(3)

    private fun prepare(f: Frame) {
        split(f.c0, ground); split(f.c1, ink1); split(f.c2, ink2)
        split(if (f.night) mix(f.c1, Color.WHITE, 0.55f) else mix(f.c1, f.c2, 0.5f), core)
        val next = f.c0.toLong() * 7 + if (f.night) 1 else 2
        if (next == key && flute != null) return
        key = next
        val dark = if (f.night) 0x8C000000.toInt() else alpha(mix(f.c2, Color.BLACK, 0.3f), 70)
        val light = if (f.night) 0x38FFFFFF else 0xB3FFFFFF.toInt()
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
        // The lamp drifts on its own; a held finger carries it.
        val a = f.amount
        val baseX = if (lenses) 560f + sin(f.t * 0.41f) * 240f * a else 640f + sin(f.t * 0.5f) * 280f * a
        val baseY = f.h * (if (lenses) 0.46f else 0.5f) + cos(f.t * 0.37f) * f.h * 0.12f * a
        lampX = baseX + (f.dragX - baseX) * f.drag
        lampY = baseY + (f.dragY - baseY) * f.drag
        field.ensure(f, 40)
        field.fill(f, this)
        if (lenses) lenses(canvas, f) else fluted(canvas, f)
    }

    // region Light behind the glass

    override fun shade(f: Frame, x: Float, y: Float, out: GradientField) {
        // A token smear so the field reports the wake; the light is not dragged, it is drawn.
        out.displace(f, x, y, smear = 1f, lens = 0f, ripple = 26f)
        val sx = x + out.dx + f.shiftX(0.4f)
        val sy = y + out.dy + f.shiftY(0.3f)
        val v = y / f.h
        val t = f.t
        val base = if (f.night) 0.14f + 0.22f * smooth((v - 0.1f) / 0.32f) - 0.2f * smooth((v - 0.84f) / 0.08f)
            else 0.45f + 0.3f * smooth((v - 0.1f) / 0.4f)
        // The lamp tightens and brightens as a held finger gathers charge.
        val focus = 1f - 0.55f * f.charge * f.drag
        val ex = (sx - lampX) / (360f * focus)
        val ey = (sy - lampY) / (520f * focus)
        val lamp = exp(-(ex * ex + ey * ey)) * (1f + f.charge * f.drag * 0.5f)
        var w1: Float
        var w2: Float
        var hot: Float
        if (lensMode) {
            // Two soft lights and a long diagonal streak of light, as in a lens-flare photograph.
            val bx = sx - 260f - sin(t * 0.23f) * 120f
            val by = sy - f.h * 0.74f - cos(t * 0.19f) * 90f
            val second = exp(-(bx * bx) / (420f * 420f) - (by * by) / (300f * 300f))
            val d = (sx * 0.42f - sy * 0.91f + f.h * 0.52f + sin(t * 0.17f) * 80f) / 120f
            val streak = exp(-d * d) * (0.6f + 0.4f * sin(sx * 0.004f + t * 0.3f))
            w1 = lamp * base * 0.95f + streak * base * 0.4f
            w2 = second * base * 1.3f + (1f - lamp) * base * 0.2f
            hot = lamp * lamp * base * 0.4f + streak * streak * base * 0.6f
        } else {
            // Diagonal violet bands sweep through an orange glow.
            val b = (sx * 0.55f + sy * 0.83f) / 1000f
            val band = 0.5f + 0.5f * sin(b * 6.8f - t * 0.35f + 0.6f * sin(sx * 0.003f + t * 0.2f))
            val violet = smooth((band - 0.4f) / 0.35f)
            w1 = (lamp * 0.8f + (1f - violet) * 0.42f) * base * (1f - violet * 0.6f)
            w2 = violet * base
            hot = lamp * lamp * lamp * base * 0.5f
        }
        // The finger's wake: a ribbon of light, which the glass then refracts.
        w1 += out.wake * 0.45f + out.crest * 0.2f
        hot += out.wake * 0.35f + out.crest * 0.25f
        val reveal = smooth(f.reveal * 1.3f)
        w1 = (w1 * reveal).coerceIn(0f, 1f); w2 = (w2 * reveal).coerceIn(0f, 1f); hot = (hot * reveal).coerceIn(0f, 1f)
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
        val t = f.t
        val a = f.amount
        val p = k.fill
        val s = k.stroke
        val glassShift = f.shiftX(1.2f)
        // Flutes narrow from left to right, as in a pane photographed at an angle.
        var x = -14f + glassShift % 1f
        var index = 0
        val flowX = (f.flowX / 2500f).coerceIn(-1f, 1f)
        while (x < 1014f) {
            val width = 24f + 104f * (1f - (x / 1000f).coerceIn(0f, 1f)).pow(2f)
            val cx = x + width / 2f
            // Where the finger passed, the rib was "scratched": it splits and drags the light.
            var scar = 0f
            for (i in 0 until f.trail) {
                val d = (cx - f.trailX[i]) / (width * 0.5f + 30f)
                val v = exp(-d * d) * f.trailA[i]
                if (v > scar) scar = v
            }
            val wave = f.waveX(cx, 90f)
            val near = f.dragInfluence(cx, f.dragY, 200f)
            val shift = (cx - 500f) * 0.2f + sin(index * 1.7f) * 40f + sin(t * 0.31f + index * 0.43f) * 40f * a -
                flowX * 70f * (scar + near) + wave * 30f * sin(index * 2.3f)
            val shear = 0.22f * sin(index * 0.9f + t * 0.17f) + scar * 0.25f * flowX
            val scale = 0.27f - wave * 0.05f + near * f.charge * 0.1f
            val spread = 0.035f + scar * 0.12f + wave * 0.05f + near * f.charge * 0.06f
            val saved = canvas.save()
            canvas.clipRect(x, 0f, x + width + 0.5f, h)
            refract(canvas, f, cx, h * 0.5f, scale, 1f, shift, shear, spread)

            // Caustic: the rib focuses a narrow band of light into a bright thread.
            val focusX = x + width * (0.58f + 0.22f * sin(index * 1.3f + t * 0.23f) * 0.5f + 0.12f * ((lampX - cx) / 1000f).coerceIn(-1f, 1f))
            val boost = 1f + f.charge * near * 2.5f + scar * 1.5f + wave
            val cs = canvas.save()
            canvas.clipRect(focusX - 2.2f, 0f, focusX + 2.2f, h)
            canvas.translate(focusX, 0f)
            canvas.scale(0.06f, 1f)
            canvas.translate(-(focusX + shift * 1.4f), 0f)
            plain.alpha = (min255(if (f.night) 150f * boost else 110f * boost))
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
            s.alpha = min255((if (f.night) 34f else 120f) + field.sample(cx, lampY) * 70f + scar * 80f)
            s.strokeWidth = 1.3f
            canvas.drawLine(x + width * 0.88f, -10f, x + width * 0.88f, h + 10f, s)
            k.normal(s)
            x += width
            index++
        }
        k.reset(p)
        k.reset(s)
        focusBurn(canvas, f)
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

        // The lens nearest a new touch follows the finger until released.
        if (f.drag > 0.5f && grabbed < 0) {
            var best = Float.MAX_VALUE
            for (i in 0 until LENSES) {
                val d = sqrt((f.dragX - lensX(i, t, a)).pow(2) + (f.dragY - lensY(i, t, a, h)).pow(2)) - RADII[i]
                if (d < best) { best = d; grabbed = i }
            }
        }
        if (f.drag < 0.2f) grabbed = -1
        for (i in 0 until LENSES) {
            val tx = if (i == grabbed) (f.dragX - lensX(i, t, a)) * 0.85f else 0f
            val ty = if (i == grabbed) (f.dragY - lensY(i, t, a, h)) * 0.85f else 0f
            lensSpring.step(i, tx, ty, f.dt, 30f, 8f)
        }
        if (!f.busy && f.dt == 0f) lensSpring.reset()

        // The finger's glide nudges lenses it passes, like bubbles in a current.
        var nearest = -1
        var nearestD = Float.MAX_VALUE
        for (i in 0 until LENSES) {
            val x0 = lensX(i, t, a) + lensSpring.x[i] + f.shiftX(0.5f + i * 0.25f)
            val y0 = lensY(i, t, a, h) + lensSpring.y[i]
            val push = exp(-((x0 - f.dragX).pow(2) + (y0 - f.dragY).pow(2)) / (420f * 420f))
            val x = x0 + f.flowX * 0.05f * push
            val y = y0 + f.flowY * 0.05f * push
            lines[i * 4] = x; lines[i * 4 + 1] = y
            val d = sqrt((x - f.dragX).pow(2) + (y - f.dragY).pow(2))
            if (d < nearestD) { nearestD = d; nearest = i }
        }
        for (i in 0 until LENSES) {
            val focus = if (i == nearest) f.charge * f.drag else 0f
            val wobble = f.wave(lines[i * 4], lines[i * 4 + 1], 160f)
            val r = RADII[i] * (1f + wobble * 0.06f * sin(f.now * 18f))
            lens(canvas, f, lines[i * 4], lines[i * 4 + 1], r, focus, wobble)
        }
    }

    private fun lensX(i: Int, t: Float, a: Float) = BASE_X[i] + sin(t * (0.31f + i * 0.07f) + i * 1.9f) * (40f + i * 9f) * a
    private fun lensY(i: Int, t: Float, a: Float, h: Float) = BASE_Y[i] * h + cos(t * (0.27f + i * 0.05f) + i * 0.7f) * (55f + i * 6f) * a

    private fun lens(canvas: Canvas, f: Frame, x: Float, y: Float, r: Float, focus: Float, wobble: Float) {
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
        val spot = r * (0.26f - 0.2f * focus)
        val cx = x + ux * r * 0.55f
        val cy = y + uy * r * 0.55f + r * 0.12f
        val power = (0.35f + light) * (1f + focus * 2.5f + wobble)
        k.glow(canvas, k.glowA, cx, cy, spot * 2.6f, spot * 2.2f, min255(70f * power), mode)
        k.glow(canvas, if (night) k.glowWhite else k.glowB, cx, cy, spot, spot * 0.85f, min255(150f * power), mode)

        val saved = canvas.save()
        clip.reset()
        clip.addCircle(x, y, r, Path.Direction.CW)
        canvas.clipPath(clip)
        // Inside, the world is flipped and magnified, as through a ball lens; focusing a lens
        // pulls the magnification up and the dispersion out toward the rim.
        val m = 1.4f + focus * 0.9f + wobble * 0.2f
        refract(canvas, f, x, y, -m, -m, 0f, 0f, 0.05f + focus * 0.05f)
        val flipped = canvas.save()
        canvas.translate(x, y)
        canvas.scale(-m, -m)
        canvas.translate(-x, -y)
        printScreen(canvas, f)
        // A lens gathers light: its body glows a little brighter than the world around it.
        plain.alpha = if (night) 45 else 35
        k.blend(plain, if (night) RenderKit.Mode.ADD else RenderKit.Mode.INK)
        field.drawClamped(canvas, plain, x - 1000f, y - 1000f, x + 1000f, y + 1000f)
        k.reset(plain)
        canvas.restoreToCount(flipped)
        canvas.translate(x, y)
        canvas.save()
        canvas.rotate(f.t * 9f + x * 0.2f)
        k.stroke.shader = holo
        k.stroke.strokeWidth = r * 0.07f
        k.stroke.alpha = if (night) 120 else 110
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
        screen.draw(canvas, f, field, gap, if (f.night) 0.8f else 0.6f, 10f / f.detail.coerceAtLeast(0.35f), 30f, lines = false)
    }

    private fun shadow(canvas: Canvas, f: Frame, x: Float, y: Float, r: Float) {
        // A soft contact shadow: dark at night, a pale ink by day.
        k.glow(canvas, k.glowGround, x, y, r * 1.1f, r * 1.0f, if (f.night) 150 else 60,
            if (f.night) RenderKit.Mode.NORMAL else RenderKit.Mode.INK)
    }

    // endregion

    /** Holding gathers the light behind the glass into a burning point under the finger. */
    private fun focusBurn(canvas: Canvas, f: Frame) {
        val mode = if (f.night) RenderKit.Mode.ADD else RenderKit.Mode.INK
        val c = f.charge * f.drag
        if (c > 0f) {
            val r = 160f - 120f * c
            k.glow(canvas, k.glowA, f.dragX, f.dragY, r * 1.6f, r * 2.6f, min255(c * 140f), mode)
            k.glow(canvas, if (f.night) k.glowWhite else k.glowB, f.dragX, f.dragY, r * 0.35f, r * 1.4f, min255(c * 220f), mode)
        }
        for (i in 0 until f.pulses) {
            val bloom = (f.pulseA[i] - 0.4f).coerceAtLeast(0f)
            if (bloom <= 0f) continue
            val r = 100f + f.pulseR[i] * 0.4f
            k.glow(canvas, k.glowA, f.pulseX[i], f.pulseY[i], r * 0.7f, r * 1.6f, min255(bloom * 120f), mode)
        }
    }

    private fun min255(v: Float) = v.toInt().coerceIn(0, 255)

    fun release() {
        lensSpring.reset(); grabbed = -1; flute = null; rim = null; holo = null; key = 0
        field.release()
        screen.release()
    }

    private companion object {
        const val LENSES = 5
        val BASE_X = floatArrayOf(290f, 730f, 200f, 820f, 520f)
        val BASE_Y = floatArrayOf(0.31f, 0.55f, 0.76f, 0.2f, 0.8f)
        val RADII = floatArrayOf(230f, 300f, 150f, 90f, 70f)
    }
}
