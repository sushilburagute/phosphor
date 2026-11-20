package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader

/** Shared halftone screen: a dot tile at 45 degrees, tinted per mood. */
internal class Halftone {
    private var tile: Bitmap? = null
    var shader: Shader? = null; private set

    fun ensure() {
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

/** Soft light at night, overprinted ink by day. */
internal fun RenderKit.modeFor(f: Frame) = if (f.night) RenderKit.Mode.SCREEN else RenderKit.Mode.INK
