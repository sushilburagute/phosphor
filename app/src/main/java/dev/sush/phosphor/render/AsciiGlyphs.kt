package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import dev.sush.phosphor.render.RenderKit.Companion.fraction

/**
 * Glyphs pre-rendered once into an alpha-only atlas. An ALPHA_8 bitmap is tinted by the
 * paint's color, so each glyph is one `drawBitmap` from a shared texture (no text shaping, and
 * the GPU batches draws that share it) and color changes cost nothing.
 *
 * Also builds a tile of dim, randomly chosen glyphs: the unlit "screen" behind the art, drawn
 * as a single shader-filled rectangle.
 */
internal class AsciiGlyphs {
    private var atlas: Bitmap? = null
    private var tile: Bitmap? = null
    private val src = Array(CHARS.length) { Rect() }
    private val dst = RectF()
    private val matrix = Matrix()
    var lattice: Shader? = null; private set

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val latticePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun ensure() {
        if (atlas != null) return
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = PX_H * 0.86f
            color = Color.WHITE
        }
        val cols = 16
        val rows = (CHARS.length + cols - 1) / cols
        val full = Bitmap.createBitmap(cols * PX_W, rows * PX_H, Bitmap.Config.ARGB_8888)
        val c = Canvas(full)
        for (i in CHARS.indices) {
            val x = (i % cols) * PX_W
            val y = (i / cols) * PX_H
            src[i].set(x, y, x + PX_W, y + PX_H)
            c.drawText(CHARS, i, i + 1, x + PX_W * 0.5f, y + PX_H * 0.5f + text.textSize * 0.36f, text)
        }
        atlas = full.extractAlpha()
        full.recycle()

        // A 24 x 24 cell tile of sparse, dim glyphs.
        val tc = 24
        val t = Bitmap.createBitmap(tc * PX_W, tc * PX_H, Bitmap.Config.ARGB_8888)
        val tcv = Canvas(t)
        for (r in 0 until tc) for (q in 0 until tc) {
            val v = fraction(r * 131 + q * 17 + 5)
            if (v < 0.35f) continue
            val g = DIM[(fraction(r * 37 + q * 59 + 1) * DIM.size).toInt().coerceAtMost(DIM.size - 1)]
            text.alpha = (90 + 165 * fraction(r * 7 + q * 3 + 11)).toInt()
            tcv.drawText(CHARS, g, g + 1, q * PX_W + PX_W * 0.5f, r * PX_H + PX_H * 0.5f + text.textSize * 0.36f, text)
        }
        tile = t.extractAlpha()
        t.recycle()
        lattice = BitmapShader(tile!!, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    /** Draw glyph [index] filling a [w] x [h] cell centred on ([x], [y]), tinted by [paint]'s color. */
    fun draw(canvas: Canvas, index: Int, x: Float, y: Float, w: Float, h: Float) {
        dst.set(x - w * 0.5f, y - h * 0.5f, x + w * 0.5f, y + h * 0.5f)
        canvas.drawBitmap(atlas!!, src[index], dst, paint)
    }

    /** Fill a rectangle with the dim lattice, its cells [w] x [h] units, aligned to ([ox], [oy]). */
    fun lattice(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, w: Float, h: Float, ox: Float, oy: Float, color: Int, mode: RenderKit.Mode, k: RenderKit) {
        matrix.setScale(w / PX_W, h / PX_H)
        matrix.postTranslate(ox - w * 0.5f, oy - h * 0.5f)
        lattice!!.setLocalMatrix(matrix)
        latticePaint.shader = lattice
        latticePaint.color = color
        k.blend(latticePaint, mode)
        canvas.drawRect(left, top, right, bottom, latticePaint)
        latticePaint.shader = null
        k.normal(latticePaint)
    }

    fun release() {
        atlas?.recycle(); atlas = null
        tile?.recycle(); tile = null
        lattice = null
    }

    companion object {
        /** Atlas cell in pixels: large enough to stay crisp when scaled onto a 1440p screen. */
        const val PX_W = 30
        const val PX_H = 38

        /** Tone ramp first (dim to dense), then slope strokes, then scramble noise. */
        const val CHARS = ".:-=+*#%@" + "/\\|_" + "<>{}[]$&?17~^;!0"
        const val RAMP = 9
        const val SLASH = 9
        const val BACKSLASH = 10
        const val BAR = 11
        const val UNDERSCORE = 12
        const val NOISE = 13
        val NOISE_COUNT = CHARS.length - NOISE
        private val DIM = intArrayOf(0, 0, 0, 1, 1, 2, 4)
    }
}
