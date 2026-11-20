package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.abs
import kotlin.math.sin

/** A turbulent dust veil with blue light spilling through an irregular seam. */
internal class OrbitNebula(private val orbit: OrbitKit) {
    private val k = orbit.k
    private var dust: Bitmap? = null
    private var groundColor = 0
    private var coolColor = 0
    private var warmColor = 0

    fun draw(canvas: Canvas, f: Frame) {
        val t = f.t
        val energy = f.amount
        val centerX = 500f + f.shiftX(0.9f) + sin(t * 0.078f) * 33f * energy
        val centerY = f.h * 0.516f + f.shiftY(0.9f) + sin(t * 0.092f + 0.2f) * 26f * energy
        val halfHeight = minOf(f.h * 0.38f, 815f) * (1f + f.composition * 0.75f)
        val light = smooth(f.reveal) * (0.72f + 0.28f * energy)

        orbit.stars(canvas, f, 230, 0f, f.h, 0.15f)
        k.glow(canvas, k.glowA, centerX + 85f, centerY - halfHeight * 0.13f,
            560f, halfHeight * 0.67f, (32 * light).toInt(), RenderKit.Mode.SCREEN)

        val layer = dust?.takeIf { groundColor == f.c0 && coolColor == f.c1 && warmColor == f.c2 }
            ?: createDust(f).also { dust = it }
        k.rect.set(centerX - 630f, centerY - halfHeight, centerX + 630f, centerY + halfHeight)
        k.fill.alpha = (240f * (0.70f + light * 0.30f)).toInt()
        canvas.drawBitmap(layer, null, k.rect, k.fill)
        k.reset(k.fill)

        // These lights move at a different depth from the dust. Their intensity changes the glow
        // across the seam while the cloud silhouette remains coherent.
        val pulse = (0.76f + energy * sin(t * 0.66f + 1.2f) * 0.24f) * light
        val lightX = centerX + 172f + sin(t * 0.135f) * 105f * energy
        val lightY = centerY - halfHeight * 0.23f + sin(t * 0.104f) * halfHeight * 0.10f * energy
        k.glow(canvas, k.glowA, lightX, lightY, 240f, halfHeight * 0.30f, (136 * pulse).toInt(), RenderKit.Mode.SCREEN)
        k.glow(canvas, k.glowB, centerX - 114f, centerY + halfHeight * 0.13f,
            198f, halfHeight * 0.22f, (45 * light).toInt(), RenderKit.Mode.SCREEN)
        star(canvas, lightX + 22f, lightY - halfHeight * 0.105f, 34f, pulse)
        star(canvas, 224f + f.shiftX(0.2f), centerY - halfHeight * 0.41f, 22f,
            light * (0.60f + energy * sin(t * 0.74f) * 0.22f))
        star(canvas, 771f + f.shiftX(0.3f), centerY + halfHeight * 0.29f, 14f, 0.62f * light)
    }

    private fun star(canvas: Canvas, x: Float, y: Float, radius: Float, light: Float) {
        k.glow(canvas, k.glowA, x, y, radius, radius, (110 * light).toInt(), RenderKit.Mode.SCREEN)
        k.glow(canvas, k.glowWhite, x, y, radius * 0.82f, radius * 0.084f, (185 * light).toInt(), RenderKit.Mode.ADD)
        k.glow(canvas, k.glowWhite, x, y, radius * 0.065f, radius * 0.74f, (143 * light).toInt(), RenderKit.Mode.ADD)
        k.fill.color = Color.WHITE
        k.fill.alpha = (220 * light).toInt()
        canvas.drawCircle(x, y, radius * 0.07f, k.fill)
        k.reset(k.fill)
    }

    private fun createDust(f: Frame): Bitmap {
        groundColor = f.c0
        coolColor = f.c1
        warmColor = f.c2
        val width = 420
        val height = 500
        val pixels = IntArray(width * height)
        val noise = SpaceNoise(2719)
        val deepBlue = mix(f.c0, 0xFF3A5590.toInt(), 0.71f)
        val rimIce = mix(f.c1, Color.WHITE, 0.56f)
        val heatedDust = mix(f.c2, 0xFFD2A6B1.toInt(), 0.21f)
        for (row in 0 until height) {
            val y = 2f * row / (height - 1) - 1f
            for (column in 0 until width) {
                val x = 2f * column / (width - 1) - 1f
                val envelope = smooth((1f - x * x - y * y) * 1.85f)
                if (envelope <= 0f) continue
                val u = x * 3.4f + y * 2.2f
                val v = y * 4.6f - x * 1.3f
                val warpX = noise.fractal(u + 9f, v - 6f) - 0.5f
                val warpY = noise.fractal(u - 5f, v + 13f) - 0.5f
                val foldX = u + warpX * 4.5f + warpY * 2.5f
                val foldY = v + warpY * 7.8f + warpX * 1.6f
                val body = noise.fractal(foldX, foldY)
                val thread = noise.fractal(foldX * 1.9f - 11f, foldY * 2.05f + 6f)

                val seam = y + 0.39f * x + 0.26f * sin(x * 3.9f + 0.51f) + warpY * 0.72f
                val roughEdge = seam + (body - 0.52f) * 0.38f
                val haze = smooth(1f - abs(roughEdge + 0.09f) * 1.75f)
                val rim = smooth(1f - abs(roughEdge) * 8.6f)
                val foreground = smooth((roughEdge + 0.034f) * 5.7f)
                val density = smooth((body * 0.80f + thread * 0.20f - 0.19f) * 1.95f)
                val hotSpot = smooth(1f - abs(x - 0.19f) * 1.05f)
                val wash = haze * density * (1f - foreground * 0.78f)
                val edgeLight = rim * (0.29f + 0.71f * density) * hotSpot * (0.49f + thread * 0.76f)

                val shadow = mix(f.c0, deepBlue, 0.34f + density * 0.66f)
                val atmosphere = mix(shadow, f.c1, wash * (0.65f + 0.19f * hotSpot))
                val copper = smooth((roughEdge + 0.052f) * 13f) * (0.65f + 0.35f * noise.sample(u + 4f, v))
                val tint = mix(atmosphere, mix(rimIce, heatedDust, copper), edgeLight)
                val opacity = envelope * (0.12f + density * 0.60f + wash * 0.23f + rim * 0.05f)
                pixels[row * width + column] = alpha(tint, (opacity * 255f).toInt())
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    fun release() {
        dust = null
    }
}
