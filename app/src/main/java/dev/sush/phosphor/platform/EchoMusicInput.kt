package dev.sush.phosphor.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.SystemClock
import dev.sush.phosphor.model.WallpaperSettings
import dev.sush.phosphor.render.EchoSpectrum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun WallpaperSettings.allowsMusic(profile: WallpaperPowerProfile): Boolean =
    presetId == "ascii-echo" && musicReactive && !reducedMotion && speed > 0f && !profile.energySaving

internal enum class MusicStatus { IDLE, WAITING, RECEIVING, UNAVAILABLE }

/** The editor, picker and applied wallpaper can overlap. They share one output-mix effect. */
internal class EchoMusicInput {
    private var listening = false

    fun update(context: Context, eligible: Boolean) {
        val listen = eligible && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (listen == listening) return
        if (!listen) { stop(); return }
        listening = true
        if (++listeners == 1) startCapture()
    }

    fun current(now: Long): EchoSpectrum? {
        if (!listening) return null
        val trace = spectrum ?: return null
        trace.updateBlend(now)
        state.value = if (trace.blend > 0f) MusicStatus.RECEIVING else MusicStatus.WAITING
        return trace.takeIf { it.blend > 0f }
    }

    fun stop() {
        if (!listening) return
        listening = false
        if (--listeners == 0) releaseCapture()
    }

    companion object {
        // Acquired by UI/Engine lifecycle callbacks. Visualizer delivers callbacks on its creating looper.
        private var listeners = 0
        private var effect: Visualizer? = null
        private var spectrum: EchoSpectrum? = null
        private val state = MutableStateFlow(MusicStatus.IDLE)
        val status = state.asStateFlow()

        private fun startCapture() {
            try {
                val output = Visualizer(0)
                effect = output
                if (!output.enabled) {
                    val range = Visualizer.getCaptureSizeRange()
                    check(output.setCaptureSize(1024.coerceIn(range.first(), range.last())) == Visualizer.SUCCESS)
                }
                val trace = EchoSpectrum()
                val listener = object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer, waveform: ByteArray, rate: Int) = Unit
                    override fun onFftDataCapture(v: Visualizer, fft: ByteArray, rate: Int) {
                        if (effect === v) trace.accept(fft, rate, SystemClock.uptimeMillis())
                    }
                }
                val rate = minOf(20000, Visualizer.getMaxCaptureRate())
                check(output.setDataCaptureListener(listener, rate, false, true) == Visualizer.SUCCESS)
                check(output.setEnabled(true) == Visualizer.SUCCESS)
                spectrum = trace
                state.value = MusicStatus.WAITING
            } catch (_: RuntimeException) {
                releaseCapture()
                state.value = MusicStatus.UNAVAILABLE
            }
        }

        private fun releaseCapture() {
            val output = effect
            effect = null
            spectrum = null
            state.value = MusicStatus.IDLE
            if (output != null) {
                try {
                    output.setEnabled(false)
                } catch (_: RuntimeException) {
                    // The audio service can disappear when an output route changes.
                } finally {
                    output.release()
                }
            }
        }
    }
}
