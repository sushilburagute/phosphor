package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos

/**
 * An amplitude-modulated print screen: dot (or line) size follows the field's coverage, like a
 * real halftone, at a constant cost however fine the screen is.
 *
 * Inside one layer the field's coverage (half range) is added to a half-range spot function;
 * the layer's colour matrix then thresholds the sum at one half with a steep ramp. Where the
 * sum is below the threshold we are between dots, and the layer paints the [gap] colour there.
 * So dots keep the field's colour and the gaps fall back to the ground (night) or paper (day).
 */
internal class GradientScreen {
    private var dotTile: Bitmap? = null
    private var lineTile: Bitmap? = null
    private var dotShader: BitmapShader? = null
    private var lineShader: BitmapShader? = null
    private val layer = Paint()
    private val cover = Paint(Paint.FILTER_BITMAP_FLAG)
    private val screen = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val matrix = Matrix()
    private var gapKey = 0

    private fun ensure() {
        if (dotTile != null) return
        // The Euclidean spot: round dots in light areas, a checkerboard at 50%, round holes in
        // dark areas. Two dots per tile give the classic 45-degree screen.
        dotTile = tile { x, y -> 0.5f + 0.25f * (cos(2f * PI.toFloat() * (x + y)) + cos(2f * PI.toFloat() * (x - y))) }
        lineTile = tile { x, _ -> 0.5f + 0.5f * cos(2f * PI.toFloat() * x) }
        dotShader = BitmapShader(dotTile!!, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        lineShader = BitmapShader(lineTile!!, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private inline fun tile(spot: (Float, Float) -> Float): Bitmap {
        val pixels = IntArray(TILE * TILE)
        for (j in 0 until TILE) for (i in 0 until TILE) {
            val v = (spot((i + 0.5f) / TILE, (j + 0.5f) / TILE).coerceIn(0f, 1f) * 127.5f).toInt()
            pixels[j * TILE + i] = Color.rgb(v, v, v)
        }
        return Bitmap.createBitmap(pixels, TILE, TILE, Bitmap.Config.ARGB_8888)
    }

    /**
     * Print [field] through the screen. [pitch] is the dot spacing in artwork units, [angle]
     * the screen angle in degrees, [strength] 0..1 how fully the gaps return to [gap].
     */
    fun draw(canvas: Canvas, f: Frame, field: GradientField, gap: Int, strength: Float, pitch: Float, angle: Float, lines: Boolean) {
        ensure()
        if (strength <= 0f || field.cols == 0) return
        if (gap != gapKey || layer.colorFilter == null) {
            gapKey = gap
            // Alpha = GAIN * (0.5 - sum): opaque gap colour between dots, clear on the dots.
            layer.colorFilter = ColorMatrixColorFilter(floatArrayOf(
                0f, 0f, 0f, 0f, Color.red(gap).toFloat(),
                0f, 0f, 0f, 0f, Color.green(gap).toFloat(),
                0f, 0f, 0f, 0f, Color.blue(gap).toFloat(),
                -GAIN, 0f, 0f, 0f, GAIN * 127.5f,
            ))
        }
        layer.alpha = (strength * 255f).toInt().coerceIn(0, 255)
        val shader = if (lines) lineShader else dotShader
        matrix.setScale(pitch / TILE, pitch / TILE)
        matrix.postRotate(angle)
        shader?.setLocalMatrix(matrix)
        screen.shader = shader
        val saved = canvas.saveLayer(0f, 0f, 1000f, f.h, layer)
        field.draw(canvas, f, cover, cover = true)
        canvas.drawRect(0f, 0f, 1000f, f.h, screen)
        canvas.restoreToCount(saved)
    }

    fun release() {
        dotTile?.recycle(); dotTile = null
        lineTile?.recycle(); lineTile = null
        dotShader = null; lineShader = null
        screen.shader = null
        layer.colorFilter = null
        gapKey = 0
    }

    private companion object {
        const val TILE = 32
        /** Ramp steepness of the threshold; ~1 px of anti-aliasing at phone pitch. */
        const val GAIN = 9f
    }
}
