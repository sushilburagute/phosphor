package dev.sush.phosphor.render

import android.graphics.Canvas

internal class TextureScene(k: RenderKit, halftone: Halftone) {
    private val silk = TextureSilk(k, halftone)
    private val basalt = TextureBasalt(k)
    private val pixels = TexturePixelWeave()

    fun draw(canvas: Canvas, f: Frame, id: String) {
        when (id) {
            "texture-satin" -> silk.draw(canvas, f)
            "texture-pixel-weave" -> pixels.draw(canvas, f)
            else -> basalt.draw(canvas, f)
        }
    }
}
