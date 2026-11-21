package dev.sush.phosphor.ui

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import dev.sush.phosphor.model.WallpaperPreset
import dev.sush.phosphor.model.WallpaperSettings
import dev.sush.phosphor.render.WallpaperRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal data class ThumbnailRequest(
    val preset: WallpaperPreset,
    val settings: WallpaperSettings,
    val size: IntSize,
)

internal fun thumbnailSize(width: Int, height: Int): IntSize {
    // Keep the tile's aspect ratio and bound both rendering work and retained pixel memory.
    val scale = minOf(1f, 640f / maxOf(width, height).coerceAtLeast(1))
    return IntSize(
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
    )
}

internal object GalleryThumbnails {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker = Dispatchers.Default.limitedParallelism(1)
    private val bytes = minOf(16L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8).toInt()
    private val cache = object : LruCache<ThumbnailRequest, Bitmap>(bytes) {
        override fun sizeOf(key: ThumbnailRequest, value: Bitmap) = value.allocationByteCount
    }

    fun cached(request: ThumbnailRequest): Bitmap? = cache.get(request)

    suspend fun load(request: ThumbnailRequest): Bitmap = withContext(worker) {
        // Explore and Saved can ask for the same frame. Check again after waiting for the worker.
        cached(request)?.let { return@withContext it }
        val (width, height) = request.size
        val renderer = WallpaperRenderer()
        try {
            val bitmap = createBitmap(width, height)
            renderer.draw(Canvas(bitmap), width, height, 12f, request.preset, request.settings)
            bitmap.prepareToDraw()
            cache.put(request, bitmap)
            bitmap
        } finally {
            renderer.release()
        }
    }
}

@Composable
internal fun GalleryThumbnail(preset: WallpaperPreset, settings: WallpaperSettings, modifier: Modifier) {
    BoxWithConstraints(modifier.background(Color(settings.resolvedColors(preset).first()))) {
        val size = thumbnailSize(constraints.maxWidth, constraints.maxHeight)
        val reduced = settings.reducedMotion || !ValueAnimator.areAnimatorsEnabled()
        val request = remember(preset, settings, size, reduced) {
            ThumbnailRequest(preset, settings.copy(reducedMotion = reduced), size)
        }
        var bitmap by remember(request) { mutableStateOf(GalleryThumbnails.cached(request)) }
        LaunchedEffect(request) {
            if (bitmap == null) bitmap = GalleryThumbnails.load(request)
        }
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(), contentDescription = null,
                modifier = Modifier.fillMaxSize().testTag("thumbnail-${preset.id}"),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}
