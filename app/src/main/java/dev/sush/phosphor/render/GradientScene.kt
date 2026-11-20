package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Flowing gradients, computed as a real domain-warped colour field (see [GradientField]).
 *
 * - **Daybreak**: clouds of colour that fold into each other, printed through
 *   an amplitude-modulated dot screen whose dots swell with the light.
 * - **Low tide**: broad, rolling folds crossed by thousands of fine
 *   fibres that carry the local colour ([GradientSilk]), with a hot sheen riding each crest.
 */
internal class GradientScene(private val k: RenderKit) : GradientField.Source {
    private val field = GradientField()
    private val screen = GradientScreen()
    private val fibres = GradientSilk()
    private var flowing = false

    // Palette as 0..1 channels, refreshed each frame (cheap) for the per-cell maths.
    private val ground = FloatArray(3)
    private val ink1 = FloatArray(3)
    private val ink2 = FloatArray(3)
    private val core = FloatArray(3)
    private var w1 = 0f
    private var w2 = 0f
    private var hot = 0f

    fun draw(canvas: Canvas, f: Frame, silk: Boolean) {
        flowing = silk
        palette(f)
        field.ensure(f, 48)
        field.fill(f, this)
        val p = k.fill
        k.reset(p)
        field.draw(canvas, f, p)

        if (silk) {
            fibres.draw(canvas, f, k, field, ANGLE)
        } else {
            val pitch = (11f + 9f * f.composition) / f.detail.coerceAtLeast(0.35f)
            val gap = if (f.night) mix(f.c0, Color.BLACK, 0.35f) else mix(f.c0, Color.WHITE, 0.55f)
            screen.draw(canvas, f, field, gap, if (f.night) 0.82f else 0.62f, pitch, 45f, lines = false)
        }
    }

    private fun palette(f: Frame) {
        split(f.c0, ground)
        split(f.c1, ink1)
        split(f.c2, ink2)
        split(if (f.night) mix(f.c1, Color.WHITE, 0.6f) else mix(mix(f.c1, f.c2, 0.5f), Color.BLACK, 0.18f), core)
    }

    private fun split(color: Int, out: FloatArray) {
        out[0] = Color.red(color) / 255f; out[1] = Color.green(color) / 255f; out[2] = Color.blue(color) / 255f
    }

    override fun shade(f: Frame, x: Float, y: Float, out: GradientField) {
        val sx = x + f.shiftX(0.8f)
        val sy = y + f.shiftY(0.5f)
        if (flowing) silkAt(f, sx, sy, x, y / f.h) else cloudAt(f, sx, sy, x, y / f.h)
        val reveal = 0.25f + 0.75f * smooth(f.reveal * 1.4f - (1f - y / f.h) * 0.4f)
        val a1 = (w1 * reveal).coerceIn(0f, 1f)
        val a2 = (w2 * reveal).coerceIn(0f, 1f)
        val ah = (hot * reveal).coerceIn(0f, 1f)
        if (f.night) {
            out.r = ground[0] + ink1[0] * a1 + ink2[0] * a2 + core[0] * ah
            out.g = ground[1] + ink1[1] * a1 + ink2[1] * a2 + core[1] * ah
            out.b = ground[2] + ink1[2] * a1 + ink2[2] * a2 + core[2] * ah
        } else {
            out.r = ground[0] * (1f - a1 * (1f - ink1[0])) * (1f - a2 * (1f - ink2[0])) * (1f - ah * (1f - core[0]))
            out.g = ground[1] * (1f - a1 * (1f - ink1[1])) * (1f - a2 * (1f - ink2[1])) * (1f - ah * (1f - core[1]))
            out.b = ground[2] * (1f - a1 * (1f - ink1[2])) * (1f - a2 * (1f - ink2[2])) * (1f - ah * (1f - core[2]))
        }
        // Dots swell with the light: small open dots in the shadows, merging in the glow.
        val presence = 1f - (1f - a1) * (1f - a2 * 0.85f) * (1f - ah)
        out.cover = smooth((presence - 0.04f) / 0.8f)
    }

