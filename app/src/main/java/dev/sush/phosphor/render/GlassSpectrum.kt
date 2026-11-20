package dev.sush.phosphor.render

import android.graphics.Canvas
import android.graphics.Color
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.mix
import kotlin.math.cos
import kotlin.math.sin

/** A triangular glass section, its rear face offset as the prism turns. */
internal fun drawSpectrum(canvas: Canvas, f: Frame, k: RenderKit) {
    val cy = f.h * 0.56f + f.shiftY(0.75f)
    val cx = 465f + f.shiftX(0.75f)
    val turn = sin(f.t * 0.32f) * f.amount
    val size = minOf(205f, f.h * 0.24f)
    val left = cx - size * (0.92f - turn * 0.15f)
    val right = cx + size
    val base = cy + size * 0.58f
    val apex = cx + turn * size * 0.30f
    val top = cy - size
    val depth = size * (0.20f + 0.10f * cos(f.t * 0.32f) * f.amount)
    val exit = right - size * 0.35f
    val tilt = sin(f.t * 0.37f) * 0.12f * f.amount
    val spread = 0.62f + f.composition * 0.78f
    val far = 1150f + f.shiftX(1.4f)
    val distance = far - exit

    k.glow(canvas, k.glowA, cx, cy, size * 2.9f, size * 1.8f, 72, RenderKit.Mode.SCREEN)
    val paint = k.fill
    val path = k.path
    // The short and long wavelengths pick up the scene accents. Green and gold
    // remain visible at the center of the fan as the prism turns.
    val violet = mix(f.c1, 0xFF7855ED.toInt(), 0.62f)
    val green = mix(f.c1, 0xFF75E5A6.toInt(), 0.64f)
    val gold = mix(f.c2, 0xFFFFD666.toInt(), 0.72f)
    for (band in 0 until 24) {
        val u = band / 24f
        val v = (band + 1) / 24f
        path.rewind()
        path.moveTo(exit, cy + 8f * u)
        path.lineTo(far, cy + distance * (tilt + (u - 0.5f) * spread))
        path.lineTo(far, cy + distance * (tilt + (v - 0.5f) * spread))
        path.close()
        val color = when {
            u < 0.25f -> mix(violet, f.c1, u * 4f)
            u < 0.5f -> mix(f.c1, green, (u - 0.25f) * 4f)
            u < 0.75f -> mix(green, gold, (u - 0.5f) * 4f)
            else -> mix(gold, f.c2, (u - 0.75f) * 4f)
        }
        paint.color = alpha(color, (93f + 24f * sin(u * 3.14f)).toInt())
        canvas.drawPath(path, paint)
    }
    path.rewind()
    path.moveTo(apex, top)
    path.lineTo(apex + depth, top - depth * 0.32f)
    path.lineTo(right + depth, base - depth * 0.32f)
    path.lineTo(right, base)
    path.close()
    paint.color = alpha(f.c2, 72)
    canvas.drawPath(path, paint)
    path.rewind()
    path.moveTo(apex, top)
    path.lineTo(left, base)
    path.lineTo(right, base)
    path.close()
    paint.color = alpha(f.c1, 41)
    canvas.drawPath(path, paint)
    val rim = k.stroke
    rim.color = alpha(mix(f.c1, Color.WHITE, 0.45f), 180)
    rim.strokeWidth = 2.6f
    canvas.drawPath(path, rim)
    val entry = left + (apex - left) * 0.35f
    canvas.drawLine(-50f, cy + size * 0.36f, entry, cy + size * 0.027f, rim)
    canvas.drawLine(entry, cy + size * 0.027f, exit, cy, rim)
    k.glow(canvas, k.glowWhite, exit, cy, 36f, 12f, 145, RenderKit.Mode.SCREEN)
    k.reset(rim)
    k.reset(paint)
}
