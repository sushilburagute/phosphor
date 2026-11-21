package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A jointed lava bed seen obliquely, with a narrow erosion channel cutting across it. */
internal class TextureBasalt {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val noise = SpaceNoise(281)
    private var stone: Bitmap? = null
    private var sceneHeight = 0f
    private var meshRows = 0
    private var vertices = FloatArray(0)
    private var relief = FloatArray(0)
    private val lights = arrayOfNulls<ColorMatrixColorFilter>(96)
    private var ground = 0
    private var slate = 0
    private var mineral = 0

    fun draw(canvas: Canvas, f: Frame) {
        if (stone == null || sceneHeight != f.h) bake(f.h)
        if (ground != f.c0 || slate != f.c1 || mineral != f.c2) {
            ground = f.c0
            slate = f.c1
            mineral = f.c2
            lights.fill(null)
        }
        val light = (0.48f + f.composition * 0.74f + 0.20f * f.amount * sin(f.t * 0.32f)).coerceIn(0f, 1f)
        val step = (light * lights.lastIndex).roundToInt()
        ink.colorFilter = lights[step] ?: lighting(step.toFloat() / lights.lastIndex).also { lights[step] = it }
        var i = 0
        for (row in 0..meshRows) {
            val y = -MARGIN + (f.h + 2f * MARGIN) * row / meshRows
            for (col in 0..MESH_COLS) {
                val depth = 0.24f + 0.84f * relief[i]
                vertices[2 * i] = -MARGIN + WIDTH * col / MESH_COLS + f.shiftX(depth)
                vertices[2 * i + 1] = y + f.shiftY(depth)
                i++
            }
        }
        canvas.drawBitmapMesh(checkNotNull(stone), MESH_COLS, meshRows, vertices, 0, null, 0, ink)
        ink.colorFilter = null
    }

    // The baked RGB channels contain skylight and diffuse illumination from two oblique directions.
    // Relighting the rock takes one draw regardless of the number of columns in view.
    private fun lighting(light: Float): ColorMatrixColorFilter {
        val cool = mix(slate, Color.WHITE, 0.09f)
        val amber = mix(slate, mineral, 0.90f)
        val matrix = FloatArray(20)
        for (i in 0..2) {
            val shift = 16 - i * 8
            val base = (ground ushr shift) and 255
            val sky = (((cool ushr shift) and 255) - base) / 255f
            val sun = (((amber ushr shift) and 255) - base) / 255f
            matrix[5 * i] = sky
            matrix[5 * i + 1] = sun * (1f - light)
            matrix[5 * i + 2] = sky * light
            matrix[5 * i + 4] = base.toFloat()
        }
        matrix[18] = 1f
        return ColorMatrixColorFilter(matrix)
    }

