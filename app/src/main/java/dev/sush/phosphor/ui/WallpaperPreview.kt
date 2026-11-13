package dev.sush.phosphor.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.os.PowerManager
import android.os.SystemClock
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
import dev.sush.phosphor.render.TouchField
import dev.sush.phosphor.render.WallpaperRenderer

/**
 * The gallery draws still frames; only the editor animates. An animated preview behaves like
 * the real wallpaper: taps send pulses, drags pull the artwork, and [replayReveal] replays the
 * wake animation whenever its value changes.
 */
@Composable
fun WallpaperPreview(
    preset: WallpaperPreset,
    settings: WallpaperSettings,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    night: Boolean = true,
    replayReveal: Int = 0,
) {
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
        update = { it.update(preset, settings, animate && resumed, night, replayReveal) },
        onRelease = { it.dispose() },
    )
}

private class PreviewView(context: Context) : View(context) {
    private val renderer = WallpaperRenderer()
    private val touch = TouchField()
    private val power = context.getSystemService(PowerManager::class.java)
    private var preset: WallpaperPreset? = null
    private var settings = WallpaperSettings()
    private var playing = false
    private var night = true
    private var phase = 0f
    private var lastFrame = 0L
    private var revealToken = 0
    private var revealStart = -1L

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun update(preset: WallpaperPreset, settings: WallpaperSettings, playing: Boolean, night: Boolean, reveal: Int) {
        val changed = this.preset != preset || this.settings != settings || this.playing != playing || this.night != night
        this.preset = preset
        this.settings = settings
        this.playing = playing
        this.night = night
        if (reveal != revealToken) {
            revealToken = reveal
            if (playing && motionAllowed()) revealStart = SystemClock.uptimeMillis()
            invalidate()
        }
        if (changed) { lastFrame = 0L; invalidate() }
    }

    private fun motionAllowed() = !settings.reducedMotion && ValueAnimator.areAnimatorsEnabled()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val art = preset ?: return
        val now = SystemClock.uptimeMillis()
        val live = playing && motionAllowed()
        if (live && lastFrame != 0L) phase += (now - lastFrame).coerceAtMost(100L) / 1000f * settings.speed
        lastFrame = now
        val reveal = if (revealStart < 0) 1f else ((now - revealStart) / 1500f).coerceIn(0f, 1f).also { if (it >= 1f) revealStart = -1 }
        val time = if (live) phase else 12f
        renderer.draw(
            canvas, width, height, time, art,
            // Thumbnails show a frame from 12 s in; live previews keep their own phase.
            if (live) settings.copy(speed = 1f) else settings,
            (touch.dragX - 0.5f) * 0.8f * touch.drag, (touch.dragY - 0.5f) * 0.8f * touch.drag,
            night = night,
            touch = if (live) touch else null,
            realSeconds = now / 1000f,
            reveal = if (live) reveal else 1f,
        )
        val responding = touch.isActive(now / 1000f) || reveal < 1f
        if (playing && motionAllowed() && (settings.speed > 0f || responding) && isAttachedToWindow && windowVisibility == VISIBLE) {
            val fps = if (power.isPowerSaveMode) minOf(settings.fps, 15) else settings.fps
            postInvalidateDelayed(1000L / fps.coerceIn(15, 60))
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!playing || !settings.parallax || !motionAllowed()) return false
        val x = event.x / width.coerceAtLeast(1)
        val y = event.y / height.coerceAtLeast(1)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                touch.press(x, y)
                touch.tap(x, y, SystemClock.uptimeMillis() / 1000f, 0.8f)
            }
            MotionEvent.ACTION_MOVE -> touch.move(x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touch.release()
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
    fun dispose() { playing = false; touch.clear(); renderer.release() }
    override fun onDetachedFromWindow() { renderer.release(); super.onDetachedFromWindow() }
}
