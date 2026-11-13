package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Gradient and glass motion rates are written for this pace of [Frame.t]: a field of light
 * should drift and breathe, never churn, even at the fastest speed setting.
 */
internal const val GRADIENT_PACE = 0.4f

/**
 * A low-resolution colour field computed on the CPU and stretched over the canvas with bilinear
 * filtering. Real domain warping, smearing and lensing need per-point maths that radial glows
 * can't express; at ~48 columns this costs well under a millisecond and one small texture upload.
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

    // Touch displacement registers, filled by [displace].
    var dx = 0f; private set
    var dy = 0f; private set
    /** Gaussian crest strength of every tap wavefront at the last displaced point. */
    var crest = 0f; private set
    /** Freshest nearby wake at the last displaced point, 0..1. */
    var wake = 0f; private set
    private val wakeX = FloatArray(TouchField.TRAIL)
    private val wakeY = FloatArray(TouchField.TRAIL)

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
        prepareWake(f)
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

    /** Unit direction of the wake at each trail point, so smears follow the swipe. */
    private fun prepareWake(f: Frame) {
        for (i in 0 until f.trail) {
            val a = (i - 1).coerceAtLeast(0)
            val b = (i + 1).coerceAtMost(f.trail - 1)
            val ex = f.trailX[b] - f.trailX[a]
            val ey = f.trailY[b] - f.trailY[a]
            val d = sqrt(ex * ex + ey * ey)
            if (d < 0.01f) { wakeX[i] = 0f; wakeY[i] = 0f } else { wakeX[i] = ex / d; wakeY[i] = ey / d }
        }
    }

    /**
     * How far the material at ([x], [y]) has been pushed, as a sampling offset ([dx], [dy]):
     * sample the field at (x + dx, y + dy).
     * - [smear]: the wake drags colour along the swipe, like ink stirred in water. It relaxes
     *   back as the wake fades.
     * - [lens]: a charged finger magnifies what is under it, as if a lens were focusing there.
     * - [ripple]: tap wavefronts refract the field (a derivative-of-gaussian push).
     */
    fun displace(f: Frame, x: Float, y: Float, smear: Float, lens: Float, ripple: Float) {
        var ox = 0f
        var oy = 0f
        var wakeAt = 0f
        if (f.trail > 1 && smear > 0f) {
            val radius = 130f
            val r2 = radius * radius
            var sx = 0f; var sy = 0f; var sw = 0f; var best = 0f
            for (i in 0 until f.trail) {
                val ex = x - f.trailX[i]
                val ey = y - f.trailY[i]
                val d2 = ex * ex + ey * ey
                if (d2 > r2 * 6f) continue
                val w = exp(-d2 / r2)
                val a = f.trailA[i]
                sx += wakeX[i] * w * a; sy += wakeY[i] * w * a; sw += w
                if (w * a > best) best = w * a
            }
            wakeAt = best
            if (sw > 0f) {
                val n = smear / sw.coerceAtLeast(1f)
                ox -= sx * n; oy -= sy * n
            }
        }
        if (lens > 0f && f.drag > 0.001f && f.charge > 0f) {
            val ex = x - f.dragX
            val ey = y - f.dragY
            val k = exp(-(ex * ex + ey * ey) / (230f * 230f)) * f.charge * f.drag * lens
            ox -= ex * k; oy -= ey * k
        }
        var wave = 0f
        if (f.pulses > 0) {
            for (i in 0 until f.pulses) {
                val ex = x - f.pulseX[i]
                val ey = y - f.pulseY[i]
                val d = sqrt(ex * ex + ey * ey).coerceAtLeast(1f)
                val q = (d - f.pulseR[i]) / 90f
                if (q < -3f || q > 3f) continue
                val g = exp(-q * q) * f.pulseA[i]
                wave += g
                if (ripple > 0f) { val push = q * g * ripple / d; ox += ex * push; oy += ey * push }
            }
        }
        dx = ox; dy = oy; crest = wave; wake = wakeAt
    }

    fun release() {
        bitmap?.recycle(); bitmap = null
        coverBitmap?.recycle(); coverBitmap = null
        shader = null
        cols = 0; rows = 0
    }

    private fun channel(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
}
