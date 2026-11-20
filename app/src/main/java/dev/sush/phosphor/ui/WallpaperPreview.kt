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
import dev.sush.phosphor.platform.EchoMusicInput
import dev.sush.phosphor.platform.allowsMusic
import dev.sush.phosphor.platform.WallpaperBatteryState
import dev.sush.phosphor.platform.WallpaperPowerPolicy
import dev.sush.phosphor.platform.WallpaperTilt
import dev.sush.phosphor.platform.allowsTilt
import dev.sush.phosphor.render.WallpaperRenderer
import dev.sush.phosphor.render.WallpaperFrameScheduler

/** The gallery draws still frames; only the resumed editor animates. */
@Composable
fun WallpaperPreview(
    preset: WallpaperPreset, settings: WallpaperSettings, modifier: Modifier = Modifier,
    animate: Boolean = false, replayReveal: Int = 0,
) {
    if (!animate) {
        GalleryThumbnail(preset, settings, modifier)
        return
    }
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
        update = { it.update(preset, settings, resumed, replayReveal) },
        onRelease = { it.dispose() },
    )
}

internal open class PreviewView(context: Context) : View(context) {
    private val renderer = WallpaperRenderer(cacheOrbitBands = true)
    private val power = context.getSystemService(PowerManager::class.java)
    private var screenInteractive = power.isInteractive
    private var systemSaver = power.isPowerSaveMode
    private var preset: WallpaperPreset? = null
    private var settings = WallpaperSettings()
    private var stillSettings = settings.copy(reducedMotion = true)
    private var playingSettings = settings.copy(speed = 1f)
    private var playing = false
    private var elapsed = 0f
    private var revealToken = 0
    private var revealStart = -1L
    private var lastFrame = 0L
    private var battery = WallpaperBatteryState()
    private var profile = resolvePowerProfile()
    private val music = EchoMusicInput()
    private val tilt = WallpaperTilt(context)
    private var drawnTiltX = 0f
    private var drawnTiltY = 0f
    private var observing = false
    private val handler = Handler(Looper.getMainLooper())
    private val frames = WallpaperFrameScheduler { invalidate() }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                battery = WallpaperPowerPolicy.fromIntent(intent)
                if (profile == resolvePowerProfile()) return
            } else {
                refreshDeviceState()
                if (intent?.action == Intent.ACTION_SCREEN_OFF) screenInteractive = false
                if (intent?.action == Intent.ACTION_SCREEN_ON) screenInteractive = true
            }
            wakeFrame()
        }
    }
    private val animationObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { wakeFrame() }
    }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun update(preset: WallpaperPreset, settings: WallpaperSettings, playing: Boolean, reveal: Int = 0) {
        val changed = this.preset != preset || this.settings != settings || this.playing != playing
        if (this.preset != preset) elapsed = 0f
        if (reveal != revealToken) {
            revealToken = reveal
            if (playing && settings.wakeReveal && !settings.reducedMotion && ValueAnimator.areAnimatorsEnabled()) {
                revealStart = SystemClock.uptimeMillis()
            }
            wakeFrame()
        }
        this.preset = preset
        if (this.settings != settings) {
            stillSettings = settings.copy(reducedMotion = true)
            playingSettings = settings.copy(speed = 1f)
        }
        this.settings = settings
        if (this.playing != playing) lastFrame = 0L
        this.playing = playing
        updateObservers()
        if (changed) wakeFrame()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        frames.onFrame()
        val art = preset ?: return
        if (width <= 0 || height <= 0) return
        val reduced = settings.reducedMotion || !ValueAnimator.areAnimatorsEnabled()
        val animate = playing && !reduced && profile.motionScale > 0f && canRender() && screenInteractive
        val now = SystemClock.uptimeMillis()
        val dt = if (animate && lastFrame != 0L) ((now - lastFrame).coerceIn(0L, 100L) / 1000f) else 0f
        lastFrame = if (animate) now else 0L
        if (animate && settings.speed > 0f) elapsed += dt * settings.speed * profile.motionScale
        if (animate || !playing || settings.speed == 0f) {
            drawnTiltX = tilt.x
            drawnTiltY = tilt.y
        }
        val reveal = if (revealStart < 0 || !animate || profile.energySaving) 1f else
            ((now - revealStart) / 1500f).coerceIn(0f, 1f).also { if (it >= 1f) revealStart = -1L }
        renderer.musicSpectrum = music.current(now)
        renderer.draw(
            canvas, width, height, if (playing) elapsed else 12f, art,
            if (reduced) stillSettings else if (playing) playingSettings else settings,
            reveal = reveal,
            tiltX = drawnTiltX, tiltY = drawnTiltY,
        )
        if (animate && (settings.speed > 0f || reveal < 1f)) {
            frames.request(profile.fps)
        }
    }

    private fun wakeFrame() {
        profile = resolvePowerProfile()
        val motion = playing && canRender() && screenInteractive && ValueAnimator.areAnimatorsEnabled()
        music.update(context, motion && settings.allowsMusic(profile))
        if (motion && settings.allowsTilt(profile)) {
            tilt.start(display)
        } else {
            tilt.stop()
        }
        if (profile.energySaving || !settings.wakeReveal || !screenInteractive) revealStart = -1L
        lastFrame = 0L
        frames.stop()
        if (canRender() && screenInteractive) frames.request(profile.fps)
    }

    private fun canRender(): Boolean = isAttachedToWindow && windowVisibility == VISIBLE && isShown

    private fun refreshDeviceState() {
        screenInteractive = power.isInteractive
        systemSaver = power.isPowerSaveMode
    }

    private fun resolvePowerProfile() = WallpaperPowerPolicy.resolve(settings, systemSaver, battery.percent, battery.charging)

    private fun updateObservers() {
        val shouldObserve = playing && canRender()
        if (shouldObserve == observing) return
        if (shouldObserve) {
            refreshDeviceState()
            battery = WallpaperPowerPolicy.readBattery(context)
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
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

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (preset == null) return
        updateObservers()
        wakeFrame()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrame = 0L
        updateObservers()
        wakeFrame()
    }

    fun dispose() {
        music.stop()
        tilt.stop()
        playing = false
        frames.stop()
        updateObservers()
        renderer.release()
    }

    override fun onDetachedFromWindow() {
        music.stop()
        tilt.stop()
        if (observing) {
            context.unregisterReceiver(batteryReceiver)
            context.contentResolver.unregisterContentObserver(animationObserver)
            observing = false
        }
        frames.stop()
        lastFrame = 0L
        renderer.release()
        super.onDetachedFromWindow()
    }
}
