package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.mix
import dev.sush.phosphor.render.RenderKit.Companion.smooth
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Long-exposure light trails: the ORBIT family. Each preset lives in its own file
 * ([OrbitExposure], [OrbitThreshold], [OrbitRingfall]); this file holds what they share.
 *
 * Night draws additive light, so crossing trails brighten like a real long exposure. Day prints
 * the same trails in two overprinted inks with a slight misregistration, like a riso print.
 *
 * Every preset keeps the launcher's label rows (about 0.36, 0.48, 0.60 and 0.72 of the height)
 * quiet by composition: orbits are sized so their long, nearly horizontal stretches settle in
 * the gaps between rows, and only their steep flanks cross a row.
 */
internal class OrbitScene(k: RenderKit) {
    private val kit = OrbitKit(k)
    private val exposure = OrbitExposure(kit)
    private val threshold = OrbitThreshold(kit)
    private val ringfall = OrbitRingfall(kit)

    fun draw(canvas: Canvas, f: Frame, variant: String) {
        kit.step(f)
        when (variant) {
            "orbit-threshold" -> threshold.draw(canvas, f)
            "orbit-ringfall" -> ringfall.draw(canvas, f)
            else -> exposure.draw(canvas, f)
        }
    }
}

/** Shared state, batched specks and the gesture answers every orbit preset uses. */
internal class OrbitKit(val k: RenderKit) {
    /** Point batches: [CLASSES] colors x two sizes, each drawn with one drawPoints call. */
    private val batches = Array(CLASSES * 2) { FloatArray(BATCH * 2) }
    private val counts = IntArray(CLASSES * 2)
    private val wakeX = FloatArray(TouchField.TRAIL)
    private val wakeY = FloatArray(TouchField.TRAIL)

    private val laneColors = IntArray(LANES)
    private val laneShaders = arrayOfNulls<Shader>(LANES)
    private var laneCount = 0
    private var laneKey = 0L

    /** Extra orbital phase (degrees) a swipe has spun into the system; glides, then holds. */
    var spin = 0f; private set
    private var spinVelocity = 0f

    fun step(f: Frame) {
        if (!f.busy && f.dt == 0f) { spin = 0f; spinVelocity = 0f; return }
        if (f.dt <= 0f) return
        // A sideways swipe spins the orbits up; the extra speed glides away over about a second.
        spinVelocity += f.flowX * 0.06f * f.dt
        spinVelocity *= exp(-1.1f * f.dt)
        spin += spinVelocity * f.dt
    }

    /**
     * A vertical shader of [color] that thins light (or ink) to [depth] where it passes behind a
     * launcher label row, with soft shoulders: it reads as faint haze lanes across the exposure.
     * Cached per color, so a scene should draw from a small, fixed set of colors.
     */
    fun lane(f: Frame, color: Int, depth: Float = if (f.night) 0.42f else 0.55f): Shader {
        val key = f.h.toRawBits().toLong() * 1031 + (depth * 1000f).toLong()
        if (key != laneKey) { laneCount = 0; laneKey = key }
        for (i in 0 until laneCount) if (laneColors[i] == color) return laneShaders[i]!!
        val stops = FloatArray(18)
        val colors = IntArray(18)
        val full = alpha(color, 255)
        val thin = alpha(color, (255 * depth).toInt())
        stops[0] = 0f; colors[0] = full
        for (row in 0 until 4) {
            val top = 0.356f + row * 0.12f
            val bottom = top + 0.033f
            val j = 1 + row * 4
            stops[j] = top - 0.014f; colors[j] = full
            stops[j + 1] = top; colors[j + 1] = thin
            stops[j + 2] = bottom; colors[j + 2] = thin
            stops[j + 3] = bottom + 0.014f; colors[j + 3] = full
        }
        stops[17] = 1f; colors[17] = full
        val shader = LinearGradient(0f, 0f, 0f, f.h, colors, stops, Shader.TileMode.CLAMP)
        if (laneCount < LANES) {
            laneColors[laneCount] = color
            laneShaders[laneCount] = shader
            laneCount++
        }
        return shader
    }

    // region Specks

