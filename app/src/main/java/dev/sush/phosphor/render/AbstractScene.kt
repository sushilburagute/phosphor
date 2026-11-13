package dev.sush.phosphor.render

import android.graphics.Canvas

/** The abstract family: a gravity well of filaments, or a sculptural chromatic band. */
internal class AbstractScene(k: RenderKit) {
    private val horizon = AbstractHorizon(k)
    private val band = AbstractLoop(k)

    fun draw(canvas: Canvas, f: Frame, loop: Boolean) {
        if (loop) band.draw(canvas, f) else horizon.draw(canvas, f)
    }
}
