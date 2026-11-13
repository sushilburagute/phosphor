package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import dev.sush.phosphor.model.ColorTheory
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.hash
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.floor
import kotlin.math.sign

/**
 * Digital rain. The characters sit still in a grid and streams of light fall through them, so
 * a glyph flares as a stream's head reaches it and fades behind, flickering now and then.
 *
 * Three layers give the rain volume, drawn far to near. Distant columns are smaller, slower,
 * dimmer and shift toward the cooler accent (atmospheric perspective); by day they also fade
 * toward the paper. The whole layer is mirrored, so the katakana read reversed, as in the film.
 * Each stream is one draw call: its fade comes from a gradient shader, not per-glyph alpha.
 */
internal class RainScene(private val k: RenderKit) {
    private class Layer(val size: Float, val speed: Float, val opacity: Float, val depth: Float, val cool: Float)

    private val layers = arrayOf(
        Layer(19f, 0.55f, 0.34f, 0.25f, 0.75f),
        Layer(26f, 0.8f, 0.62f, 0.6f, 0.35f),
        Layer(35f, 1.1f, 1f, 1.1f, 0f),
    )
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val chars = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123457889Z:.=*+<>¦".toCharArray()
    private val text = CharArray(MAX_TRAIL)
    private val pos = FloatArray(MAX_TRAIL * 2)
    private val shaders = arrayOfNulls<Shader>(3)
    private val local = Matrix()
    private var key = 0L
    private var head = Color.WHITE

    private fun prepare(f: Frame) {
        val next = (f.c0.toLong() * 31 + f.c1) * 31 + f.c2 + if (f.night) 1 else 2
        if (next == key && shaders[0] != null) return
        key = next
        for ((i, layer) in layers.withIndex()) {
            var color = mix(f.c1, f.c2, layer.cool)
            if (!f.night) color = mix(color, f.c0, layer.cool * 0.45f)
            val tip = if (f.night) mix(color, Color.WHITE, 0.4f) else ColorTheory.withLightness(color, 0.36f)
            // Local space: 0 is the tail, 1 is the head.
            shaders[i] = LinearGradient(0f, 0f, 0f, 1f,
                intArrayOf(alpha(color, 0), alpha(color, 60), alpha(color, 175), alpha(tip, 255)),
                floatArrayOf(0f, 0.45f, 0.86f, 1f), Shader.TileMode.CLAMP)
        }
        head = if (f.night) mix(f.c1, Color.WHITE, 0.82f) else ColorTheory.withLightness(f.c1, 0.26f, 0.8f)
    }

    fun draw(canvas: Canvas, f: Frame) {
        prepare(f)
        val h = f.h
        val mode = k.modeFor(f)
        k.glow(canvas, k.glowA, 520f, h * 0.38f, 950f, h * 0.42f, if (f.night) 42 else 80, mode)
        k.glow(canvas, k.glowB, 120f, h * 0.86f, 700f, h * 0.3f, if (f.night) 26 else 90, mode)

        val saved = canvas.save()
        canvas.scale(-1f, 1f, 500f, 0f)
        for (i in layers.indices) stream(canvas, f, i)
        if (f.pulses > 0) ring(canvas, f)
        canvas.restoreToCount(saved)
    }