    fun speck(cls: Int, big: Boolean, x: Float, y: Float) {
        val b = cls * 2 + if (big) 1 else 0
        val n = counts[b]
        if (n >= BATCH) return
        batches[b][n * 2] = x
        batches[b][n * 2 + 1] = y
        counts[b] = n + 1
    }

    /**
     * Dots along an elliptical arc from [head] back over [sweep] degrees (negative sweeps run the
     * other way), like a strobed exposure. A few dots turn into white or ember specks.
     */
    fun dottedArc(
        f: Frame, cx: Float, cy: Float, rx: Float, ry: Float, head: Float, sweep: Float,
        spacing: Float, seed: Int, cls: Int, big: Boolean, specks: Float = 0.12f,
        lean: Float = 0f, pivot: Float = cy,
    ) {
        val sweepRad = sweep * DEG
        val reach = sqrt((rx * rx + ry * ry) * 0.5f)
        val n = (kotlin.math.abs(sweepRad) * reach / spacing).toInt().coerceIn(2, 180)
        val step = -sweepRad / n
        var c = cos(head * DEG)
        var s = sin(head * DEG)
        val cs = cos(step)
        val ss = sin(step)
        val bottom = f.h + 6f
        for (j in 0 until n) {
            // Point on the ellipse, skewed sideways by [lean] in proportion to its height above [pivot].
            val y = cy + ry * s
            val x = cx + rx * c + lean * (y - pivot)
            // Rotate the unit vector one step instead of calling sin/cos per dot.
            val nc = c * cs - s * ss
            s = s * cs + c * ss
            c = nc
            if (x < -6f || x > 1006f || y < -6f || y > bottom) continue
            val r = fraction(seed + j * 7)
            // The tail thins out, so each trail fades away from its head; label rows thin it too.
            if (r < j.toFloat() / n * 0.7f || (r < 0.5f && inLabelRow(y / f.h))) continue
            val tint = fraction(seed * 3 + j)
            val kind = when {
                tint < specks * 0.45f -> WHITE
                tint < specks * 0.8f -> EMBER
                tint < specks -> if (cls == EMBER || cls == RED) BLUE else RED
                else -> cls
            }
            speck(kind, big && r > 0.5f, x, y)
        }
    }

    /** Draw and empty every batch. [gain] scales the whole layer (reveal, intensity). */
    fun flushSpecks(canvas: Canvas, f: Frame, gain: Float, size: Float = 1f) {
        val p = k.dots
        p.shader = null
        k.additive(p, f.night)
        // Gallery thumbnails draw fewer, slightly larger dots so trails still read.
        val thumb = 1.9f - f.detail * 0.9f
        for (b in 0 until CLASSES * 2) {
            val n = counts[b]
            if (n == 0) continue
            val cls = b / 2
            p.color = specColor(f, cls)
            p.alpha = (specAlpha(f, cls) * gain).toInt().coerceIn(0, 255)
            p.strokeWidth = (if (b % 2 == 1) 3.3f else 1.9f) * size * thumb
            canvas.drawPoints(batches[b], 0, n * 2, p)
            counts[b] = 0
        }
        k.reset(p)
    }

    private fun specColor(f: Frame, cls: Int): Int = if (f.night) when (cls) {
        BLUE -> mix(f.c1, Color.WHITE, 0.12f)
        PALE -> mix(f.c1, Color.WHITE, 0.55f)
        WHITE -> Color.WHITE
        EMBER -> mix(f.c2, Color.WHITE, 0.25f)
        RED -> f.c2
        else -> mix(f.c1, f.c0, 0.35f)
    } else when (cls) {
        BLUE, PALE, DEEP -> f.c1
        WHITE -> mix(f.c1, Color.BLACK, 0.2f)
        else -> f.c2
    }

    private fun specAlpha(f: Frame, cls: Int): Float = if (f.night) when (cls) {
        BLUE -> 160f; PALE -> 180f; WHITE -> 230f; EMBER -> 215f; RED -> 190f; else -> 110f
    } else when (cls) {
        BLUE -> 170f; PALE -> 105f; WHITE -> 225f; EMBER -> 210f; RED -> 160f; else -> 70f
    }

