package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.ceil

/**
 * Gradient and glass motion rates are written for this pace of [Frame.t]: a field of light
 * should drift and breathe, never churn, even at the fastest speed setting.
 */
internal const val GRADIENT_PACE = 0.4f

/**
 * A low-resolution colour field computed on the CPU and stretched over the canvas with bilinear
 * filtering. The grid carries the domain-warped colour and the coverage used for silk and glass.
 *
 * Each cell also stores a 0..1 [cover] (how much colour is there), which the halftone screen
 * turns into dot size, and which scenes can [sample] for cheap lookups (caustics, fibres).
 *
 * Bitmaps are allocated once per size and reused every frame; [release] frees them.
 */
internal class GradientField {
    /** Writes the colour (0..1 channels) and coverage of one point. Implemented by scenes. */
    interface Source {
        fun shade(f: Frame, x: Float, y: Float, out: GradientField)
    }

    var cols = 0; private set
    var rows = 0; private set
    var cell = 1f; private set
    var bitmap: Bitmap? = null; private set
    var coverBitmap: Bitmap? = null; private set
    /** The colour field as a shader in artwork units, for strokes that carry local colour. */
    var shader: BitmapShader? = null; private set
    private var colors = IntArray(0)
    private var covers = IntArray(0)
    private var coverage = FloatArray(0)
    private val matrix = Matrix()
    private val rect = RectF()

    // Output registers for [Source.shade]; avoids allocating a colour per point.
    var r = 0f; var g = 0f; var b = 0f; var cover = 0f

    /** Size the field for this frame: [density] columns at full detail. */
    fun ensure(f: Frame, density: Int) {
        val n = (density * f.detail).toInt().coerceAtLeast(16)
        val size = 1000f / n
        val c = n + 1
        val r = ceil(f.h / size).toInt() + 1
        if (bitmap != null && c == cols && r == rows) return
        release()
        cols = c; rows = r; cell = size
        colors = IntArray(c * r)
        covers = IntArray(c * r)
        coverage = FloatArray(c * r)
        val color = Bitmap.createBitmap(c, r, Bitmap.Config.ARGB_8888)
        bitmap = color
        coverBitmap = Bitmap.createBitmap(c, r, Bitmap.Config.ARGB_8888)
        // Texel centres land on the cell grid: texel (i, j) is drawn at (i * cell, j * cell).
        matrix.setScale(cell, cell)
        matrix.postTranslate(-cell / 2f, -cell / 2f)
        shader = BitmapShader(color, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
    }

    /** Evaluate [source] at every cell centre and upload both bitmaps. */
    fun fill(f: Frame, source: Source) {
        var index = 0
        for (j in 0 until rows) {
            val y = j * cell
            for (i in 0 until cols) {
                val x = i * cell
                source.shade(f, x, y, this)
                colors[index] = 0xFF000000.toInt() or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
                val c = cover.coerceIn(0f, 1f)
                coverage[index] = c
                // Half range: the halftone adds its own half-range spot function on top.
                val grey = (c * 127.5f).toInt()
                covers[index] = 0xFF000000.toInt() or (grey shl 16) or (grey shl 8) or grey
                index++
            }
        }
        bitmap?.setPixels(colors, 0, cols, 0, 0, cols, rows)
        coverBitmap?.setPixels(covers, 0, cols, 0, 0, cols, rows)
    }

    /** Bilinear coverage at an artwork point. */
    fun sample(x: Float, y: Float): Float {
        if (cols == 0) return 0f
        val fx = (x / cell).coerceIn(0f, cols - 1.001f)
        val fy = (y / cell).coerceIn(0f, rows - 1.001f)
        val i = fx.toInt(); val j = fy.toInt()
        val u = fx - i; val v = fy - j
        val o = j * cols + i
        val top = coverage[o] + (coverage[o + 1] - coverage[o]) * u
        val bottom = coverage[o + cols] + (coverage[o + cols + 1] - coverage[o + cols]) * u
        return top + (bottom - top) * v
    }

    /** Nearest cell colour at an artwork point (opaque ARGB). */
    fun color(x: Float, y: Float): Int {
        if (cols == 0) return 0
        val i = (x / cell + 0.5f).toInt().coerceIn(0, cols - 1)
        val j = (y / cell + 0.5f).toInt().coerceIn(0, rows - 1)
        return colors[j * cols + i]
    }

    /** Stretch the colour (or coverage) field over the artwork. */
    fun draw(canvas: Canvas, f: Frame, paint: Paint, cover: Boolean = false) {
        val bmp = (if (cover) coverBitmap else bitmap) ?: return
        val half = cell / 2f
        rect.set(-half, -half, cols * cell - half, rows * cell - half)
        canvas.drawBitmap(bmp, null, rect, paint)
    }

    /**
     * Fill a rect with the colour field through [paint], clamping beyond the edges, for
     * transformed copies (refraction) that look past the artwork's borders.
     */
    fun drawClamped(canvas: Canvas, paint: Paint, left: Float, top: Float, right: Float, bottom: Float) {
        paint.shader = shader ?: return
        canvas.drawRect(left, top, right, bottom, paint)
        paint.shader = null
    }

    fun release() {
        bitmap?.recycle(); bitmap = null
        coverBitmap?.recycle(); coverBitmap = null
        shader = null
        cols = 0; rows = 0
    }

    private fun channel(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
}