    private fun bake(h: Float) {
        sceneHeight = h
        val scale = minOf(0.74f, 1560f / (h + 2f * MARGIN))
        val bitmap = Bitmap.createBitmap(ceil(WIDTH * scale).toInt().coerceAtLeast(1),
            ceil((h + 2f * MARGIN) * scale).toInt(), Bitmap.Config.ARGB_8888)
        val surface = Canvas(bitmap)
        surface.drawColor(Color.rgb(6, 1, 2))
        surface.scale(scale, scale)
        surface.translate(MARGIN, MARGIN)
        val count = ceil((h + MARGIN + 390f) / ROW_STEP).toInt()
        val rows = (-3..count).map { row -> (-3..13).map { col ->
            val seed = row * 14327 + col * 571
            PointF((col + (row and 1) * 0.5f + (fraction(seed) - 0.5f) * 0.53f) * COL_STEP,
                (row + (fraction(seed + 37) - 0.5f) * 0.54f) * ROW_STEP)
        } }
        // In this projection the faces grow toward the bottom edge. Paint back to front.
        for (r in 1 until rows.lastIndex) {
            for (c in (1 until rows[r].lastIndex).sortedBy { rows[r][it].y }) {
                val center = rows[r][c]
                var boundary = listOf(PointF(-COL_STEP, -ROW_STEP), PointF(COL_STEP, -ROW_STEP),
                    PointF(COL_STEP, ROW_STEP), PointF(-COL_STEP, ROW_STEP))
                for (dr in -1..1) for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val neighbor = rows[r + dr][c + dc]
                    boundary = clipCell(boundary, neighbor.x - center.x, neighbor.y - center.y)
                }
                drawColumn(surface, center, boundary, h, (r - 3) * 14327 + (c - 3) * 571)
            }
        }
        meshRows = ceil((h + 2f * MARGIN) / 55f).toInt().coerceIn(8, 72)
        relief = FloatArray((MESH_COLS + 1) * (meshRows + 1)) { i ->
            val x = (i % (MESH_COLS + 1)) * (bitmap.width - 1) / MESH_COLS
            val y = (i / (MESH_COLS + 1)) * (bitmap.height - 1) / meshRows
            (Color.red(bitmap.getPixel(x, y)) / 65f).coerceIn(0f, 1f)
        }
        vertices = FloatArray(relief.size * 2)
        stone?.recycle()
        stone = bitmap
    }

    // Perpendicular bisectors give neighboring joints a shared, irregular edge.
    private fun clipCell(points: List<PointF>, dx: Float, dy: Float): List<PointF> {
        if (points.isEmpty()) return points
        val ny = dy * 2.2f // foreshorten the ground plane
        val mid = (dx * dx + dy * ny) * 0.5f
        val clipped = ArrayList<PointF>(8)
        var a = points.last()
        var da = a.x * dx + a.y * ny - mid
        for (b in points) {
            val db = b.x * dx + b.y * ny - mid
            if ((da <= 0f) != (db <= 0f)) {
                val t = da / (da - db)
                clipped.add(PointF(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y)))
            }
            if (db <= 0f) clipped.add(b)
            a = b
            da = db
        }
        return clipped
    }

    private fun drawColumn(canvas: Canvas, center: PointF, outline: List<PointF>, h: Float, seed: Int) {
        if (outline.size < 3) return
        val u = center.y / h
        val fault = 505f - 430f * (u - 0.5f) + 145f * sin(u * 6.4f + 0.34f)
        val distance = center.x - fault
        val rough = noise.sample(center.x * 0.0036f, center.y * 0.0036f)
        val cliff = smooth((abs(distance) + 88f * (rough - 0.5f) - 26f) / 310f)
        val uplift = if (distance < 0f) 138f else 266f
        val height = 8f + cliff * (uplift + 118f * rough) + fraction(seed + 16) * (18f + 90f * cliff)
        val exposure = (0.14f + 0.86f * cliff) * (0.74f + 0.26f * rough)
        val slopeX = (fraction(seed + 73) - 0.57f) * 0.45f
        val slopeY = (fraction(seed + 24) - 0.45f) * 0.43f
        val top = outline.map { p -> PointF(center.x + p.x * 0.978f,
            center.y - height + p.y * 0.978f + slopeX * p.x + slopeY * p.y) }
        val bottom = outline.map { p -> PointF(center.x + p.x, center.y + p.y + 6f) }
        val paint = ink
        for (i in top.indices) {
            val j = (i + 1) % top.size
            val a = top[i]
            val b = top[j]
            val dx = b.x - a.x
            if (dx > 0f) continue
            val dy = b.y - a.y
            val nx = dy / sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            val left = (0.16f - nx * 0.76f).coerceIn(0f, 1f)
            val right = (0.25f + nx * 0.71f).coerceIn(0f, 1f)
            val shoulder = channels(7f + exposure * 23f, left * exposure * 230f, right * exposure * 185f)
            path.rewind()
            path.moveTo(a.x, a.y)
            path.lineTo(b.x, b.y)
            path.lineTo(bottom[j].x, bottom[j].y)
            path.lineTo(bottom[i].x, bottom[i].y)
            path.close()
            val y = 0.5f * (a.y + b.y)
            paint.shader = LinearGradient(0f, y, 0f, y + height * 0.86f, shoulder,
                mix(shoulder, Color.BLACK, 0.83f), Shader.TileMode.CLAMP)
            paint.color = Color.WHITE
            canvas.drawPath(path, paint)
            paint.shader = null
            // Narrow growth flutes follow each exposed face down into the cleft.
            for (n in 1..6) {
                val seedLine = seed + i * 103 + n * 17
                val t = (n + fraction(seedLine) * 0.65f) / 7f
                val x = a.x + t * dx
                val y0 = a.y + t * dy + 4f
                paint.color = RenderKit.alpha(shoulder, 35 + (fraction(seedLine + 5) * 67f).toInt())
                paint.strokeWidth = 0.8f + fraction(seedLine + 8) * 2f
                canvas.drawLine(x, y0, x + 1.8f, y0 + height * (0.15f + 0.69f * fraction(seedLine + 3)), paint)
            }
        }
        path.rewind()
        path.moveTo(top.first().x, top.first().y)
        for (i in 1 until top.size) path.lineTo(top[i].x, top[i].y)
        path.close()
        val cap = channels(7f + exposure * (39f + 25f * rough),
            exposure * (46f + slopeX * 155f + slopeY * 42f),
            exposure * (43f - slopeX * 123f - slopeY * 42f))
        val a0 = top.first()
        val opposite = top[top.size / 2]
        paint.shader = LinearGradient(a0.x, a0.y, opposite.x, opposite.y,
            cap, mix(cap, Color.BLACK, 0.24f), Shader.TileMode.CLAMP)
        paint.color = Color.WHITE
        canvas.drawPath(path, paint)
        paint.shader = null
        // A broken corner exposes a fresh plane. Its irregular crease ends inside the cap.
        val fracture = fraction(seed + 57)
        if (fracture > 0.36f) {
            val i = (fracture * 127f).toInt() % top.size
            val a = top[i]
            val tip = top[(i + 1) % top.size]
            val b = top[(i + 2) % top.size]
            val left = PointF(a.x + (tip.x - a.x) * 0.21f, a.y + (tip.y - a.y) * 0.21f)
            val right = PointF(b.x + (tip.x - b.x) * 0.38f, b.y + (tip.y - b.y) * 0.38f)
            val split = 0.18f + 0.38f * fraction(seed + 59)
            val x = tip.x + (center.x - tip.x) * split
            val y = tip.y + (center.y - height - tip.y) * split
            path.rewind()
            path.moveTo(left.x, left.y)
            path.lineTo(tip.x, tip.y)
            path.lineTo(right.x, right.y)
            path.lineTo(x, y)
            path.close()
            paint.color = channels(Color.red(cap) * 0.85f, Color.green(cap) + 78f * exposure,
                Color.blue(cap) * 0.68f)
            canvas.drawPath(path, paint)
            paint.color = mix(cap, Color.BLACK, 0.50f)
            paint.strokeWidth = 1.25f
            canvas.drawLine(left.x, left.y, x, y, paint)
        }
        for (i in top.indices) {
            val a = top[i]
            val b = top[(i + 1) % top.size]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val side = dy / sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            paint.color = channels(Color.red(cap) + exposure * 12f,
                Color.green(cap) + exposure * (43f - side * 42f), Color.blue(cap) + exposure * (40f + side * 37f))
            paint.strokeWidth = 0.7f + fraction(seed + i) * 1.6f
            canvas.drawLine(a.x, a.y, b.x, b.y, paint)
        }
    }

    private fun channels(sky: Float, left: Float, right: Float) =
        Color.rgb(sky.toInt().coerceIn(0, 255), left.toInt().coerceIn(0, 255), right.toInt().coerceIn(0, 255))

    fun release() {
        stone?.recycle()
        stone = null
        lights.fill(null)
        vertices = FloatArray(0)
        relief = FloatArray(0)
    }

    private companion object {
        const val COL_STEP = 115f
        const val ROW_STEP = 74f
        const val MARGIN = 100f
        const val WIDTH = 1000f + 2 * MARGIN
        const val MESH_COLS = 24
    }
}
