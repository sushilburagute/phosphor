package dev.sush.phosphor.render

import dev.sush.phosphor.render.RenderKit.Companion.fraction
import kotlin.math.floor

/** Seeded, smoothly interpolated noise for the cached cloud layers in the orbit scenes. */
internal class SpaceNoise(seed: Int) {
    private val values = FloatArray(64 * 64) { fraction(seed + it * 97) }

    fun sample(x: Float, y: Float): Float {
        val left = floor(x).toInt()
        val top = floor(y).toInt()
        val u = interpolate(x - left)
        val v = interpolate(y - top)
        val a = lerp(at(left, top), at(left + 1, top), u)
        val b = lerp(at(left, top + 1), at(left + 1, top + 1), u)
        return lerp(a, b, v)
    }

    fun fractal(x: Float, y: Float): Float =
        sample(x, y) * 0.52f +
            sample(x * 2.03f + 17f, y * 2.03f + 7f) * 0.25f +
            sample(x * 4.13f - 7f, y * 4.13f + 19f) * 0.13f +
            sample(x * 8.17f + 21f, y * 8.17f - 5f) * 0.07f +
            sample(x * 16.3f - 13f, y * 16.3f + 23f) * 0.03f

    private fun at(x: Int, y: Int) = values[(y and 63) * 64 + (x and 63)]
    private fun interpolate(value: Float) = value * value * (3f - 2f * value)
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
