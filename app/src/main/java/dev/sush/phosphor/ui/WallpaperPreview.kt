package dev.sush.phosphor.ui

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.os.PowerManager
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sush.phosphor.model.WallpaperPreset
import dev.sush.phosphor.model.WallpaperSettings
import dev.sush.phosphor.render.WallpaperRenderer

/** The gallery draws still frames; only the editor animates. */
@Composable
fun WallpaperPreview(preset: WallpaperPreset, settings: WallpaperSettings, modifier: Modifier = Modifier, animate: Boolean = false) {
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    AndroidView(
        factory = { PreviewView(it) },
        modifier = modifier,
        update = { it.update(preset, settings, animate && resumed) },
        onRelease = { it.dispose() },
    )
}

private class PreviewView(context: Context) : View(context) {
    private val renderer = WallpaperRenderer()
    private val power = context.getSystemService(PowerManager::class.java)
    private var preset: WallpaperPreset? = null
    private var settings = WallpaperSettings()
    private var playing = false
    private val start = SystemClock.uptimeMillis()
    private var offsetX = 0f
    private var offsetY = 0f

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun update(preset: WallpaperPreset, settings: WallpaperSettings, playing: Boolean) {
        val changed = this.preset != preset || this.settings != settings || this.playing != playing
        this.preset = preset
        this.settings = settings
        this.playing = playing
        if (changed) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val art = preset ?: return
        val time = if (playing && !settings.reducedMotion) (SystemClock.uptimeMillis() - start) / 1000f else 12f
        renderer.draw(canvas, width, height, time, art, settings, offsetX, offsetY)
        if (playing && !settings.reducedMotion && settings.speed > 0f && isAttachedToWindow && windowVisibility == VISIBLE) {
            val fps = if (power.isPowerSaveMode) minOf(settings.fps, 15) else settings.fps
            postInvalidateDelayed(1000L / fps.coerceIn(15, 60))
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!playing || !settings.parallax || settings.reducedMotion) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                offsetX = (event.x / width.coerceAtLeast(1) - .5f) * 2f
                offsetY = (event.y / height.coerceAtLeast(1) - .5f) * 2f
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { offsetX = 0f; offsetY = 0f; invalidate(); performClick() }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
    fun dispose() { playing = false; renderer.release() }
    override fun onDetachedFromWindow() { renderer.release(); super.onDetachedFromWindow() }
}
