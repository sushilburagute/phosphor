package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.mix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Oblique relief of a basalt canyon. Each contour has a cut face, a shelf and an exposed rim. */
internal class TextureBasalt(private val k: RenderKit) {
    private class Shelf(val top: Path, val foot: Path, val walls: Path, val rim: Array<Path>)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stoneNoise = SpaceNoise(281)
    private val far by lazy(LazyThreadSafetyMode.NONE) { shelves(18, 1.7f) }
    private val near by lazy(LazyThreadSafetyMode.NONE) { shelves(23, 4.1f) }
    private var tint = 0
    private var ground = 0
    private var copper = 0
    private var rock: Shader? = null
    private var cut: Shader? = null

    fun draw(canvas: Canvas, f: Frame) {
        if (rock == null || f.c0 != ground || f.c1 != tint || f.c2 != copper) {
            ground = f.c0
            tint = f.c1
            copper = f.c2
            rock = LinearGradient(-760f, -350f, 620f, 370f,
                intArrayOf(mix(ground, tint, 0.17f), mix(ground, tint, 0.55f),
                    mix(ground, copper, 0.23f), mix(ground, tint, 0.18f)),
                floatArrayOf(0f, 0.39f, 0.70f, 1f), Shader.TileMode.CLAMP)
            cut = LinearGradient(-590f, -330f, 650f, 240f, mix(ground, tint, 0.22f),
                mix(ground, Color.BLACK, 0.46f), Shader.TileMode.CLAMP)
        }
        val light = -2.25f + f.composition * 4.8f + sin(f.t * 0.21f) * f.amount * 0.85f
        k.glow(canvas, k.glowA, 460f + f.shiftX(0.2f), f.h * 0.51f, 810f, f.h * 0.33f, 44)
        // The offset outcrops open a winding channel through the center of the image.
        outcrop(canvas, f, far, 86f, f.h * 0.37f, -27f, 0.79f, 0.47f, light)
        outcrop(canvas, f, near, 887f, f.h * 0.72f, -38f, 1.08f, 0.90f, light)
    }

    private fun outcrop(canvas: Canvas, f: Frame, layers: List<Shelf>, x: Float, y: Float,
        angle: Float, scale: Float, depth: Float, light: Float) {
        val saved = canvas.save()
        canvas.translate(x + f.shiftX(depth), y + f.shiftY(depth))
        val size = scale * minOf(1.12f, f.h / 980f)
        canvas.scale(size, size)
        canvas.rotate(angle)
        val localLight = light - angle * DEGREE
        val rimColor = mix(f.c1, f.c2, 0.32f)
        // A broad shadow anchors the cliff to the canyon floor.
        k.glow(canvas, k.glowGround, 35f, 75f, 870f, 520f, 200)
        for ((index, shelf) in layers.withIndex()) {
            val exposedSeam = index % 7 == 3
            val layerSave = canvas.save()
            // Closer terraces travel further in the camera pan than the foot of the pile.
            val elevation = index.toFloat() / layers.size
            canvas.translate(f.shiftX(0.33f) * elevation, f.shiftY(0.28f) * elevation)
            ink.style = Paint.Style.FILL
            ink.color = alpha(Color.BLACK, 80)
            canvas.drawPath(shelf.foot, ink)
            ink.shader = cut
            ink.alpha = 255
            canvas.drawPath(shelf.walls, ink)
            ink.shader = rock
            canvas.drawPath(shelf.top, ink)
            ink.shader = null
            // Tiny variations in mineral composition expose individual strata.
            ink.color = if (exposedSeam) alpha(f.c2, 19) else alpha(f.c0, (index * 7) % 29)
            canvas.drawPath(shelf.top, ink)
            ink.style = Paint.Style.STROKE
            ink.strokeWidth = if (exposedSeam) 1.9f else 1.35f
            if (f.detail > 0.60f || index % 2 == 0 || exposedSeam) {
                for (bin in shelf.rim.indices) {
                    val facing = cos(bin * TURN / FACES - localLight).coerceAtLeast(0f)
                    ink.color = alpha(if (exposedSeam) f.c2 else rimColor, (12f + 156f * facing * facing).toInt())
                    canvas.drawPath(shelf.rim[bin], ink)
                }
            }
            canvas.restoreToCount(layerSave)
        }
        canvas.restoreToCount(saved)
    }

    /** The same folded rock volume is intersected at successively higher elevations. */
    private fun shelves(count: Int, seed: Float): List<Shelf> {
        val result = List(count) { layer -> buildShelf(layer, count, seed) }
        val inset = Matrix()
        // Omit the stone covered by the next terrace. A small bleed leaves room for differential parallax.
        for (i in 0 until result.lastIndex) {
            val level = (i + 1f) / count
            inset.setScale(0.9f, 0.9f, -78f * level, -168f * level)
            result[i].top.fillType = Path.FillType.EVEN_ODD
            result[i].top.addPath(result[i + 1].top, inset)
        }
        return result
    }

    private fun buildShelf(layer: Int, count: Int, seed: Float): Shelf {
        val level = layer.toFloat() / count
        val radius = sqrt(1f - level)
        val points = FloatArray(SAMPLES * 2)
        val drop = 7.5f + 4f * sin(seed + layer * 0.75f)
        for (i in 0 until SAMPLES) {
            val angle = i * TURN / SAMPLES
            val ux = cos(angle)
            val uy = sin(angle)
            val fold = 1f + 0.15f * cos(3f * angle + seed + level * 0.7f) +
                0.085f * sin(5f * angle - seed) + 0.06f * cos(2f * angle + level * 2f)
            val grain = stoneNoise.sample(ux * 6f + seed, uy * 6f + level * 2.3f) - 0.5f
            points[2 * i] = ux * (fold + grain * 0.055f) * 780f * radius - 78f * level
            points[2 * i + 1] = uy * (fold + grain * 0.06f) * 420f * radius -
                168f * level + ux * ux * 86f * radius
        }
        val top = Path()
        val walls = Path()
        val foot = Path()
        val rim = Array(FACES) { Path() }
        top.moveTo(points[0], points[1])
        for (i in 1 until SAMPLES) {
            top.lineTo(points[2 * i], points[2 * i + 1])
        }
        top.close()
        // Only the front-facing walls extend below the projected lid.
        for (i in 0 until SAMPLES) {
            val j = (i + 1) % SAMPLES
            val x = points[2 * i]; val y = points[2 * i + 1]
            val x1 = points[2 * j]; val y1 = points[2 * j + 1]
            if (x1 < x) {
                walls.moveTo(x1, y1); walls.lineTo(x, y); walls.lineTo(x, y + drop)
                walls.lineTo(x1, y1 + drop); walls.close()
            }
            val normal = kotlin.math.atan2(x - x1, y1 - y)
            val bin = Math.floorMod(kotlin.math.round(normal * FACES / TURN).toInt(), FACES)
            rim[bin].apply { moveTo(x, y); lineTo(x1, y1) }
        }
        walls.offset(1.8f, 3f, foot)
        return Shelf(top, foot, walls, rim)
    }

    private companion object {
        const val SAMPLES = 152
        const val FACES = 8
        const val TURN = (2 * PI).toFloat()
        const val DEGREE = (PI / 180).toFloat()
    }
}
