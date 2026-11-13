package dev.sush.phosphor.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperSettings
import dev.sush.phosphor.render.WallpaperRenderer
import java.io.File
import java.io.IOException

/** Decode a bounded, private copy so a revoked document permission cannot break the wallpaper. */
internal fun importImage(context: Context, uri: Uri): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    (context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read image")).use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unsupported image")
    var sample = 1
    while (bounds.outWidth / sample > 2560 || bounds.outHeight / sample > 2560) sample *= 2
    val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: throw IOException("Cannot decode image")
    var bitmap = decoded
    val directory = File(context.filesDir, "wallpapers").apply { mkdirs() }
    val target = File(directory, "import-${System.currentTimeMillis()}.png")
    var saved = false
    try {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: IOException) { ExifInterface.ORIENTATION_NORMAL }
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        if (!matrix.isIdentity) bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (bitmap !== decoded) decoded.recycle()
        target.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Cannot save image") }
        saved = true
        return target.absolutePath
    } finally {
        if (!decoded.isRecycled) decoded.recycle()
        if (!bitmap.isRecycled) bitmap.recycle()
        if (!saved) target.delete()
    }
}

internal fun cleanOldImports(context: Context, keep: Set<String>) {
    val directory = File(context.filesDir, "wallpapers")
    val root = directory.canonicalFile
    directory.listFiles()?.filter { it.name.startsWith("import-") && it.extension == "png" }?.forEach { file ->
        if (file.canonicalFile.parentFile == root && file.absolutePath !in keep) file.delete()
    }
}

/** Export a still in the same treatment (day or night) the person is looking at. */
internal fun exportFrame(context: Context, uri: Uri, settings: WallpaperSettings, night: Boolean = true) {
    val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
    val renderer = WallpaperRenderer()
    try {
        renderer.draw(Canvas(bitmap), bitmap.width, bitmap.height, 12f, WallpaperCatalog.find(settings.presetId), settings, night = night)
        context.contentResolver.openOutputStream(uri, "w")?.use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Cannot encode frame") } ?: throw IOException("Cannot write destination")
    } finally { renderer.release(); bitmap.recycle() }
}
