package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Low tide's silk: a cached sheet of fine brushed streaks, bent through a mesh that follows the
 * folds of the colour field, so thousands of fibres flow and curve for the cost of two draws.
 * Mesh vertices take the field's own colour, so each fibre carries the colour beneath it, and
 * they move with the same smear and lens as the field, so a swipe visibly combs the silk.
 */
internal class GradientSilk {
    private var streaks: Bitmap? = null
    private val verts = FloatArray((MESH_A + 1) * (MESH_C + 1) * 2)
    private val light = IntArray((MESH_A + 1) * (MESH_C + 1))
    private val shade = IntArray((MESH_A + 1) * (MESH_C + 1))
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** Cross-stream offset (artwork units / 1000) of the fold through ([along], [across]). */
    fun warp(along: Float, across: Float, t: Float) =
        0.16f * sin(along * 2.3f + t * 0.21f) + 0.07f * sin(along * 5.1f - t * 0.33f + 1.7f) + 0.04f * sin(across * 3.1f + t * 0.1f)

    private fun ensure() {
        if (streaks != null) return
        val pixels = IntArray(TEX_A * TEX_C)
        for (j in 0 until TEX_C) {
            // Fibres cluster into bundles: coarse and fine noise across the stream, plus a
            // per-fibre flicker. Along the stream each fibre fades in and out in segments.
            val n = 0.4f * noise(j / 37f, 3) + 0.3f * noise(j / 5.5f, 11) + 0.3f * fraction(j * 31 + 7)
            val row = 0.15f + 0.85f * smooth((n - 0.2f) / 0.6f)
            val offset = fraction(j / 2 * 17 + 5) * 40f
            val span = 9f + fraction(j * 3 + 1) * 16f
            for (i in 0 until TEX_A) {
                val m = noise(i / span + offset, j / 2 + 101)
                val a = row * smooth((m - 0.15f) / 0.55f)
                pixels[j * TEX_A + i] = Color.argb((a * a * 235f).toInt(), 255, 255, 255)
            }
        }
        streaks = Bitmap.createBitmap(pixels, TEX_A, TEX_C, Bitmap.Config.ARGB_8888)
    }

    /** Smooth 1D value noise, 0..1. */
    private fun noise(x: Float, seed: Int): Float {
        val i = floor(x).toInt()
        val u = smooth(x - i)
        val a = fraction(i * 157 + seed * 7919)
        val b = fraction((i + 1) * 157 + seed * 7919)
        return a + (b - a) * u
    }

    fun draw(canvas: Canvas, f: Frame, k: RenderKit, field: GradientField, angle: Float) {
        ensure()
        val bitmap = streaks ?: return
        val rad = angle * PI.toFloat() / 180f
        val ca = cos(rad); val sa = sin(rad)
        val t = f.t * GRADIENT_PACE
        // Cover the screen in the stream's frame, with room for the folds' sway.
        val a0 = -150f; val a1 = 1000f * ca + f.h * sa + 150f
        val c0 = -1000f * sa - 300f; val c1 = f.h * ca + 300f
        var v = 0
        var n = 0
        val reveal = smooth(f.reveal)
        for (r in 0..MESH_C) {
            val cp = c0 + (c1 - c0) * r / MESH_C
            for (q in 0..MESH_A) {
                val a = a0 + (a1 - a0) * q / MESH_A
                val c = cp - warp(a / 1000f, cp / 1000f, t) * 1000f
                val x = a * ca - c * sa
                val y = a * sa + c * ca
                // Match the field's sampling offset, so fibres and colour smear together.
                field.displace(f, x, y, smear = 105f, lens = 0.6f, ripple = 38f)
                val vx = x - field.dx - f.shiftX(0.8f)
                val vy = y - field.dy - f.shiftY(0.5f)
                verts[v++] = vx; verts[v++] = vy
                val cover = field.sample(vx, vy) * reveal
                val base = field.color(vx, vy)
                if (f.night) {
                    light[n] = RenderKit.alpha(RenderKit.mix(base, Color.WHITE, 0.4f), (10f + cover * 170f).toInt())
                    shade[n] = RenderKit.alpha(Color.BLACK, (30f + cover * 90f).toInt())
                } else {
                    light[n] = RenderKit.alpha(Color.WHITE, (90f + (1f - cover) * 60f).toInt())
                    shade[n] = RenderKit.alpha(RenderKit.mix(base, Color.BLACK, 0.1f), (30f + cover * 120f).toInt())
                }
                n++
            }
        }
        paint.alpha = 255
        // Shadowed fibres first, shifted a few units across so they interleave with the lit ones.
        val saved = canvas.save()
        canvas.translate(-sa * 2.5f, ca * 2.5f)
        k.blend(paint, if (f.night) RenderKit.Mode.NORMAL else RenderKit.Mode.INK)
        canvas.drawBitmapMesh(bitmap, MESH_A, MESH_C, verts, 0, shade, 0, paint)
        canvas.restoreToCount(saved)
        k.blend(paint, if (f.night) RenderKit.Mode.SCREEN else RenderKit.Mode.NORMAL)
        canvas.drawBitmapMesh(bitmap, MESH_A, MESH_C, verts, 0, light, 0, paint)
        k.normal(paint)
    }

    fun release() { streaks?.recycle(); streaks = null }

    private companion object {
        const val TEX_A = 128
        const val TEX_C = 1024
        const val MESH_A = 32
        const val MESH_C = 80
    }
}
