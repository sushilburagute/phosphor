package dev.sush.phosphor.render

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Everything a scene needs for one frame. Positions are in artwork units: 1000 wide, [h] tall.
 *
 * [t] is animation time (already scaled by the speed setting). [now] is real time, used for
 * touch pulses so a tap still answers when ambient motion speed is zero.
 */
class Frame {
    var h = 2000f
    var t = 0f
    var now = 0f
    var dt = 0f
    var amount = 0.6f
    var night = true
    var reveal = 1f
    var detail = 1f
    var offsetX = 0f
    var offsetY = 0f
    var c0 = Color.BLACK
    var c1 = Color.WHITE
    var c2 = Color.GRAY

    val pulseX = FloatArray(TouchField.MAX)
    val pulseY = FloatArray(TouchField.MAX)
    val pulseR = FloatArray(TouchField.MAX)
    val pulseA = FloatArray(TouchField.MAX)
    var pulses = 0
    var dragX = 500f
    var dragY = 1000f
    var drag = 0f

    /** Horizontal parallax shift for a layer; distant layers pass small depths. */
    fun shiftX(depth: Float) = offsetX * 34f * depth * (0.4f + amount * 0.6f)
    fun shiftY(depth: Float) = offsetY * 26f * depth * (0.4f + amount * 0.6f)

    /** Strength of every pulse wavefront passing through a point, 0 when nothing is happening. */
    fun wave(x: Float, y: Float, width: Float = 80f): Float {
        var sum = 0f
        for (i in 0 until pulses) {
            val dx = x - pulseX[i]
            val dy = y - pulseY[i]
            val d = sqrt(dx * dx + dy * dy)
            val k = (d - pulseR[i]) / width
            if (k > -3f && k < 3f) sum += exp(-k * k) * pulseA[i]
        }
        return sum
    }

    /** One-dimensional wavefronts travelling sideways from each tap, for curtain-like art. */
    fun waveX(x: Float, width: Float = 70f): Float {
        var sum = 0f
        for (i in 0 until pulses) {
            val k = (kotlin.math.abs(x - pulseX[i]) - pulseR[i] * 0.8f) / width
            if (k > -3f && k < 3f) sum += exp(-k * k) * pulseA[i]
        }
        return sum
    }

    /** Gaussian influence of the finger currently held on the screen. */
    fun dragInfluence(x: Float, y: Float, radius: Float): Float {
        if (drag <= 0.001f) return 0f
        val dx = x - dragX
        val dy = y - dragY
        return exp(-(dx * dx + dy * dy) / (radius * radius)) * drag
    }

    val busy: Boolean get() = pulses > 0 || drag > 0.001f
}

/** Shared paints, cached palette shaders and pure helpers. One kit per renderer. */
class RenderKit {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    val path = Path()
    val rect = RectF()