    private fun stream(canvas: Canvas, f: Frame, index: Int) {
        val layer = layers[index]
        val h = f.h
        val cellW = layer.size * 0.92f
        val cellH = layer.size * 1.12f
        val cols = (1000f / cellW).toInt() + 2
        val rows = (h / cellH).toInt() + 2
        val reveal = smooth(f.reveal)
        // Rain falls in from the top on wake.
        val floorY = (h + 200f) * (0.1f + reveal * 0.9f)
        glyph.textSize = layer.size
        glyph.shader = shaders[index]
        k.additive(glyph, f.night)
        // Ink on pale paper needs more weight than light on black to read at the same strength.
        val weight = if (f.night) 1f else 1.3f
        glyph.alpha = (255f * layer.opacity * weight * (0.7f + f.amount * 0.3f) * (0.35f + reveal * 0.65f)).toInt().coerceIn(0, 255)
        val shift = -f.shiftX(layer.depth)
        val streams = if (f.amount > 0.7f) 2 else 1
        for (col in 0 until cols) {
            val seed = index * 4099 + col * 131
            val baseX = col * cellW + cellW * 0.5f + shift
            for (s in 0 until streams + if (fraction(seed + 17) < 0.55f) 1 else 0) {
                val ss = seed + s * 7919
                if (fraction(ss + 1) < 0.1f) continue  // some columns rest
                val speed = (11f + fraction(ss + 2) * 16f) * layer.speed
                val trail = ((7f + fraction(ss + 3).let { it * it } * 26f) * (0.6f + f.amount * 0.6f)).toInt().coerceIn(3, MAX_TRAIL - 1)
                val period = rows + trail + fraction(ss + 4) * rows * 0.9f
                val headPos = ((f.t * speed + fraction(ss + 5) * period) % period) - trail * 0.3f
                val headRow = floor(headPos).toInt()
                val first = maxOf(0, headRow - trail + 1)
                val last = minOf(rows, headRow)
                if (last < first) continue
                var n = 0
                for (row in first..last) {
                    val y = row * cellH + layer.size
                    if (y > floorY) break
                    var x = baseX
                    if (f.drag > 0.001f) {
                        // A held finger parts the rain like an umbrella. World x is mirrored.
                        val worldX = 1000f - x
                        x -= f.dragInfluence(worldX, y, 150f + layer.size * 3f) * 80f * sign(worldX - f.dragX)
                    }
                    text[n] = chars[glyphAt(ss, row, f.t)]
                    pos[n * 2] = x
                    pos[n * 2 + 1] = y
                    n++
                }
                if (n == 0) continue
                val tail = (headRow - trail + 1) * cellH + layer.size
                local.setScale(1f, (headRow * cellH + layer.size - tail).coerceAtLeast(1f))
                local.postTranslate(0f, tail)
                glyph.shader?.setLocalMatrix(local)
                @Suppress("DEPRECATION")
                canvas.drawPosText(text, 0, n, pos, glyph)
                // The leading glyph burns brightest, with a small bloom on the near layers.
                if (headRow in 0..rows && pos[(n - 1) * 2 + 1] == headRow * cellH + layer.size) {
                    val hx = pos[(n - 1) * 2]
                    val hy = pos[(n - 1) * 2 + 1]
                    if (index > 0 && f.night) {
                        k.glow(canvas, k.glowWhite, hx, hy - layer.size * 0.35f, layer.size * 1.3f, layer.size * 1.3f,
                            (36 * layer.opacity).toInt(), RenderKit.Mode.ADD)
                    }
                    val shader = glyph.shader
                    val opacity = glyph.alpha
                    glyph.shader = null
                    glyph.color = head
                    glyph.alpha = opacity
                    canvas.drawText(text, n - 1, 1, hx, hy, glyph)
                    glyph.shader = shader
                    glyph.alpha = opacity
                }
            }
        }
        glyph.shader = null
        k.normal(glyph)
    }

    /** Each tap sends a ring of freshly decoded characters out across the rain. */
    private fun ring(canvas: Canvas, f: Frame) {
        val layer = layers[1]
        val cellW = layer.size * 0.92f
        val cellH = layer.size * 1.12f
        glyph.textSize = layer.size
        glyph.shader = null
        k.additive(glyph, f.night)
        glyph.color = head
        val cols = (1000f / cellW).toInt() + 2
        val rows = (f.h / cellH).toInt() + 2
        for (row in 0 until rows) {
            val y = row * cellH + layer.size
            for (col in 0 until cols) {
                val x = col * cellW + cellW * 0.5f
                val wave = f.wave(1000f - x, y, 46f)
                if (wave < 0.12f) continue
                glyph.alpha = (wave * 255f).toInt().coerceIn(0, 255)
                val c = hash(row * 977 + col + (f.now * 20f).toInt() * 31) % chars.size
                canvas.drawText(chars, c, 1, x, y, glyph)
            }
        }
        k.normal(glyph)
    }

    /** Most characters hold still; a few flicker to a new glyph every so often. */
    private fun glyphAt(seed: Int, row: Int, t: Float): Int {
        val cell = seed + row * 31
        val rate = if (fraction(cell + 9) < 0.2f) 3.5f else 0.25f
        val tick = floor(t * rate + fraction(cell + 3) * 9f).toInt()
        return hash(cell * 17 + tick * 7907) % chars.size
    }

    companion object {
        private const val MAX_TRAIL = 48
    }
}
