package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.withRotation
import kotlin.math.cos
import kotlin.math.sin

/** A polar strip of ink wrapped onto a rotating annulus. */
internal class OrbitBand(private val ink: Bitmap, inner: Float, outer: Float, private val speed: Float) {
    private val vertices = FloatArray((SEGMENTS + 1) * 4)

    init {
        val row = (SEGMENTS + 1) * 2
        for (i in 0..SEGMENTS) {
            val angle = (i % SEGMENTS) * (2.0 * Math.PI / SEGMENTS)
            val x = cos(angle).toFloat()
            val y = sin(angle).toFloat()
            vertices[i * 2] = inner * x
            vertices[i * 2 + 1] = inner * y
            vertices[row + i * 2] = outer * x
            vertices[row + i * 2 + 1] = outer * y
        }
    }

    fun draw(canvas: Canvas, time: Float, paint: Paint) {
        canvas.withRotation(time * speed) {
            drawBitmapMesh(ink, SEGMENTS, 1, vertices, 0, null, 0, paint)
        }
    }

    private companion object {
        const val SEGMENTS = 128
    }
}
