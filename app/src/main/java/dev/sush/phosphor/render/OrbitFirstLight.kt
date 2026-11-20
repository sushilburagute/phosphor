package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.SweepGradient
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** An orbital sunrise. A dark Earth cuts a bite out of the sun and its light scatters along the limb. */
internal class OrbitFirstLight(private val orbit: OrbitKit) {
    private val k = orbit.k
    private val cloudVertices = FloatArray((MESH_COLUMNS + 1) * (MESH_ROWS + 1) * 2)
    private var clouds: Bitmap? = null
    private var atmosphere: SweepGradient? = null
    private var coolColor = 0
    private var warmColor = 0

    fun draw(canvas: Canvas, f: Frame) {
        val radius = 1460f
        val limb = f.h * 0.532f
        val earthX = 770f + f.shiftX(0.45f)
        val earthY = limb + radius + f.shiftY(0.55f)
        val angle = -94.8f + 3.8f * sin(f.t * 0.092f - 0.38f) * f.amount
        val radians = angle * (Math.PI.toFloat() / 180f)
        val sunX = earthX + (radius + 5f) * cos(radians)
        val sunY = earthY + (radius + 5f) * sin(radians)
        val light = (0.75f + 0.25f * smooth(f.reveal)) * (0.86f + 0.14f * f.amount)

        orbit.stars(canvas, f, 140, 0f, limb + 150f, 0.16f)
        k.glow(canvas, k.glowA, sunX, sunY, 690f, 275f, (61 * light).toInt(), RenderKit.Mode.SCREEN)
        k.glow(canvas, k.glowB, sunX, sunY, 280f, 104f, (127 * light).toInt(), RenderKit.Mode.SCREEN)

        val flare = canvas.save()
        canvas.rotate(angle + 90f, sunX, sunY)
        k.glow(canvas, k.glowB, sunX, sunY, 345f, 21f, (146 * light).toInt(), RenderKit.Mode.ADD)
        k.glow(canvas, k.glowWhite, sunX, sunY, 171f, 5f, (150 * light).toInt(), RenderKit.Mode.ADD)
        k.glow(canvas, k.glowWhite, sunX, sunY - 5f, 95f, 64f, (144 * light).toInt(), RenderKit.Mode.SCREEN)
        k.glow(canvas, k.glowB, sunX, sunY - 12f, 45f, 122f, (69 * light).toInt(), RenderKit.Mode.SCREEN)
        k.glow(canvas, k.glowWhite, sunX, sunY - 10f, 17f, 108f, (96 * light).toInt(), RenderKit.Mode.SCREEN)
        k.fill.color = mix(f.c2, Color.WHITE, 0.88f)
        canvas.drawCircle(sunX, sunY, 14f, k.fill)
        canvas.restoreToCount(flare)

        val planet = canvas.save()
        k.path.rewind()
        k.path.addCircle(earthX, earthY, radius, Path.Direction.CW)
        canvas.clipPath(k.path)
        canvas.drawColor(mix(f.c0, Color.BLACK, 0.45f))
        k.glow(canvas, k.glowA, sunX + 5f, sunY + 30f, 850f, 280f, (75 * light).toInt(), RenderKit.Mode.SCREEN)
        drawClouds(canvas, f, earthX, earthY, radius)
        k.glow(canvas, k.glowB, sunX, sunY + 15f, 225f, 62f, (74 * light).toInt(), RenderKit.Mode.SCREEN)
        canvas.restoreToCount(planet)

        drawAtmosphere(canvas, f, earthX, earthY, radius, angle, light)
    }

    private fun drawAtmosphere(
        canvas: Canvas, f: Frame, x: Float, y: Float, radius: Float, angle: Float, light: Float,
    ) {
        if (atmosphere == null || coolColor != f.c1 || warmColor != f.c2) {
            coolColor = f.c1
            warmColor = f.c2
            val peak = mix(f.c2, Color.WHITE, 0.79f)
            val gold = mix(f.c2, Color.WHITE, 0.28f)
            atmosphere = SweepGradient(
                0f, 0f,
                intArrayOf(peak, gold, f.c1, alpha(f.c1, 145), alpha(f.c1, 0),
                    alpha(f.c1, 0), alpha(f.c1, 153), f.c1, gold, peak),
                floatArrayOf(0f, 0.014f, 0.058f, 0.115f, 0.193f, 0.81f, 0.88f, 0.936f, 0.984f, 1f),
            )
        }
        val saved = canvas.save()
        canvas.translate(x, y)
        canvas.rotate(angle)
        val stroke = k.stroke
        stroke.shader = atmosphere
        k.blend(stroke, RenderKit.Mode.SCREEN)
        stroke.alpha = (27 * light).toInt()
        stroke.strokeWidth = 35f
        canvas.drawCircle(0f, 0f, radius + 17f, stroke)
        stroke.alpha = (59 * light).toInt()
        stroke.strokeWidth = 13f
        canvas.drawCircle(0f, 0f, radius + 8f, stroke)
        stroke.alpha = (208 * light).toInt()
        stroke.strokeWidth = 3.4f
        canvas.drawCircle(0f, 0f, radius + 2f, stroke)
        k.reset(stroke)
        canvas.restoreToCount(saved)
    }

    private fun drawClouds(canvas: Canvas, f: Frame, earthX: Float, earthY: Float, radius: Float) {
        val bitmap = clouds ?: createClouds().also { clouds = it }
        val height = minOf(415f, f.h * 0.41f)
        val drift = 70f * sin(f.t * 0.042f) * f.amount + f.shiftX(0.55f)
        var vertex = 0
        for (row in 0..MESH_ROWS) {
            val depth = row.toFloat() / MESH_ROWS * height
            for (column in 0..MESH_COLUMNS) {
                val x = -155f + column.toFloat() / MESH_COLUMNS * 1310f + drift
                val dx = x - earthX
                val edge = earthY - sqrt((radius * radius - dx * dx).coerceAtLeast(0f))
                cloudVertices[vertex++] = x
                cloudVertices[vertex++] = edge + depth
            }
        }
        k.fill.alpha = (170 + 66 * f.amount).toInt()
        canvas.drawBitmapMesh(bitmap, MESH_COLUMNS, MESH_ROWS, cloudVertices, 0, null, 0, k.fill)
        k.reset(k.fill)
    }

    private fun createClouds(): Bitmap {
        val noise = SpaceNoise(819)
        val width = 416
        val height = 132
        val pixels = IntArray(width * height)
        for (row in 0 until height) {
            val v = row.toFloat() / (height - 1)
            val fade = smooth(v * 16f) * (1f - v) * (1f - v)
            for (column in 0 until width) {
                val u = column.toFloat() / (width - 1)
                val x = u * 6.6f
                val y = v * 8.4f + u * 2.2f
                val curl = noise.sample(x * 0.62f + 9f, y * 0.48f)
                val density = noise.fractal(x + curl * 2.3f, y + curl * 6.4f)
                val wisps = smooth((density - 0.42f) * 2.5f)
                val side = smooth(minOf(u, 1f - u) * 7f) * (0.28f + 0.72f * u)
                val tint = mix(0xFF2D63A0.toInt(), 0xFFB4D8F4.toInt(), wisps * 0.72f)
                pixels[row * width + column] = alpha(tint, (185f * wisps * fade * side).toInt())
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    fun release() {
        clouds = null
        atmosphere = null
    }

    private companion object {
        const val MESH_COLUMNS = 32
        const val MESH_ROWS = 6
    }
}
