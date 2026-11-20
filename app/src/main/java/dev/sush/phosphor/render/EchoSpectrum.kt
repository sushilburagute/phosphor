package dev.sush.phosphor.render

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** A short waterfall of logarithmic frequency bands. All accesses run on the main looper. */
internal class EchoSpectrum {
    private val rows = Array(19) { FloatArray(24) }
    private val peaks = FloatArray(rows[0].size)
    private val edges = IntArray(rows[0].size + 1)
    private var fftSize = 0
    private var sampleRate = 0
    private var newest = 0
    private var lastSound = -1L
    var blend = 0f
        private set

    fun accept(fft: ByteArray, rateMilliHz: Int, now: Long) {
        if (fft.size < 8 || rateMilliHz <= 0) return
        if (fftSize != fft.size || sampleRate != rateMilliHz) {
            fftSize = fft.size
            sampleRate = rateMilliHz
            val hz = rateMilliHz / 1000.0
            val high = minOf(16000.0, hz / 2)
            val low = minOf(45.0, high / 2)
            for (i in edges.indices) {
                val frequency = low * (high / low).pow(i.toDouble() / rows[0].size)
                edges[i] = ceil(frequency * fft.size / hz).toInt().coerceIn(1, fft.size / 2 - 1)
            }
        }
        val previous = rows[newest]
        newest = (newest + 1) % rows.size
        val row = rows[newest]
        var audible = false
        for (band in row.indices) {
            var power = 0
            // Bin zero contains DC and Nyquist. The other pairs are signed real/imaginary values.
            for (bin in edges[band] until maxOf(edges[band] + 1, edges[band + 1])) {
                val re = fft[2 * bin].toInt()
                val im = fft[2 * bin + 1].toInt()
                power = maxOf(power, re * re + im * im)
            }
            val magnitude = sqrt(power.toDouble())
            if (magnitude > 2.0) audible = true
            peaks[band] = (ln(1.0 + magnitude) / 4.85).toFloat().coerceIn(0f, 1f)
        }
        for (band in row.indices) {
            val shoulders = maxOf(peaks[maxOf(0, band - 1)], peaks[minOf(row.lastIndex, band + 1)]) * 0.55f
            row[band] = maxOf(peaks[band], shoulders, previous[band] * 0.65f)
        }
        if (audible) lastSound = now
        updateBlend(now)
    }

    fun updateBlend(now: Long) {
        blend = if (lastSound < 0L) 0f else (1f - (now - lastSound - 150L).coerceAtLeast(0L) / 450f).coerceIn(0f, 1f)
    }

    fun level(age: Int, x: Float): Float {
        val row = rows[Math.floorMod(newest - age.coerceIn(0, rows.lastIndex), rows.size)]
        val at = x.coerceIn(0f, 1f) * row.lastIndex
        val left = at.toInt()
        val fraction = at - left
        val smooth = fraction * fraction * (3f - 2f * fraction)
        return row[left] + (row[minOf(left + 1, row.lastIndex)] - row[left]) * smooth
    }
}
