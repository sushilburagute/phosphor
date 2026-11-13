package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import dev.sush.phosphor.model.WallpaperKind
import dev.sush.phosphor.model.WallpaperPreset
import dev.sush.phosphor.model.WallpaperSettings
import dev.sush.phosphor.render.RenderKit.Companion.alpha
import dev.sush.phosphor.render.RenderKit.Companion.fraction
import dev.sush.phosphor.render.RenderKit.Companion.hash
import dev.sush.phosphor.render.RenderKit.Companion.mix
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The renderer shared by the gallery, editor, exported stills and the WallpaperService.
 *
 * Give each surface its own instance: it caches shaders, noise, imported bitmaps and a little
 * per-scene spring state. Call [release] when that surface is destroyed. Offsets are -1..1.
 * Artwork is drawn in a 1000-unit-wide space so it is independent of screen density.
 *
 * Readability is part of the composition. Night artwork stays dark behind the clock and dock;
 * day artwork stays pale there. [LEGIBILITY_TOP] and [LEGIBILITY_BOTTOM] add a soft veil of
 * ground color in those zones; the renderer tests measure label contrast for every preset.
 */
class WallpaperRenderer {
    private val kit = RenderKit()
    private val frame = Frame()
    private val halftone = Halftone()
    private val orbit = OrbitScene(kit)
    private val aurora = AuroraScene(kit)
    private val ascii = AsciiScene(kit)
    private val gradient = GradientScene(kit, halftone)
    private val glass = GlassScene(kit)
    private val abstract = AbstractScene(kit)
    private val texture = TextureScene(kit, halftone)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private var noise: Bitmap? = null
    private var noiseShader: Shader? = null
    private var importedBitmap: Bitmap? = null
    private var importedPath: String? = null
    private var lastReal = Float.NaN
    private var veilKey = 0L
    private var veilTop: Shader? = null
    private var veilBottom: Shader? = null
    private var groundShader: Shader? = null
    private var groundColor = 0
    private var groundLift = 0
    private var groundHeight = 0f

