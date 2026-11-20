package dev.sush.phosphor.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoSpectrumTest {
    @Test fun lowAndHighTonesProduceDifferentPeaksAndKeepTheirHistory() {
        val spectrum = EchoSpectrum()
        val fft = ByteArray(1024)
        fft[2 * 3] = 100 // ~141 Hz at 48 kHz
        spectrum.accept(fft, 48000000, 0L)
        fun peak(row: Int) = (0..100).maxBy { spectrum.level(row, it / 100f) }
        val bass = peak(0)
        assertTrue(bass in 0..30)
        assertEquals(1f, spectrum.blend)
        fft.fill(0)
        fft[2 * 68 + 1] = -100 // ~3188 Hz in the imaginary channel
        spectrum.accept(fft, 48000000, 50L)
        assertTrue(peak(0) in 65..85)
        assertEquals(bass, peak(1))
    }

    @Test fun silenceAndMissingCallbacksReturnToAmbient() {
        val spectrum = EchoSpectrum()
        val fft = ByteArray(256)
        fft[0] = 127
        fft[1] = -127
        spectrum.accept(fft, 44100000, 0L)
        assertEquals(0f, spectrum.blend)
        fft[4] = 90
        spectrum.accept(fft, 44100000, 50L)
        fft.fill(0)
        spectrum.accept(fft, 44100000, 350L)
        assertTrue(spectrum.blend in 0.1f..0.9f)
        spectrum.updateBlend(1000L)
        assertEquals(0f, spectrum.blend)
    }
}