    // endregion

    // region Sky

    /**
     * Dust and stars between [top] and [bottom], batched by color. Each one twinkles on its own
     * phase by stepping between a dim and a bright batch. [avoidLabels] thins them in label rows.
     */
    fun stars(f: Frame, count: Int, top: Float, bottom: Float, depth: Float, avoidLabels: Boolean = true) {
        val n = (count * (0.45f + f.detail * 0.55f)).toInt()
        if (smooth(f.reveal * 1.6f - 0.2f) <= 0.02f) return
        for (i in 0 until n) {
            val x = fraction(i * 13 + 7) * 1040f - 20f + f.shiftX(depth)
            val y = top + fraction(i * 29 + 3) * (bottom - top) + f.shiftY(depth)
            if (avoidLabels && inLabelRow(y / f.h) && fraction(i * 41) < 0.6f) continue
            val twinkle = sin(f.t * (0.9f + fraction(i + 99) * 2.6f) + i * 1.7f)
            val tint = fraction(i * 3 + 11)
            val big = fraction(i * 5 + 1) > 0.9f
            val boost = if (f.pulses > 0) f.wave(x, y, 70f) else 0f
            val cls = when {
                boost > 0.3f -> WHITE
                tint < 0.07f -> EMBER
                tint < 0.11f -> RED
                tint < 0.3f -> BLUE
                twinkle > 0.35f -> WHITE
                else -> if (f.night) DEEP else PALE
            }
            speck(cls, big, x, y)
        }
    }

    /** A meteor every [period] seconds of animation time, burning out in under a second. */
    fun meteor(canvas: Canvas, f: Frame, period: Float, offset: Float, top: Float, bottom: Float) {
        val cycle = ((f.t + offset) / period).toInt()
        val p = ((f.t + offset) % period) / 0.8f
        if (f.t <= 0f || p >= 1f) return
        val sx = 120f + fraction(cycle * 31 + 5) * 760f
        val sy = top + fraction(cycle * 17 + 2) * (bottom - top)
        val dir = if (fraction(cycle * 7) < 0.5f) -1f else 1f
        val travel = 260f * p
        val hx = sx + dir * travel * 0.92f
        val hy = sy + travel * 0.38f
        val life = sin(p * PI.toFloat())
        val s = k.stroke
        k.additive(s, f.night)
        s.color = if (f.night) mix(f.c1, Color.WHITE, 0.5f) else f.c1
        s.alpha = (life * 110f).toInt()
        s.strokeWidth = 2.6f
        canvas.drawLine(hx - dir * 90f * 0.92f, hy - 90f * 0.38f, hx, hy, s)
        s.color = if (f.night) Color.WHITE else f.c2
        s.alpha = (life * 220f).toInt()
        s.strokeWidth = 1.1f
        canvas.drawLine(hx - dir * 40f * 0.92f, hy - 40f * 0.38f, hx, hy, s)
        k.reset(s)
    }

    // endregion

    // region Gestures