    /** Tests measure small bitmaps at full on-device density; 0 means derive from width. */
    internal var forcedDetail = 0f

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        timeSeconds: Float,
        preset: WallpaperPreset,
        settings: WallpaperSettings,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        night: Boolean = true,
        touch: TouchField? = null,
        realSeconds: Float = Float.NaN,
        reveal: Float = 1f,
        interaction: WallpaperInteraction? = null,
        unlockReveal: Float = 1f,
    ) {
        if (width <= 0 || height <= 0) return
        val h = 1000f * height / width
        val still = settings.reducedMotion
        val f = frame
        f.h = h
        f.amount = finiteUnit(settings.intensity)
        f.t = if (still) 0f else timeSeconds.takeIf { it.isFinite() }?.times(finiteUnit(settings.speed) * 1.35f) ?: 0f
        f.night = night
        f.reveal = if (still || !settings.wakeReveal) 1f else finiteUnit(reveal)
        val awake = if (still || !settings.unlockAnimation) 1f else finiteUnit(unlockReveal)
        // Thumbnails draw fewer elements; the composition is identical.
        f.detail = if (forcedDetail > 0f) forcedDetail else (width / 1080f).coerceIn(0.35f, 1f)
        f.offsetX = if (settings.parallax && !still) safeOffset(offsetX) else 0f
        f.offsetY = if (settings.parallax && !still) safeOffset(offsetY) else 0f
        val palette = preset.palette(night)
        f.c0 = palette.getOrElse(0) { if (night) Color.BLACK else Color.WHITE }
        f.c1 = palette.getOrElse(1) { 0xFF92D9CE.toInt() }
        f.c2 = palette.getOrElse(2) { 0xFFC2A2E2.toInt() }
        val real = realSeconds.takeIf { it.isFinite() }
        f.now = real ?: 0f
        f.dt = if (real != null && !lastReal.isNaN()) (real - lastReal).coerceIn(0f, 0.1f) else 0f
        lastReal = real ?: Float.NaN
        if (touch != null && real != null && !still && settings.parallax) {
            touch.advance(real)
            touch.fill(f, real)
        } else {
            f.pulses = 0
            f.drag = 0f
        }
        // Damped dragging drives every scene; independent tap pulses still handle
        // launcher commands and overlapping gestures.
        if (!still && settings.parallax && interaction != null) {
            f.offsetX = safeOffset(f.offsetX + safeOffset(interaction.offsetX))
            f.offsetY = safeOffset(f.offsetY + safeOffset(interaction.offsetY))
            if (touch == null) {
                f.dragX = finiteUnit(interaction.x) * 1000f
                f.dragY = finiteUnit(interaction.y) * h
                f.drag = finiteUnit(interaction.strength)
                val progress = finiteUnit(interaction.rippleProgress)
                if (progress < 1f) {
                    f.pulses = 1
                    f.pulseX[0] = f.dragX
                    f.pulseY[0] = f.dragY
                    f.pulseR[0] = progress * 1150f
                    f.pulseA[0] = (1f - progress) * (1f - progress)
                }
            } else {
                f.drag = finiteUnit(interaction.strength)
            }
        }
        kit.prepare(f)

        val saved = canvas.save()
        canvas.scale(width / 1000f, width / 1000f)
        canvas.clipRect(0f, 0f, 1000f, h)
        paint.shader = null
        paint.xfermode = null
        paint.alpha = 255
        paint.color = f.c0
        canvas.drawRect(0f, 0f, 1000f, h, paint)
        ground(canvas, f)

        val artworkSave = canvas.save()
        val zoom = 1f + (1f - awake) * 0.055f +
            if (!still && settings.parallax && interaction != null) finiteUnit(interaction.strength) * 0.018f else 0f
        canvas.scale(zoom, zoom, 500f, h * 0.5f)
        when (preset.kind) {
            WallpaperKind.ORBIT -> orbit.draw(canvas, f, preset.id)
            WallpaperKind.AURORA -> aurora.draw(canvas, f)
            WallpaperKind.ASCII -> ascii.draw(canvas, f, preset.id == "ascii-terminal")
            WallpaperKind.GRADIENT -> gradient.draw(canvas, f)
            WallpaperKind.GLASS -> glass.draw(canvas, f, preset.id == "glass-orbit")
            WallpaperKind.ABSTRACT -> abstract.draw(canvas, f, preset.id == "abstract-orbit")
            WallpaperKind.TEXTURE -> texture.draw(canvas, f, preset.id == "texture-basalt")
            WallpaperKind.IMAGE -> drawImage(canvas, f, settings)
        }
        canvas.restoreToCount(artworkSave)

        veil(canvas, f, preset.kind == WallpaperKind.IMAGE)
        if (preset.kind != WallpaperKind.IMAGE) {
            drawNoise(canvas, h, if (night) 15 else 22, preset.kind == WallpaperKind.TEXTURE)
        }
        // "Calm" fades toward the ground: darker at night, paler by day. Labels only gain contrast.
        paint.shader = null
        paint.color = if (night) Color.BLACK else f.c0
        paint.alpha = max(finiteUnit(settings.darken) * if (night) 210f else 190f, (1f - awake) * 78f).toInt()
        canvas.drawRect(0f, 0f, 1000f, h, paint)
        if (f.reveal < 1f) {
            paint.color = if (night) Color.BLACK else f.c0
            val v = 1f - RenderKit.smooth(f.reveal)
            paint.alpha = (v * v * 255f).toInt()
            canvas.drawRect(0f, 0f, 1000f, h, paint)
        }
        paint.alpha = 255
        canvas.restoreToCount(saved)
    }

    /** A quiet base gradient so the ground is never a flat fill. */
    private fun ground(canvas: Canvas, f: Frame) {
        val lift = if (f.night) mix(f.c0, f.c1, 0.07f) else mix(f.c0, Color.WHITE, 0.5f)
        if (groundShader == null || groundColor != f.c0 || groundLift != lift || groundHeight != f.h) {
            groundColor = f.c0
            groundLift = lift
            groundHeight = f.h
            groundShader = LinearGradient(0f, 0f, 1000f, f.h, intArrayOf(f.c0, lift, f.c0), null, Shader.TileMode.CLAMP)
        }
        paint.shader = groundShader
        canvas.drawRect(0f, 0f, 1000f, f.h, paint)
        paint.shader = null
    }

    /** Ground-colored veils behind the clock/status area and the dock. */
    private fun veil(canvas: Canvas, f: Frame, image: Boolean) {
        val strength = if (image) 1.15f else 1f
        val key = (f.c0.toLong() shl 8) + (f.h.toLong() shl 2) + if (image) 1 else 0
        if (key != veilKey || veilTop == null) {
            veilKey = key
            val base = if (f.night) mix(f.c0, Color.BLACK, 0.4f) else f.c0
            veilTop = LinearGradient(0f, 0f, 0f, f.h * 0.33f,
                intArrayOf(alpha(base, (LEGIBILITY_TOP * 255 * strength).toInt()), alpha(base, (LEGIBILITY_TOP * 140 * strength).toInt()), alpha(base, 0)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            veilBottom = LinearGradient(0f, f.h * 0.76f, 0f, f.h,
                intArrayOf(alpha(base, 0), alpha(base, (LEGIBILITY_BOTTOM * 160 * strength).toInt()), alpha(base, (LEGIBILITY_BOTTOM * 255 * strength).toInt())),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        }
        paint.shader = veilTop
        canvas.drawRect(0f, 0f, 1000f, f.h * 0.33f, paint)
        paint.shader = veilBottom
        canvas.drawRect(0f, f.h * 0.76f, 1000f, f.h, paint)
        paint.shader = null
    }

    private fun drawImage(canvas: Canvas, f: Frame, settings: WallpaperSettings) {
        loadImage(settings.imagePath)
        val bitmap = importedBitmap
        if (bitmap == null) {
            gradient.draw(canvas, f)
            return
        }
        val motion = if (settings.reducedMotion) 0f else f.amount
        val h = f.h
        // Reserve overscan in both axes, including landscape and foldables,
        // for the combined image drift and surface parallax.
        val margin = 128f + motion * 96f + if (settings.parallax) 70f else 0f
        val scale = max((1000f + margin) / bitmap.width, (h + margin) / bitmap.height)
        val bw = bitmap.width * scale
        val bh = bitmap.height * scale
        val driftX = sin(f.t * 0.4f) * 32f * motion + f.shiftX(1f)
        val driftY = cos(f.t * 0.31f) * 38f * motion + f.shiftY(1f)
        val left = (1000f - bw) * 0.5f + driftX
        val top = (h - bh) * 0.5f + driftY
        rect.set(left, top, left + bw, top + bh)
        paint.shader = null
        paint.alpha = 255
        canvas.drawBitmap(bitmap, null, rect, paint)
        // Independent foreground motes create depth without pretending a flat image
        // has been segmented into separate objects.
        motes(canvas, f, 36)
        ripples(canvas, f, kit)
    }

    private fun motes(canvas: Canvas, f: Frame, count: Int) {
        paint.shader = null
        for (i in 0 until count) {
            val x0 = fraction(i * 17 + 101) * 1100f - 50f
            val y0 = fraction(i * 31 + 503) * (f.h + 100f) - 50f
            val x = x0 + sin(f.t * 0.33f + i * 0.83f) * 48f * f.amount + f.shiftX(1.6f)
            val y = ((y0 + 70f - f.t * 14f * f.amount) % (f.h + 140f) + f.h + 140f) % (f.h + 140f) - 70f +
                cos(f.t * 0.27f + i * 1.37f) * 45f * f.amount
            paint.color = mix(f.c1, Color.WHITE, fraction(i + 200))
            paint.alpha = ((75f + fraction(i + 430) * 140f) * 0.35f + f.wave(x, y) * 120f).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, 0.8f + fraction(i + 810) * 2.4f, paint)
        }
        paint.alpha = 255
    }

    private fun loadImage(path: String?) {
        if (path == importedPath) return
        importedBitmap?.recycle()
        importedBitmap = null
        importedPath = path
        if (path.isNullOrBlank()) return
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
            importedBitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } catch (_: RuntimeException) {
            // Corrupt or removed private imports show the procedural fallback.
        } catch (_: OutOfMemoryError) {
            // Keep the wallpaper service alive even under system memory pressure.
        }
    }

    private fun ensureNoise() {
        if (noise != null) return
        val pixels = IntArray(160 * 160)
        for (i in pixels.indices) {
            val shade = hash(i + 1847) and 255
            pixels[i] = if (shade > 127) alpha(Color.WHITE, (shade - 127) * 2) else alpha(Color.BLACK, (127 - shade) * 2)
        }
        val bitmap = Bitmap.createBitmap(pixels, 160, 160, Bitmap.Config.ARGB_8888)
        noise = bitmap
        noiseShader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(1.35f, 1.35f) })
        }
    }

    // Fine grain ties the procedural artwork together without animated noise shimmer.
    private fun drawNoise(canvas: Canvas, h: Float, opacity: Int, heavy: Boolean) {
        ensureNoise()
        paint.shader = noiseShader
        paint.alpha = if (heavy) opacity * 2 else opacity
        canvas.drawRect(0f, 0f, 1000f, h, paint)
        paint.alpha = 255
        paint.shader = null
    }

    fun release() {
        importedBitmap?.recycle()
        importedBitmap = null
        importedPath = null
        noise?.recycle()
        noise = null
        noiseShader = null
        halftone.release()
        gradient.release()
        glass.release()
        kit.release()
        veilTop = null
        veilBottom = null
        veilKey = 0
        groundShader = null
        lastReal = Float.NaN
    }

    private fun finiteUnit(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    private fun safeOffset(value: Float): Float = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f

    companion object {
        /** Peak veil opacity behind the status bar and clock. */
        const val LEGIBILITY_TOP = 0.5f
        /** Peak veil opacity behind the dock and navigation area. */
        const val LEGIBILITY_BOTTOM = 0.55f
    }
}
