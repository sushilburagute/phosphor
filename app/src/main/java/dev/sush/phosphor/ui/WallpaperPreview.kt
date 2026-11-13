package dev.sush.phosphor.ui

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
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
import dev.sush.phosphor.platform.WallpaperBatteryState
import dev.sush.phosphor.platform.WallpaperPowerPolicy
import dev.sush.phosphor.render.TouchField
import dev.sush.phosphor.render.WallpaperInteraction
import dev.sush.phosphor.render.WallpaperMotion
import dev.sush.phosphor.render.WallpaperRenderer

/** The gallery draws still frames; only the resumed editor animates. */
@Composable
fun WallpaperPreview(
    preset: WallpaperPreset, settings: WallpaperSettings, modifier: Modifier = Modifier,
    animate: Boolean = false, night: Boolean = true, replayReveal: Int = 0,
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
    private val motion = WallpaperMotion()
    private val touch = TouchField()
    private val idleInteraction = WallpaperInteraction()
    private val power = context.getSystemService(PowerManager::class.java)
    private var preset: WallpaperPreset? = null
    private var settings = WallpaperSettings()
    private var stillSettings = settings.copy(reducedMotion = true)
    private var playing = false
    private var elapsed = 0f
    private var night = true
    private var revealToken = 0
    private var revealStart = -1L
    private var lastFrame = 0L
    private var battery = WallpaperBatteryState()
    private var observing = false
    private val frame = Runnable { invalidate() }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) battery = WallpaperPowerPolicy.fromIntent(intent)
            wakeFrame()
        }
    }
    private val animationObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { wakeFrame() }
    }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun update(preset: WallpaperPreset, settings: WallpaperSettings, playing: Boolean, night: Boolean, reveal: Int) {
        val changed = this.preset != preset || this.settings != settings || this.playing != playing || this.night != night
        if (this.preset != preset) elapsed = 0f
        this.night = night
        if (reveal != revealToken) {
            revealToken = reveal
            if (playing && settings.wakeReveal && !settings.reducedMotion && ValueAnimator.areAnimatorsEnabled()) {
                revealStart = SystemClock.uptimeMillis()
            }
            invalidate()
        }
        this.preset = preset
        if (this.settings != settings) stillSettings = settings.copy(reducedMotion = true)
        this.settings = settings
        if (this.playing != playing) lastFrame = 0L
        this.playing = playing
        if (!playing || settings.reducedMotion || !settings.parallax) { motion.reset(); touch.clear() }
        updateObservers()
        if (changed) wakeFrame()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        removeCallbacks(frame)
        val art = preset ?: return
        val profile = WallpaperPowerPolicy.resolve(settings, power.isPowerSaveMode, battery.percent, battery.charging)
        val reduced = settings.reducedMotion || !ValueAnimator.areAnimatorsEnabled()
        val animate = playing && !reduced && profile.motionScale > 0f && isAttachedToWindow && windowVisibility == VISIBLE && isShown
        val now = SystemClock.uptimeMillis()
        val dt = if (animate && lastFrame != 0L) ((now - lastFrame).coerceIn(0L, 100L) / 1000f) else 0f
        lastFrame = if (animate) now else 0L
        if (animate && settings.speed > 0f) elapsed += dt * settings.speed * profile.motionScale
        if (!animate) { motion.reset(); touch.clear() }
        val interaction = if (animate) motion.advance(dt) else idleInteraction
        val reveal = if (revealStart < 0 || !animate || profile.energySaving) 1f else
            ((now - revealStart) / 1500f).coerceIn(0f, 1f).also { if (it >= 1f) revealStart = -1L }
        renderer.draw(
            canvas, width, height, if (playing) elapsed else 12f, art,
            if (reduced) stillSettings else if (playing) settings.copy(speed = 1f) else settings,
            night = night, touch = if (animate) touch else null, realSeconds = now / 1000f,
            reveal = reveal, interaction = interaction,
        )
        if (animate && (settings.speed > 0f || motion.isActive || touch.isActive(now / 1000f) || reveal < 1f)) {
            postDelayed(frame, (1000L / profile.fps.coerceIn(1, 60) - (SystemClock.uptimeMillis() - now)).coerceAtLeast(1L))
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val profile = WallpaperPowerPolicy.resolve(settings, power.isPowerSaveMode, battery.percent, battery.charging)
        if (!playing || !settings.parallax || settings.reducedMotion || !ValueAnimator.areAnimatorsEnabled() || profile.motionScale == 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                touch.press(event.x / width.coerceAtLeast(1), event.y / height.coerceAtLeast(1))
                touch.tap(event.x / width.coerceAtLeast(1), event.y / height.coerceAtLeast(1), SystemClock.uptimeMillis() / 1000f, 0.8f)
            }
            MotionEvent.ACTION_MOVE -> touch.move(event.x / width.coerceAtLeast(1), event.y / height.coerceAtLeast(1))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Only a real lift releases a held charge; a cancelled gesture just lets go.
                touch.release(if (event.actionMasked == MotionEvent.ACTION_UP) SystemClock.uptimeMillis() / 1000f else Float.NaN)
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        motion.onTouch(event.actionMasked, event.x / width.coerceAtLeast(1), event.y / height.coerceAtLeast(1))
        invalidate()
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    private fun wakeFrame() {
        lastFrame = 0L
        removeCallbacks(frame)
        invalidate()
    }

    private fun updateObservers() {
        val shouldObserve = playing && isAttachedToWindow
        if (shouldObserve == observing) return
        if (shouldObserve) {
            battery = WallpaperPowerPolicy.readBattery(context)
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(batteryReceiver, filter)
            }
            context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, animationObserver)
            observing = true
        } else {
            context.unregisterReceiver(batteryReceiver)
            context.contentResolver.unregisterContentObserver(animationObserver)
            observing = false
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        lastFrame = 0L
        removeCallbacks(frame)
        if (visibility == VISIBLE) invalidate() else { motion.reset(); touch.clear() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrame = 0L
        updateObservers()
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    fun dispose() {
        playing = false
        removeCallbacks(frame)
        updateObservers()
        motion.reset()
        touch.clear()
        renderer.release()
    }

    override fun onDetachedFromWindow() {
        if (observing) {
            context.unregisterReceiver(batteryReceiver)
            context.contentResolver.unregisterContentObserver(animationObserver)
            observing = false
        }
        removeCallbacks(frame)
        lastFrame = 0L
        motion.reset()
        touch.clear()
        renderer.release()
        super.onDetachedFromWindow()
    }
}