    private var key: Long = 0
    var glowA: Shader? = null; private set
    var glowB: Shader? = null; private set
    var glowWhite: Shader? = null; private set
    var glowGround: Shader? = null; private set

    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)

    fun prepare(f: Frame) {
        val next = (f.c0.toLong() * 31 + f.c1) * 31 + f.c2 + if (f.night) 7 else 3
        if (next == key && glowA != null) return
        key = next
        glowA = radial(f.c1)
        glowB = radial(f.c2)
        glowWhite = radial(if (f.night) Color.WHITE else mix(f.c0, Color.WHITE, 0.6f))
        glowGround = radial(f.c0)
    }

    private fun radial(color: Int) = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(alpha(color, 255), alpha(color, 150), alpha(color, 52), alpha(color, 0)),
        floatArrayOf(0f, 0.32f, 0.66f, 1f), Shader.TileMode.CLAMP,
    )

    /** Emitted light at night: overlapping strokes build up like a long exposure. */
    fun additive(paint: Paint, night: Boolean) = blend(paint, if (night) Mode.ADD else Mode.INK)

    /** Soft light at night, overprinted ink by day. */
    fun luminous(paint: Paint, night: Boolean) = blend(paint, if (night) Mode.SCREEN else Mode.INK)

    fun normal(paint: Paint) = blend(paint, Mode.NORMAL)

    enum class Mode { NORMAL, ADD, SCREEN, INK }

    fun blend(paint: Paint, mode: Mode) {
        when (mode) {
            Mode.NORMAL -> { paint.xfermode = null; if (Build.VERSION.SDK_INT >= 29) paint.blendMode = null }
            Mode.ADD -> paint.xfermode = add
            Mode.SCREEN -> paint.xfermode = screen
            // Porter-Duff MULTIPLY also multiplies alpha; BlendMode.MULTIPLY is the separable
            // overprint. API 27-28 falls back to translucent ink, which reads similarly.
            Mode.INK -> if (Build.VERSION.SDK_INT >= 29) paint.blendMode = BlendMode.MULTIPLY else paint.xfermode = null
        }
    }

    fun glow(canvas: Canvas, shader: Shader?, x: Float, y: Float, rx: Float, ry: Float, opacity: Int, mode: Mode = Mode.NORMAL) {
        if (opacity <= 0 || rx <= 0f || ry <= 0f) return
        val saved = canvas.save()
        canvas.translate(x, y)
        canvas.scale(rx, ry)
        fill.shader = shader
        fill.alpha = opacity.coerceIn(0, 255)
        fill.style = Paint.Style.FILL
        blend(fill, mode)
        canvas.drawCircle(0f, 0f, 1f, fill)
        canvas.restoreToCount(saved)
        reset(fill)
    }

    fun reset(paint: Paint) {
        paint.shader = null
        paint.alpha = 255
        normal(paint)
    }

    /** A tiny figure for scale, standing with a long shadow, optionally mid-stride. */
    fun figure(canvas: Canvas, x: Float, y: Float, height: Float, color: Int, stride: Float = 0f, shadow: Int = 0) {
        val u = height / 30f
        fill.shader = null
        normal(fill)
        if (shadow != 0) {
            fill.color = shadow
            rect.set(x - 46f * u, y - 0.9f * u, x + 1.5f * u, y + 0.9f * u)
            canvas.drawRoundRect(rect, u, u, fill)
        }
        fill.color = color
        canvas.drawCircle(x, y - 26.5f * u, 2.6f * u, fill)
        rect.set(x - 3.4f * u, y - 23.2f * u, x + 3.4f * u, y - 11f * u)
        canvas.drawRoundRect(rect, 2.6f * u, 2.6f * u, fill)
        stroke.shader = null
        normal(stroke)
        stroke.color = color
        stroke.strokeWidth = 2.6f * u
        canvas.drawLine(x - 1.4f * u, y - 12f * u, x - 1.6f * u - stride * 3f * u, y - 0.8f * u, stroke)
        canvas.drawLine(x + 1.4f * u, y - 12f * u, x + 1.6f * u + stride * 3f * u, y - 0.8f * u, stroke)
    }

    fun release() {
        glowA = null; glowB = null; glowWhite = null; glowGround = null
        key = 0
    }

    companion object {
        fun alpha(color: Int, opacity: Int): Int = (color and 0x00FFFFFF) or (opacity.coerceIn(0, 255) shl 24)

        fun mix(a: Int, b: Int, ratio: Float): Int {
            val f = ratio.coerceIn(0f, 1f)
            return Color.rgb(
                (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
                (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
                (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt(),
            )
        }

        fun hash(seed: Int): Int {
            var value = seed
            value = ((value ushr 16) xor value) * 0x45d9f3b
            value = ((value ushr 16) xor value) * 0x45d9f3b
            return ((value ushr 16) xor value) and Int.MAX_VALUE
        }

        fun fraction(seed: Int): Float = (hash(seed) and 65535) / 65535f

        fun smooth(x: Float): Float { val v = x.coerceIn(0f, 1f); return v * v * (3f - 2f * v) }

        /** Relative luminance (WCAG) of an opaque sRGB color. */
        fun luminance(color: Int): Float {
            fun channel(c: Int): Float { val s = c / 255f; return if (s <= 0.03928f) s / 12.92f else Math.pow(((s + 0.055f) / 1.055f).toDouble(), 2.4).toFloat() }
            return 0.2126f * channel(Color.red(color)) + 0.7152f * channel(Color.green(color)) + 0.0722f * channel(Color.blue(color))
        }

        fun vertical(top: Int, bottom: Int, y0: Float, y1: Float) =
            LinearGradient(0f, y0, 0f, y1, top, bottom, Shader.TileMode.CLAMP)
    }
}