    /**
     * Daybreak: the sun is just under a horizon that sits in the gap above the dock. Clouds
     * (three octaves of domain warp folding two inks together) are lit from below; the clock
     * and the label rows only get their dim, coloured undersides at night.
     */
    private fun cloudAt(f: Frame, x: Float, y: Float, screenX: Float, v: Float) {
        val t = f.t * GRADIENT_PACE
        var px = x / 1000f * 1.5f
        var py = y / 1000f * 1.5f
        px += 0.62f * sin(py * 1.3f + t * 0.23f + 0.5f)
        py += 0.62f * cos(px * 1.1f - t * 0.19f + 1.3f)
        px += 0.3f * sin(py * 2.7f - t * 0.31f + 2.1f)
        py += 0.3f * cos(px * 2.3f + t * 0.27f + 0.4f)
        px += 0.13f * sin(py * 5.3f + t * 0.41f)
        py += 0.13f * cos(px * 4.9f - t * 0.37f)
        val a = 0.5f + 0.5f * sin(px * 1.7f + 0.4f) * cos(py * 0.9f - 0.3f)
        val b = 0.5f + 0.5f * sin(py * 1.45f - px * 0.55f + 2.2f + t * 0.05f)
        val cloud1 = smooth((a - 0.2f) / 0.7f)
        val cloud2 = smooth((b - 0.22f) / 0.7f)
        // The horizon's glow, its edge torn by the same warp as the clouds.
        val edge = 0.83f + 0.012f * sin(px * 2.2f + t * 0.2f) + 0.008f * cos(py * 3.1f - t * 0.17f)
        val dv = (v - edge) / (if (v < edge) 0.045f else 0.04f)
        val sun = exp(-dv * dv) * (0.75f + 0.25f * sin(screenX * 0.0021f + t * 0.11f))
        val du = (v - edge) / 0.22f
        val sky = exp(-du * du)
        if (f.night) {
            val base = 0.2f + 0.3f * smooth((v - 0.08f) / 0.3f) - 0.3f * smooth((v - 0.84f) / 0.08f)
            w1 = cloud1 * (base * 0.8f + sky * 0.12f) + sun * (0.45f + 0.35f * cloud1)
            w2 = cloud2 * (1f - cloud1 * 0.5f) * (base * 1.05f + sky * 0.04f) + sun * 0.12f
            hot = sun * sun * (0.2f + 0.4f * cloud1)
        } else {
            val base = 0.62f + 0.38f * smooth((v - 0.1f) / 0.4f)
            w1 = cloud1 * base * 0.66f + sun * 0.3f
            w2 = cloud2 * (1f - cloud1 * 0.6f) * base * 0.85f + sun * 0.35f
            hot = cloud1 * cloud2 * base * 0.2f + sun * sun * 0.25f
        }
    }

    /**
     * Low tide: folds streaming along [ANGLE], their crests warped by slow travelling waves.
     * Blue hour above; the turquoise pools low, in the gap over the dock.
     */
    private fun silkAt(f: Frame, x: Float, y: Float, screenX: Float, v: Float) {
        val t = f.t * GRADIENT_PACE
        val along = (x * COS + y * SIN) / 1000f
        val across = (-x * SIN + y * COS) / 1000f
        val c = across + fibres.warp(along, across, t)
        val broad = 0.5f + 0.5f * sin(c * 4.4f + 0.8f + 0.4f * sin(t * 0.13f))
        val fine = 0.5f + 0.5f * sin(c * 19f + along * 0.8f + t * 0.17f)
        val deep = 0.5f + 0.5f * sin(c * 1.7f - along * 0.9f + 2.4f - t * 0.07f)
        val fold = smooth((broad - 0.15f) / 0.85f) * (0.78f + 0.22f * fine)
        val crest = 0.5f + 0.5f * sin(c * 4.4f + 0.55f + 0.4f * sin(t * 0.13f))
        val c2 = crest * crest; val c4 = c2 * c2
        val sheen = c4 * c4 * (0.55f + 0.45f * fine)
        val edge = 0.814f - f.composition * 0.22f + 0.014f * sin(along * 3.3f + t * 0.15f) + 0.017f * sin(f.t * 0.42f) * f.amount
        val dv = (v - edge) / (if (v < edge) 0.041f else 0.05f)
        val pool = exp(-dv * dv) * (0.7f + 0.3f * sin(screenX * 0.0023f - t * 0.09f + 1f))
        if (f.night) {
            // Keep the broad blue folds in shadow. Concentrate their bright silk sheen
            // in the turquoise pool between the last row of labels and the dock.
            val base = 0.14f + 0.17f * smooth((v - 0.12f) / 0.36f) - 0.17f * smooth((v - 0.84f) / 0.08f)
            w1 = fold * base * 0.75f + pool * (0.35f + 0.35f * fold)
            w2 = smooth((deep - 0.25f) / 0.7f) * (1f - fold * 0.4f) * (base * 0.8f + 0.08f)
            hot = sheen * (base * 0.28f + pool * 0.6f)
        } else {
            val base = 0.62f + 0.38f * smooth((v - 0.1f) / 0.4f)
            w1 = fold * base * 0.7f + pool * 0.35f
            w2 = smooth((deep - 0.25f) / 0.7f) * (1f - fold * 0.4f) * base * 0.75f
            hot = sheen * base * 0.25f
        }
    }

    fun release() {
        field.release()
        screen.release()
        fibres.release()
    }

    private companion object {
        /** Low tide's stream direction, degrees below horizontal. */
        const val ANGLE = 12f
        val COS = cos(ANGLE * PI.toFloat() / 180f)
        val SIN = sin(ANGLE * PI.toFloat() / 180f)
    }
}
