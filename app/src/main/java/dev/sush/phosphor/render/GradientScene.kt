package dev.sush.phosphor.render

import android.graphics.Canvas
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import kotlin.math.cos
import kotlin.math.sin

/** Mesh-like gradient blobs that you can stir with a finger, under a halftone screen. */
internal class GradientScene(private val k: RenderKit, private val halftone: Halftone) {
    private val stir = Spring(4)

    fun draw(canvas: Canvas, f: Frame) {
        val h = f.h
        val t = f.t
        val a = f.amount
        val mode = k.modeFor(f)
        // Base positions follow slow Lissajous paths.
        val bx = floatArrayOf(250f + sin(t * 0.7f) * 220f * a, 800f + cos(t * 0.45f) * 190f * a, 80f + sin(t * 0.35f) * 180f * a, 520f + sin(t * 0.5f) * 170f * a)
        val by = floatArrayOf(h * 0.34f + cos(t * 0.63f) * h * 0.1f * a, h * 0.74f + sin(t * 0.6f) * h * 0.1f * a, h * 0.02f, h * 0.55f + cos(t * 0.4f) * h * 0.08f * a)
        for (i in 0 until 4) {
            val influence = f.dragInfluence(bx[i] + stir.x[i], by[i] + stir.y[i], 520f)
            stir.step(i, (f.dragX - bx[i]) * influence * 0.8f, (f.dragY - by[i]) * influence * 0.8f, f.dt)
        }
        if (!f.busy && f.dt == 0f) stir.reset()
        k.glow(canvas, k.glowA, bx[0] + stir.x[0], by[0] + stir.y[0], 1050f, h * 0.48f, if (f.night) 165 else 210, mode)
        k.glow(canvas, k.glowB, bx[1] + stir.x[1], by[1] + stir.y[1], 1000f, h * 0.44f, if (f.night) 165 else 200, mode)
        k.glow(canvas, k.glowB, bx[2] + stir.x[2], by[2] + stir.y[2], 900f, h * 0.36f, if (f.night) 150 else 150, mode)
        k.glow(canvas, k.glowWhite, bx[3] + stir.x[3], by[3] + stir.y[3], 640f, h * 0.2f, if (f.night) 18 else 90, if (f.night) RenderKit.Mode.SCREEN else RenderKit.Mode.NORMAL)

        // The halftone screen: denser where the color is, like print.
        halftone.ensure(k.fill)
        val p = k.fill
        p.shader = halftone.shader
        p.alpha = if (f.night) 110 else 36
        if (!f.night) k.blend(p, RenderKit.Mode.INK)
        canvas.drawRect(0f, 0f, 1000f, h, p)
        k.reset(p)

        ripples(canvas, f, k)
    }

    fun release() = stir.reset()
}
