package dev.sush.phosphor.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.sush.phosphor.R
import dev.sush.phosphor.model.WallpaperSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The focus target is on the complete source image. The pinned wallpaper shows the resulting crop. */
@Composable
internal fun PhotoFraming(settings: WallpaperSettings, onChange: (WallpaperSettings) -> Unit) {
    val path = settings.imagePath
    val source by produceState<ImageBitmap?>(null, path) {
        value = null
        if (path != null) value = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 768) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
    val changeFocus by rememberUpdatedState<(Float, Float) -> Unit> { x, y ->
        onChange(settings.copy(imageFocusX = x, imageFocusY = y))
    }
    Section("Photo framing", R.drawable.ic_home_screen) {
        Text("Move the target to the part of the photo you want to keep in view.", style = MaterialTheme.typography.bodyMedium)
        source?.let { image ->
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val ratio = image.width.toFloat() / image.height
                val width = minOf(maxWidth, 164.dp * ratio)
                Box(Modifier.size(width, width / ratio).clip(MaterialTheme.shapes.small).pointerInput(path) {
                    fun place(point: Offset) {
                        changeFocus((point.x / size.width).coerceIn(0f, 1f), (point.y / size.height).coerceIn(0f, 1f))
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        place(down.position)
                        down.consume()
                        do {
                            val move = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (move.pressed) place(move.position)
                            move.consume()
                        } while (move.pressed)
                    }
                }) {
                    Image(image, "Full imported photo", modifier = Modifier.fillMaxSize())
                    Canvas(Modifier.fillMaxSize()) {
                        val point = Offset(settings.imageFocusX * size.width, settings.imageFocusY * size.height)
                        drawCircle(Color.Black, 10.dp.toPx(), point, style = Stroke(4.dp.toPx()))
                        drawCircle(Color.White, 10.dp.toPx(), point, style = Stroke(2.dp.toPx()))
                        drawCircle(Color.White, 2.dp.toPx(), point)
                    }
                }
            }
        }
        SliderRow("Horizontal focus", settings.imageFocusX) { onChange(settings.copy(imageFocusX = it)) }
        SliderRow("Vertical focus", settings.imageFocusY) { onChange(settings.copy(imageFocusY = it)) }
    }
}
