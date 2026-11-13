package dev.sush.phosphor.render

import android.graphics.Canvas

/** The texture family: lit rose silk under a halftone screen, or an etched basalt relief map. */
internal class TextureScene(k: RenderKit, halftone: Halftone) {
    private val silk = TextureSilk(k, halftone)
    private val basalt = TextureBasalt(k)

    fun draw(canvas: Canvas, f: Frame, basalt: Boolean) {
        if (basalt) this.basalt.draw(canvas, f) else silk.draw(canvas, f)
    }
}
