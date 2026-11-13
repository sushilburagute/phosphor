package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import dev.sush.phosphor.render.RenderKit.Companion.alpha

/** A small spring that eases a value toward a target; deterministic when left alone. */
internal class Spring(count: Int) {
    val x = FloatArray(count)
    val y = FloatArray(count)
    private val vx = FloatArray(count)
    private val vy = FloatArray(count)

    fun step(i: Int, tx: Float, ty: Float, dt: Float, stiffness: Float = 18f, damping: Float = 6.5f) {
        if (dt <= 0f) return
        val ax = (tx - x[i]) * stiffness - vx[i] * damping
        val ay = (ty - y[i]) * stiffness - vy[i] * damping
        vx[i] += ax * dt; vy[i] += ay * dt
        x[i] += vx[i] * dt; y[i] += vy[i] * dt
    }

    fun reset() { x.fill(0f); y.fill(0f); vx.fill(0f); vy.fill(0f) }
}

/** Shared halftone screen: a dot tile at 45 degrees, tinted per mood. */
internal class Halftone {
    private var tile: Bitmap? = null
    var shader: Shader? = null; private set

    fun ensure(paint: Paint) {
        if (tile != null) return
        val size = 12
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        c.drawCircle(3f, 3f, 1.9f, p)
        c.drawCircle(9f, 9f, 1.9f, p)
        tile = bitmap
        shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(1.1f, 1.1f) })
        }
    }

    fun release() { tile?.recycle(); tile = null; shader = null }
}

/** Concentric water-drop rings for each pulse: light crest followed by a shadow trough. */
internal fun ripples(canvas: Canvas, f: Frame, k: RenderKit) {
    if (f.pulses == 0) return
    val s = k.stroke
    for (i in 0 until f.pulses) {
        for (ring in 0 until 3) {
            val r = f.pulseR[i] * (1f - ring * 0.18f)
            if (r < 4f) continue
            val a = f.pulseA[i] * (1f - ring * 0.3f)
            k.luminous(s, f.night)
            s.color = if (f.night) Color.WHITE else f.c1
            s.alpha = (a * (if (f.night) 70f else 60f)).toInt()
            s.strokeWidth = 10f - ring * 2f
            canvas.drawCircle(f.pulseX[i], f.pulseY[i], r, s)
            k.normal(s)
            s.color = if (f.night) Color.BLACK else Color.WHITE
            s.alpha = (a * 40f).toInt()
            s.strokeWidth = 6f
            canvas.drawCircle(f.pulseX[i], f.pulseY[i], r - 12f, s)
        }
    }
    k.reset(s)
}

/** Soft light at night, overprinted ink by day. */
internal fun RenderKit.modeFor(f: Frame) = if (f.night) RenderKit.Mode.SCREEN else RenderKit.Mode.INK