    /**
     * The finger's wake, drawn as a streak of light that is captured into orbit as it fades:
     * older points are swung around ([cx], [cy]) on an ellipse of flatness [flat], faster
     * when closer, in direction [turn] (+1 clockwise on screen).
     */
    fun wake(canvas: Canvas, f: Frame, cx: Float, cy: Float, flat: Float, turn: Float) {
        val n = f.trail
        if (n < 2) return
        for (i in 0 until n) {
            val age = 1f - f.trailA[i]
            val dx = f.trailX[i] - cx
            val dy = (f.trailY[i] - cy) / flat
            val r = sqrt(dx * dx + dy * dy)
            val swing = turn * age * age * 1.9f * (380f / max(r, 160f)).pow(0.7f)
            val a = atan2(dy, dx) + swing
            val rr = r * (1f - age * 0.12f)
            wakeX[i] = cx + cos(a) * rr
            wakeY[i] = cy + sin(a) * rr * flat
        }
        val s = k.stroke
        k.additive(s, f.night)
        val hot = if (f.night) Color.WHITE else f.c2
        val cool = if (f.night) f.c1 else f.c1
        // A soft halo under the streak, then the hot core; color cools as the light ages.
        for (pass in 0 until 2) {
            for (i in 1 until n) {
                val a = f.trailA[i]
                if (a < 0.02f) continue
                s.color = if (pass == 0) cool else mix(cool, hot, a)
                s.alpha = if (pass == 0) (a * (if (f.night) 70f else 60f)).toInt() else (a * (if (f.night) 240f else 210f)).toInt()
                s.strokeWidth = if (pass == 0) 6f + a * 16f else 1.2f + a * 4.2f
                canvas.drawLine(wakeX[i - 1], wakeY[i - 1], wakeX[i], wakeY[i], s)
            }
        }
        if (!f.night) {
            // Second ink, misregistered, so the day wake prints like the trails.
            s.color = f.c1
            for (i in 1 until n) {
                val a = f.trailA[i]
                s.alpha = (a * 120f).toInt()
                s.strokeWidth = 1f + a * 2.5f
                canvas.drawLine(wakeX[i - 1] + 3f, wakeY[i - 1] + 2f, wakeX[i] + 3f, wakeY[i] + 2f, s)
            }
        }
        k.reset(s)
        // Sparks shed along the wake drift outward as they cool.
        for (i in 0 until n) {
            val a = f.trailA[i]
            for (j in 0 until 3) {
                val seed = i * 31 + j * 7
                val spread = (1f - a) * 46f + 4f
                val x = wakeX[i] + (fraction(seed) - 0.5f) * spread * 2f
                val y = wakeY[i] + (fraction(seed + 3) - 0.5f) * spread
                if (fraction(seed + 5) > a + 0.15f) continue
                speck(if (j == 0) WHITE else if (j == 1) EMBER else PALE, a > 0.6f, x, y)
            }
        }
        val head = n - 1
        k.glow(canvas, k.glowWhite, f.trailX[head], f.trailY[head], 40f, 40f,
            (f.trailA[head] * f.drag * 150f).toInt(), if (f.night) RenderKit.Mode.SCREEN else RenderKit.Mode.INK)
    }

    /**
     * A finger resting still gathers light: specks spiral in on the orbit plane (flatness
     * [flat]) and a small bright accretion ring tightens as [Frame.charge] builds.
     */
    fun gather(canvas: Canvas, f: Frame, flat: Float) {
        val c = f.charge
        if (f.drag < 0.02f) return
        val x0 = f.dragX
        val y0 = f.dragY
        val mode = if (f.night) RenderKit.Mode.SCREEN else RenderKit.Mode.INK
        k.glow(canvas, k.glowA, x0, y0, 70f + c * 120f, (70f + c * 120f) * 0.7f, ((0.25f + c) * 70f * f.drag).toInt(), mode)
        if (c < 0.01f) return
        k.glow(canvas, k.glowWhite, x0, y0, 16f + c * 34f, 16f + c * 34f, (c * 230f).toInt(), mode)
        val count = 64
        for (i in 0 until count) {
            val p = (f.now * (0.45f + fraction(i * 7) * 0.5f) + fraction(i * 13)) % 1f
            val r = (50f + fraction(i * 3) * 300f) * (1f - p).pow(1.6f) * (0.4f + c * 0.6f)
            val a = fraction(i * 5) * 2f * PI.toFloat() + p * 3.2f
            if (fraction(i * 11 + 1) > c * 1.2f) continue
            speck(if (p > 0.7f) WHITE else if (i % 5 == 0) EMBER else PALE, p > 0.82f,
                x0 + cos(a) * r, y0 + sin(a) * r * flat)
        }
        val s = k.stroke
        k.additive(s, f.night)
        val r = 24f + (1f - c) * 40f + sin(f.now * 7f) * 2f
        k.rect.set(x0 - r, y0 - r * flat, x0 + r, y0 + r * flat)
        s.color = if (f.night) f.c2 else f.c2
        s.alpha = (c * 150f).toInt()
        s.strokeWidth = 4f
        canvas.drawOval(k.rect, s)
        s.color = if (f.night) Color.WHITE else f.c1
        s.alpha = (c * 230f).toInt()
        s.strokeWidth = 1.4f
        canvas.drawOval(k.rect, s)
        k.reset(s)
    }

