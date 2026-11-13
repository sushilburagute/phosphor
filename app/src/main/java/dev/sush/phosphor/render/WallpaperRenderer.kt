package dev.sush.phosphor.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import dev.sush.phosphor.model.WallpaperKind
import dev.sush.phosphor.model.WallpaperPreset
import dev.sush.phosphor.model.WallpaperSettings
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A software-Canvas renderer shared by the gallery, editor and WallpaperService.
 *
 * Give each surface its own instance. The renderer caches shaders, noise and imported
 * bitmaps; call [release] when that surface is destroyed. Offsets are normalized -1..1.
 * The artwork uses a 1000-unit-wide space so it is independent of screen density.
 */
class WallpaperRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val rect = RectF()
    private val shaderMatrix = Matrix()
    private var cachedPreset: WallpaperPreset? = null
    private var cachedHeight = 0f
    private var background: Shader? = null
    private var glowA: Shader? = null
    private var glowB: Shader? = null
    private var glowWhite: Shader? = null
    private var ribbon: Shader? = null
    private var lens: Shader? = null
    private var noise: Bitmap? = null
    private var noiseShader: Shader? = null
    private var halftone: Bitmap? = null
    private var halftoneShader: Shader? = null
    private var importedBitmap: Bitmap? = null
    private var importedPath: String? = null
    private var c0 = Color.BLACK
    private var c1 = Color.WHITE
    private var c2 = Color.GRAY
    private val glyphs = arrayOf(".", ":", "+", "*", "#", "%", "@")

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        timeSeconds: Float,
        preset: WallpaperPreset,
        settings: WallpaperSettings,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        bottomInsetPixels: Int = 0,
    ) {
        if (width <= 0 || height <= 0) return
        val h = 1000f * height / width
        val amount = finiteUnit(settings.intensity)
        val t = if (settings.reducedMotion) 0f else
            timeSeconds.takeIf { it.isFinite() }?.times(finiteUnit(settings.speed) * 0.45f) ?: 0f
        ensureShaders(preset, h)
        val saved = canvas.save()
        canvas.scale(width / 1000f, width / 1000f)
        canvas.clipRect(0f, 0f, 1000f, h)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.shader = background
        canvas.drawRect(0f, 0f, 1000f, h, paint)

        val artworkSave = canvas.save()
        if (settings.parallax && !settings.reducedMotion) {
            canvas.translate(safeOffset(offsetX) * 24f * amount, safeOffset(offsetY) * 24f * amount)
        }
        when (preset.kind) {
            WallpaperKind.AURORA -> drawAurora(canvas, h, t, amount)
            WallpaperKind.ASCII -> drawAscii(canvas, h, t, amount, preset.id == "ascii-terminal")
            WallpaperKind.GRADIENT -> drawGradient(canvas, h, t, amount)
            WallpaperKind.GLASS -> drawGlass(canvas, h, t, amount, preset.id == "glass-orbit")
            WallpaperKind.ABSTRACT -> drawAbstract(canvas, h, t, amount, preset.id == "abstract-orbit")
            WallpaperKind.TEXTURE -> drawTexture(canvas, h, t, amount, preset.id == "texture-basalt")
            WallpaperKind.IMAGE -> drawImage(canvas, h, t, amount, settings)
        }
        canvas.restoreToCount(artworkSave)

        // Fine grain ties the procedural artwork together without animated noise shimmer.
        if (preset.kind != WallpaperKind.IMAGE) drawNoise(canvas, h, if (preset.kind == WallpaperKind.TEXTURE) 42 else 15)
        paint.shader = null
        paint.color = Color.BLACK
        paint.alpha = (finiteUnit(settings.darken) * 210f).toInt()
        canvas.drawRect(0f, 0f, 1000f, h, paint)
        paint.alpha = 255
        drawWatermark(canvas, h, bottomInsetPixels.coerceAtLeast(0) * 1000f / width)
        canvas.restoreToCount(saved)
    }

    private fun ensureShaders(preset: WallpaperPreset, h: Float) {
        if (preset == cachedPreset && h == cachedHeight) return
        cachedPreset = preset
        cachedHeight = h
        c0 = preset.colors.getOrElse(0) { 0xFF091A28.toInt() }
        c1 = preset.colors.getOrElse(1) { 0xFF92D9CE.toInt() }
        c2 = preset.colors.getOrElse(2) { 0xFFC2A2E2.toInt() }
        background = LinearGradient(0f, 0f, 1000f, h, intArrayOf(c0, mix(c0, c1, 0.15f), c0), null, Shader.TileMode.CLAMP)
        glowA = RadialGradient(0f, 0f, 1f,
            intArrayOf(alpha(c1, 245), alpha(c1, 125), alpha(c1, 0)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        glowB = RadialGradient(0f, 0f, 1f,
            intArrayOf(alpha(c2, 245), alpha(c2, 110), alpha(c2, 0)), floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
        glowWhite = RadialGradient(0f, 0f, 1f,
            intArrayOf(0xCCFFFFFF.toInt(), 0x44FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        ribbon = LinearGradient(-350f, 0f, 350f, 0f,
            intArrayOf(alpha(c1, 0), alpha(c1, 130), alpha(c2, 210), alpha(c1, 0)),
            floatArrayOf(0f, 0.38f, 0.62f, 1f), Shader.TileMode.CLAMP)
        lens = RadialGradient(-0.28f, -0.38f, 1.4f,
            intArrayOf(0x66FFFFFF, alpha(c1, 14), alpha(c2, 55), 0xAAFFFFFF.toInt()),
            floatArrayOf(0f, 0.48f, 0.8f, 1f), Shader.TileMode.CLAMP)
    }

    private fun drawAurora(canvas: Canvas, h: Float, t: Float, amount: Float) {
        glow(canvas, glowB, 720f + sin(t * 0.53f) * 90f * amount, h * 0.32f, 630f, h * 0.45f, 105)
        glow(canvas, glowA, 160f + cos(t * 0.6f) * 120f * amount, h * 0.65f, 650f, h * 0.5f, 100)
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.shader = null
        // A bundle of individually flowing filaments gives the curtains real depth.
        for (band in 0 until 3) {
            for (strand in 0 until 48) {
                val u = strand / 47f
                path.reset()
                for (step in 0..36) {
                    val x = -100f + step * 1200f / 36f
                    val phase = x * 0.0048f + band * 1.7f
                    val y = h * (0.24f + band * 0.23f) +
                        sin(phase + t * 0.75f) * 145f * (0.7f + amount * 0.3f) +
                        cos(x * 0.009f - t * 0.4f + band) * 36f * amount +
                        (u - 0.5f) * (125f + 65f * sin(phase * 0.7f + t * 0.3f))
                    if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                linePaint.color = mix(c1, c2, (band / 2f * 0.65f + u * 0.35f))
                linePaint.alpha = (10f + 55f * sin(u * Math.PI).toFloat()).toInt()
                linePaint.strokeWidth = 5f + u * 3f
                canvas.drawPath(path, linePaint)
            }
        }
        particles(canvas, h, t, amount, 40, 0.45f)
        linePaint.alpha = 255
    }

    private fun drawAscii(canvas: Canvas, h: Float, t: Float, amount: Float, terminal: Boolean) {
        val centerX = if (terminal) 480f else 560f
        val wave = sin(t * 0.6f) * 85f * amount
        glow(canvas, glowA, centerX + wave, h * 0.47f, 530f, h * 0.52f, 85)
        glow(canvas, glowB, centerX - 160f, h * 0.65f, 400f, h * 0.32f, 90)
        glyphPaint.shader = null
        glyphPaint.textSize = 19f
        glyphPaint.textAlign = Paint.Align.CENTER
        val rows = (h / 23f).toInt() + 2
        for (row in 0..rows) {
            val y = row * 23f
            for (column in 0..44) {
                val x = column * 24f - 28f
                val center = if (terminal) 500f + sin(y * 0.005f + t * 0.5f) * 190f else
                    500f + (h * 0.5f - y) * 0.46f + sin(y * 0.006f + t) * 55f * amount
                val distance = abs(x - center - wave)
                val envelope = (1f - distance / (if (terminal) 360f else 270f)).coerceIn(0f, 1f)
                val flow = sin(row * 0.19f + column * 0.38f - t * 1.6f)
                val luminance = envelope * envelope * (0.57f + (flow + 1f) * 0.2f)
                if (luminance < 0.025f) continue
                val index = ((luminance * 6f).toInt() + ((hash(row * 71 + column) ushr 4) % 2)).coerceIn(0, 6)
                glyphPaint.color = mix(c1, c2, ((x - center) / 270f + 0.5f).coerceIn(0f, 1f))
                if (luminance > 0.75f) glyphPaint.color = mix(glyphPaint.color, Color.WHITE, (luminance - 0.75f) * 3f)
                glyphPaint.alpha = (luminance * 240f).toInt().coerceIn(0, 255)
                canvas.drawText(glyphs[index], x, y, glyphPaint)
            }
        }
        glyphPaint.alpha = 255
    }

    private fun drawGradient(canvas: Canvas, h: Float, t: Float, amount: Float) {
        glow(canvas, glowA, 240f + sin(t * 0.7f) * 240f * amount,
            h * 0.36f + cos(t * 0.63f) * h * 0.12f * amount, 1100f, h * 0.78f, 255)
        glow(canvas, glowB, 820f + cos(t * 0.45f) * 200f * amount,
            h * 0.81f + sin(t * 0.6f) * h * 0.13f * amount, 1100f, h * 0.7f, 255)
        glow(canvas, glowB, -140f + sin(t * 0.35f) * 200f * amount, -h * 0.1f, 980f, h * 0.55f, 175)
        glow(canvas, glowWhite, 470f + sin(t * 0.5f) * 170f * amount,
            h * 0.63f, 730f, h * 0.28f, 60)
    }

    private fun drawGlass(canvas: Canvas, h: Float, t: Float, amount: Float, spheres: Boolean) {
        glow(canvas, glowB, 830f, h * 0.29f, 820f, h * 0.51f, 160)
        glow(canvas, glowA, 70f, h * 0.78f, 710f, h * 0.44f, 180)
        if (spheres) {
            drawLens(canvas, 310f + sin(t * 0.6f) * 70f * amount, h * 0.30f + cos(t * 0.5f) * 45f * amount, 310f)
            drawLens(canvas, 760f + cos(t * 0.4f) * 60f * amount, h * 0.61f + sin(t * 0.5f) * 75f * amount, 410f)
            drawLens(canvas, 190f + sin(t * 0.7f) * 45f * amount, h * 0.85f, 195f)
        } else {
            val saved = canvas.save()
            canvas.translate(500f, h * 0.5f)
            canvas.rotate(-24f + sin(t * 0.32f) * 4f * amount)
            paint.shader = ribbon
            paint.alpha = 220
            rect.set(-370f, -h, 370f, h)
            canvas.drawRoundRect(rect, 330f, 330f, paint)
            // Off-center bright edges and dark seams read like a fluted glass prism.
            for (index in 0 until 24) {
                val x = -360f + index * 31f
                val bend = sin(index * 0.25f + t * 0.5f) * 60f * amount
                path.reset()
                path.moveTo(x, -h)
                path.cubicTo(x - 160f + bend, -h * 0.35f, x + 180f - bend, h * 0.35f, x, h)
                linePaint.shader = null
                linePaint.style = Paint.Style.STROKE
                linePaint.strokeWidth = 17f
                linePaint.color = alpha(c0, 55)
                canvas.drawPath(path, linePaint)
                linePaint.strokeWidth = 3.8f
                linePaint.color = alpha(mix(c1, Color.WHITE, 0.6f), 140)
                canvas.drawPath(path, linePaint)
                canvas.translate(3f, 0f)
                linePaint.strokeWidth = 1.4f
                linePaint.color = alpha(c2, 210)
                canvas.drawPath(path, linePaint)
                canvas.translate(-3f, 0f)
            }
            canvas.restoreToCount(saved)
            glow(canvas, glowWhite, 420f, h * 0.35f, 450f, 160f, 70)
        }
        paint.alpha = 255
    }

    private fun drawLens(canvas: Canvas, x: Float, y: Float, radius: Float) {
        val saved = canvas.save()
        canvas.translate(x, y)
        canvas.scale(radius, radius)
        paint.shader = lens
        paint.alpha = 255
        canvas.drawCircle(0f, 0f, 1f, paint)
        linePaint.shader = null
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 0.006f
        linePaint.color = 0x66FFFFFF
        canvas.drawCircle(0f, 0f, 0.995f, linePaint)
        linePaint.strokeWidth = 0.012f
        linePaint.color = alpha(c2, 155)
        rect.set(-0.985f, -0.985f, 0.985f, 0.985f)
        canvas.drawArc(rect, 25f, 100f, false, linePaint)
        linePaint.color = 0xB3FFFFFF.toInt()
        linePaint.strokeWidth = 0.009f
        canvas.drawArc(rect, 190f, 75f, false, linePaint)
        glow(canvas, glowWhite, -0.33f, -0.55f, 0.46f, 0.16f, 100)
        canvas.restoreToCount(saved)
    }

    private fun drawAbstract(canvas: Canvas, h: Float, t: Float, amount: Float, loop: Boolean) {
        glow(canvas, glowA, 140f, h * 0.65f, 650f, h * 0.45f, 75)
        glow(canvas, glowB, 850f, h * 0.25f, 530f, h * 0.25f, 50)
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.shader = null
        val saved = canvas.save()
        if (loop) {
            canvas.translate(500f, h * 0.5f)
            canvas.rotate(-28f + sin(t * 0.25f) * 5f * amount)
            for (index in 0 until 100) {
                val u = index / 99f
                val r = 190f + u * 240f
                path.reset()
                for (step in 0..100) {
                    val angle = step / 100f * (Math.PI * 2).toFloat()
                    val ripple = sin(angle * 3f + t * 0.5f) * 25f * amount
                    val x = cos(angle) * (r + ripple)
                    val y = sin(angle) * (r * 1.6f + ripple)
                    if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                linePaint.strokeWidth = 3.2f
                linePaint.color = mix(c1, c2, u)
                linePaint.alpha = (45f + sin(u * Math.PI).toFloat() * 165f).toInt()
                canvas.drawPath(path, linePaint)
            }
        } else {
            for (index in 0 until 130) {
                val u = index / 129f
                val drift = sin(t * 0.6f + u * 3f) * 65f * amount
                path.reset()
                path.moveTo(-180f, h * (0.12f + u * 0.78f))
                path.cubicTo(1350f + drift, h * (0.01f + u * 0.2f),
                    -540f - drift, h * (0.87f - u * 0.1f), 1180f, h * (0.69f + u * 0.3f))
                linePaint.strokeWidth = if (index % 23 == 0) 3.5f else 1.7f
                linePaint.color = if (index % 23 < 2) c2 else mix(c0, c1, 0.5f + u * 0.5f)
                linePaint.alpha = if (index % 23 < 2) 240 else 110 + index % 60
                canvas.drawPath(path, linePaint)
            }
        }
        canvas.restoreToCount(saved)
        linePaint.alpha = 255
        particles(canvas, h, t, amount, 70, 0.65f)
    }

    private fun drawTexture(canvas: Canvas, h: Float, t: Float, amount: Float, basalt: Boolean) {
        glow(canvas, glowB, 800f, h * 0.18f, 1100f, h * 0.75f, 210)
        for (band in 0 until 5) {
            val drift = sin(t * 0.42f + band * 0.7f) * 55f * amount
            val y = h * (-0.05f + band * 0.28f)
            path.reset()
            path.moveTo(-120f, y + 130f)
            path.cubicTo(240f + drift, y - 410f, 480f - drift, y + 470f, 1120f, y - 60f)
            path.lineTo(1120f, h + 150f)
            path.lineTo(-120f, h + 150f)
            path.close()
            paint.shader = null
            paint.alpha = 255
            paint.color = mix(c0, c1, 0.22f + band * 0.12f)
            canvas.drawPath(path, paint)
            val saved = canvas.save()
            canvas.clipPath(path)
            glow(canvas, glowB, 740f - band * 150f + drift, y + 200f, 750f, 340f, 235)
            glow(canvas, glowA, 100f + band * 130f, y + 430f, 680f, 370f, 110)
            canvas.restoreToCount(saved)
        }
        if (basalt) {
            linePaint.shader = null
            linePaint.style = Paint.Style.STROKE
            linePaint.strokeWidth = 1.2f
            linePaint.color = alpha(c2, 45)
            for (i in 0 until (h / 14f).toInt()) {
                val y = i * 14f
                path.reset()
                path.moveTo(-30f, y)
                path.cubicTo(260f, y - 90f, 680f, y + 90f, 1030f, y - 30f)
                canvas.drawPath(path, linePaint)
            }
        } else {
            ensureHalftone()
            paint.shader = halftoneShader
            paint.alpha = 75
            canvas.drawRect(0f, 0f, 1000f, h, paint)
            paint.alpha = 255
            paint.shader = null
        }
    }

    private fun drawImage(canvas: Canvas, h: Float, t: Float, amount: Float, settings: WallpaperSettings) {
        loadImage(settings.imagePath)
        val bitmap = importedBitmap
        if (bitmap == null) {
            drawGradient(canvas, h, t, amount)
            return
        }
        val motion = if (settings.reducedMotion) 0f else amount
        // Reserve enough overscan in both axes, including landscape and foldables,
        // for the combined image drift and surface parallax.
        val margin = 64f + motion * 48f
        val scale = max((1000f + margin) / bitmap.width, (h + margin) / bitmap.height)
        val bw = bitmap.width * scale
        val bh = bitmap.height * scale
        val driftX = sin(t * 0.4f) * 22f * motion
        val driftY = cos(t * 0.31f) * 24f * motion
        val left = (1000f - bw) * 0.5f + driftX
        val top = (h - bh) * 0.5f + driftY
        rect.set(left, top, left + bw, top + bh)
        paint.shader = null
        paint.alpha = 255
        canvas.drawBitmap(bitmap, null, rect, paint)
        // Independent foreground motes create depth without pretending a flat image
        // has been segmented into separate objects.
        particles(canvas, h, t, amount, 32, 0.35f)
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

    private fun particles(canvas: Canvas, h: Float, t: Float, amount: Float, count: Int, opacity: Float) {
        paint.shader = null
        for (i in 0 until count) {
            val x0 = fraction(i * 17 + 101) * 1100f - 50f
            val y0 = fraction(i * 31 + 503) * (h + 100f) - 50f
            val x = x0 + sin(t * 0.33f + i * 0.83f) * 25f * amount
            val y = y0 + cos(t * 0.27f + i * 1.37f) * 30f * amount
            paint.color = mix(c1, Color.WHITE, fraction(i + 200))
            paint.alpha = ((75f + fraction(i + 430) * 140f) * opacity).toInt()
            canvas.drawCircle(x, y, 0.8f + fraction(i + 810) * 2.4f, paint)
        }
        paint.alpha = 255
    }

    private fun glow(canvas: Canvas, shader: Shader?, x: Float, y: Float, rx: Float, ry: Float, opacity: Int) {
        val saved = canvas.save()
        canvas.translate(x, y)
        canvas.scale(rx, ry)
        paint.shader = shader
        paint.alpha = opacity
        paint.style = Paint.Style.FILL
        canvas.drawCircle(0f, 0f, 1f, paint)
        canvas.restoreToCount(saved)
        paint.alpha = 255
        paint.shader = null
    }

    private fun ensureNoise() {
        if (noise != null) return
        val pixels = IntArray(160 * 160)
        for (i in pixels.indices) {
            val shade = hash(i + 1847) and 255
            pixels[i] = if (shade > 127) alpha(Color.WHITE, (shade - 127) * 2) else alpha(Color.BLACK, (127 - shade) * 2)
        }
        noise = Bitmap.createBitmap(pixels, 160, 160, Bitmap.Config.ARGB_8888)
        noiseShader = BitmapShader(noise!!, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        shaderMatrix.reset()
        shaderMatrix.setScale(1.35f, 1.35f)
        noiseShader?.setLocalMatrix(shaderMatrix)
    }

    private fun ensureHalftone() {
        if (halftone != null) return
        val tile = Bitmap.createBitmap(14, 14, Bitmap.Config.ARGB_8888)
        val tileCanvas = Canvas(tile)
        paint.shader = null
        paint.color = Color.BLACK
        paint.alpha = 180
        tileCanvas.drawCircle(3.5f, 3.5f, 2f, paint)
        tileCanvas.drawCircle(10.5f, 10.5f, 2f, paint)
        paint.alpha = 255
        halftone = tile
        halftoneShader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private fun drawNoise(canvas: Canvas, h: Float, opacity: Int) {
        ensureNoise()
        paint.shader = noiseShader
        paint.alpha = opacity
        canvas.drawRect(0f, 0f, 1000f, h, paint)
        paint.alpha = 255
        paint.shader = null
    }

    private fun drawWatermark(canvas: Canvas, h: Float, bottomInset: Float) {
        watermarkPaint.textSize = 22f
        watermarkPaint.color = 0xD9FFFFFF.toInt()
        watermarkPaint.setShadowLayer(4f, 0f, 1f, 0xB3000000.toInt())
        // Sit below launcher docks, while respecting gesture/three-button navigation.
        val inset = max(66f, bottomInset + 24f).coerceAtMost(h * 0.3f)
        canvas.drawText("made by sush.dev", 500f, h - inset, watermarkPaint)
        watermarkPaint.clearShadowLayer()
    }

    fun release() {
        importedBitmap?.recycle()
        importedBitmap = null
        importedPath = null
        noise?.recycle()
        noise = null
        noiseShader = null
        halftone?.recycle()
        halftone = null
        halftoneShader = null
        cachedPreset = null
        cachedHeight = 0f
        background = null
        glowA = null
        glowB = null
        glowWhite = null
        ribbon = null
        lens = null
    }

    private fun finiteUnit(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    private fun safeOffset(value: Float): Float = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
    private fun alpha(color: Int, opacity: Int): Int = (color and 0x00FFFFFF) or (opacity.coerceIn(0, 255) shl 24)
    private fun mix(a: Int, b: Int, ratio: Float): Int {
        val f = ratio.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt(),
        )
    }
    private fun hash(seed: Int): Int {
        var value = seed
        value = ((value ushr 16) xor value) * 0x45d9f3b
        value = ((value ushr 16) xor value) * 0x45d9f3b
        return ((value ushr 16) xor value) and Int.MAX_VALUE
    }
    private fun fraction(seed: Int): Float = (hash(seed) and 65535) / 65535f
}
