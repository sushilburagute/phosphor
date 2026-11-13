package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build

/** Hardware canvases before API 29 skip drawVertices; those devices get flat-shaded cells. */
internal fun Canvas.supportsVertices() = Build.VERSION.SDK_INT >= 29 || !isHardwareAccelerated

/**
 * A regular grid of vertices over the artwork, each with its own color, drawn as one
 * Gouraud-shaded triangle mesh. Scenes compute lighting per vertex; the GPU blends between.
 */
internal class ShadedGrid {
    var cols = 0; private set
    var rows = 0; private set
    var verts = FloatArray(0); private set
    var colors = IntArray(0); private set
    /** A second color per vertex, for an overlay pass such as a halftone screen. */
    var overlay = IntArray(0); private set
    private var indices = ShortArray(0)
    private var width = 0f
    private var height = 0f

    /** Lay out [cols] x [rows] cells covering [width] x [height] plus a margin; true if rebuilt. */
    fun ensure(cols: Int, rows: Int, width: Float, height: Float, margin: Float): Boolean {
        if (cols == this.cols && rows == this.rows && width == this.width && height == this.height) return false
        this.cols = cols; this.rows = rows; this.width = width; this.height = height
        val count = (cols + 1) * (rows + 1)
        verts = FloatArray(count * 2)
        colors = IntArray(count)
        overlay = IntArray(count)
        indices = ShortArray(cols * rows * 6)
        for (j in 0..rows) for (i in 0..cols) {
            val at = index(i, j)
            verts[at * 2] = -margin + (width + margin * 2f) * i / cols
            verts[at * 2 + 1] = -margin + (height + margin * 2f) * j / rows
        }
        var n = 0
        for (j in 0 until rows) for (i in 0 until cols) {
            val a = index(i, j).toShort(); val b = index(i + 1, j).toShort()
            val c = index(i + 1, j + 1).toShort(); val d = index(i, j + 1).toShort()
            indices[n++] = a; indices[n++] = b; indices[n++] = c
            indices[n++] = a; indices[n++] = c; indices[n++] = d
        }
        return true
    }

    fun index(i: Int, j: Int) = j * (cols + 1) + i
    fun x(i: Int) = verts[i * 2]
    fun y(j: Int) = verts[index(0, j) * 2 + 1]

    /**
     * Draw the mesh. With [textured], the paint's shader is sampled in artwork units and
     * modulated by the vertex colors (used for halftone screens that follow the shading).
     */
    fun draw(canvas: Canvas, paint: Paint, textured: Boolean = false, colors: IntArray = this.colors) {
        if (cols == 0) return
        if (canvas.supportsVertices()) {
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, verts.size, verts, 0, if (textured) verts else null, 0,
                colors, 0, indices, 0, indices.size, paint)
            return
        }
        val color = paint.color
        for (j in 0 until rows) for (i in 0 until cols) {
            val a = index(i, j)
            paint.color = colors[a]
            canvas.drawRect(verts[a * 2], verts[a * 2 + 1], verts[(a + cols + 2) * 2], verts[(a + cols + 2) * 2 + 1], paint)
        }
        paint.color = color
    }
}