    /**
     * Each pulse as a shock ring lying in the orbit plane, with chromatic fringes. Charged
     * bursts (power above 1) add a second, trailing ring.
     */
    fun shockRings(canvas: Canvas, f: Frame, flat: Float) {
        if (f.pulses == 0) return
        val s = k.stroke
        k.additive(s, f.night)
        for (i in 0 until f.pulses) {
            val a = f.pulseA[i].coerceAtMost(1.5f)
            val rings = if (a > 1f) 2 else 1
            for (ring in 0 until rings) {
                val r = f.pulseR[i] * (1f - ring * 0.28f)
                if (r < 6f) continue
                val fade = a * (1f - ring * 0.35f)
                for (layer in 0 until 3) {
                    val rr = r + (layer - 1) * 7f
                    k.rect.set(f.pulseX[i] - rr, f.pulseY[i] - rr * flat, f.pulseX[i] + rr, f.pulseY[i] + rr * flat)
                    s.color = when (layer) {
                        0 -> f.c1
                        1 -> if (f.night) Color.WHITE else f.c1
                        else -> f.c2
                    }
                    s.alpha = (fade * if (layer == 1) 210f else 120f).toInt().coerceIn(0, 255)
                    s.strokeWidth = if (layer == 1) 2f + a * 1.5f else 3.5f
                    canvas.drawOval(k.rect, s)
                }
            }
        }
        k.reset(s)
    }

    /** How hot the scene's light source should burn right now: held charge plus fresh bursts. */
    fun heat(f: Frame): Float {
        var flash = 0f
        for (i in 0 until f.pulses) flash += f.pulseA[i] * exp(-f.pulseR[i] / 420f)
        return (f.charge * 0.8f + flash * 0.9f).coerceAtMost(1.6f)
    }

    /**
     * The little figure with a shadow along ([shadowX], [shadowY]) in ground units of its height,
     * lengthening with [heat]. A pulse nearby throws a second shadow, as if lit by the flash.
     */
    fun figure(canvas: Canvas, f: Frame, x: Float, y: Float, height: Float, shadowX: Float, shadowY: Float, heat: Float = 0f) {
        val color = if (f.night) 0xFF010204.toInt() else alpha(mix(f.c1, Color.BLACK, 0.35f), 240)
        val shade = if (f.night) 0xB0000000.toInt() else alpha(f.c1, 90)
        val s = k.stroke
        k.normal(s)
        s.shader = null
        s.color = shade
        s.strokeWidth = height * 0.09f
        val len = height * (1f + heat * 0.8f)
        canvas.drawLine(x, y, x + shadowX * len, y + shadowY * len, s)
        for (i in 0 until f.pulses) {
            val dx = x - f.pulseX[i]
            val dy = y - f.pulseY[i]
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val lit = f.pulseA[i] * exp(-((d - f.pulseR[i]) / 140f).let { it * it })
            if (lit < 0.03f) continue
            s.alpha = (lit * 200f).toInt().coerceIn(0, 255)
            s.strokeWidth = height * 0.08f
            val len = height * (1.2f + lit * 1.8f)
            canvas.drawLine(x, y, x + dx / d * len, y + dy / d * len * 0.25f, s)
        }
        k.reset(s)
        k.figure(canvas, x, y, height, color)
    }

    // endregion

    companion object {
        const val BLUE = 0
        const val PALE = 1
        const val WHITE = 2
        const val EMBER = 3
        const val RED = 4
        const val DEEP = 5
        const val CLASSES = 6
        const val BATCH = 3200
        const val LANES = 32
        const val DEG = (PI / 180.0).toFloat()

        /** Label strips of a 4x4 launcher grid, as fractions of the height (with a small margin). */
        fun inLabelRow(v: Float): Boolean {
            for (row in 0 until 4) {
                val top = 0.355f + row * 0.12f
                if (v > top && v < top + 0.035f) return true
            }
            return false
        }
    }
}
