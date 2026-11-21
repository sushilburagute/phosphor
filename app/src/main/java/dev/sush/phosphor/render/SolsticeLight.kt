package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withSave
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

/** Two depths of haze scatter light from the same solar disc. Changing the gaps between
 * the illuminated volumes leaves soft spokes of unlit sky between them. */
internal class SolsticeLight(private val kit: RenderKit) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bounds = RectF()
    private var distant: Bitmap? = null
    private var nearby: Bitmap? = null
    private var disc: Shader? = null
    private var rose = 0
    private var amber = 0

    fun draw(canvas: Canvas, f: Frame, x: Float) {
        if (distant == null || rose != f.c1 || amber != f.c2) prepare(f.c1, f.c2)
        val radius = minOf(74f, f.h * 0.047f)
        val y = f.h * 0.68f + f.shiftY(0.4f) - radius * (0.75f - 0.32f * sin(f.t * 0.19f) * f.amount)
        val reveal = smooth(f.reveal)
        val span = f.h * 0.63f
        val wind = sin(f.t * 0.14f) * f.amount
        val passing = sin(f.t * 0.23f + 1.1f) * f.amount
        val mode = kit.modeFor(f)
        kit.glow(canvas, kit.glowB, x, y, 400f, minOf(270f, span * 0.36f), (54f * reveal).toInt(), mode)
        canvas.withSave {
            translate(x, y)
            scale(1f + 0.94f * f.composition, 1f)
            bounds.set(-span, -span, span, 0f)
            kit.luminous(ink, f.night)
            withSave {
                rotate(-1.8f + 4.1f * wind)
                ink.alpha = ((198f + 29f * passing) * reveal).toInt()
                drawBitmap(checkNotNull(distant), null, bounds, ink)
            }
            withSave {
                rotate(1.5f + 2.4f * wind - 3.9f * passing)
                ink.alpha = ((209f - 34f * passing) * reveal).toInt()
                drawBitmap(checkNotNull(nearby), null, bounds, ink)
            }
        }
        kit.normal(ink)
        kit.glow(canvas, kit.glowB, x, y, radius * 2.3f, radius * 1.8f, (74f * reveal).toInt(), mode)
        canvas.withSave {
            translate(x, y)
            scale(radius, radius)
            ink.shader = disc
            ink.alpha = (246f * reveal).toInt()
            drawCircle(0f, 0f, 1f, ink)
        }
        kit.reset(ink)
    }

    private fun prepare(first: Int, second: Int) {
        rose = first
        amber = second
        val noise = SpaceNoise(1903)
        val far = IntArray(WIDTH * HEIGHT)
        val near = IntArray(far.size)
        val whiteGold = mix(amber, Color.WHITE, 0.55f)
        val outerGlow = mix(amber, rose, 0.60f)
        val innerGlow = mix(amber, rose, 0.30f)
        for (row in 0 until HEIGHT) {
            val v = 1f - (row + 0.5f) / HEIGHT
            for (column in 0 until WIDTH) {
                val u = 2f * (column + 0.5f) / WIDTH - 1f
                val distance = sqrt(u * u + v * v)
                if (distance >= 1f) continue
                // An extended light source softens the edges near the disc. Particulate density
                // varies through each shaft, and the farther veil scatters light higher up.
                val angle = atan2(u, v + 0.023f)
                val mist = noise.fractal(4.7f * u + 1.1f * v, 7.0f * v - 0.8f * u)
                val density = 0.48f + mist * 0.90f
                // The ridge already has a bright horizon wash. Let the distinct shafts
                // emerge beyond that haze, keeping the nearby sky and its labels readable.
                val fade = (0.35f + 0.65f * smooth(distance / 0.29f)) *
                    (1f - smooth((distance - 0.08f) / 0.92f))
                val warm = beam(angle, -0.48f, 0.23f) + 0.48f * beam(angle, -0.17f, 0.076f) +
                    0.73f * beam(angle, 0.49f, 0.18f)
                val cool = 0.76f * beam(angle, -0.89f, 0.23f) + 0.72f * beam(angle, 0.055f, 0.18f) +
                    0.48f * beam(angle, 0.78f, 0.12f)
                val heightTint = smooth(distance * 1.55f)
                val i = row * WIDTH + column
                far[i] = alpha(mix(whiteGold, outerGlow, heightTint),
                    (155f * cool * fade * density).toInt())
                near[i] = alpha(mix(whiteGold, innerGlow, heightTint),
                    (178f * warm * fade * (0.64f + 0.55f * mist)).toInt())
            }
        }
        distant = Bitmap.createBitmap(far, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        nearby = Bitmap.createBitmap(near, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        disc = RadialGradient(-0.14f, -0.28f, 1.38f,
            intArrayOf(mix(whiteGold, Color.WHITE, 0.38f), whiteGold, mix(amber, rose, 0.22f)),
            floatArrayOf(0f, 0.70f, 1f), Shader.TileMode.CLAMP)
    }

    private fun beam(angle: Float, center: Float, width: Float): Float =
        smooth(1f - abs(angle - center) / width)

    fun release() {
        distant = null
        nearby = null
        disc = null
    }

    private companion object {
        const val WIDTH = 432
        const val HEIGHT = 290
    }
}
