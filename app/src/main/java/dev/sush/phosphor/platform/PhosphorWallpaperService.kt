package dev.sush.phosphor.platform

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.app.WallpaperColors
import android.app.wallpaper.WallpaperDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperSettings
import dev.sush.phosphor.render.LiveRenderQuality
import dev.sush.phosphor.render.WallpaperFrameScheduler
import dev.sush.phosphor.render.WallpaperRenderer

class PhosphorWallpaperService : WallpaperService() {
    private val engines = mutableSetOf<PhosphorEngine>()

    override fun onCreateEngine(): Engine = PhosphorEngine().also { engines += it }

    /** Light/dark mode changes arrive at the service; each engine re-resolves its treatment. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        engines.toList().forEach { it.onSystemThemeChanged() }
    }

    private fun systemNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Each engine owns its renderer: the picker and the active wallpaper can coexist. */
    private inner class PhosphorEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val renderer = WallpaperRenderer()
        private val quality = LiveRenderQuality()
        private val store = SettingsStore(this@PhosphorWallpaperService)
        private val powerManager = getSystemService(PowerManager::class.java)
        private val keyguard = getSystemService(KeyguardManager::class.java)
        private val lockReveal = WallpaperReveal()
        private var settings = WallpaperSettings()
        private var battery = WallpaperBatteryState()
        private var night = true
        private var surfaceReady = false
        private var visible = false
        private var destroyed = false
        private var screenInteractive = powerManager.isInteractive
        private var systemSaver = powerManager.isPowerSaveMode
        private var keyguardLocked = keyguard.isKeyguardLocked
        private val ambient: Boolean get() = !screenInteractive
        private var receiverRegistered = false
        private var animationObserverRegistered = false
        private var pendingWake = !screenInteractive
        private var userPresent = false
        private var profile = WallpaperPowerPolicy.resolve(settings, systemSaver, battery.percent, battery.charging)
        private var frameSettings = settings.copy(speed = 1f)
        private var preset = WallpaperCatalog.find(settings.presetId)
        private var hardwareCanvas = true
        private var width = 1
        private var height = 1
        private var phase = 0f
        private var lastFrame = 0L
        private var revealStart = -1L
        private var scrollX = 0f
        private var scrollY = 0f
        private var drawnScrollX = 0f
        private var drawnScrollY = 0f
        private var drawnDetail = 1f
        private var cachedColors: WallpaperColors? = null
        private val frames = WallpaperFrameScheduler(handler) { drawFrame() }
        private val colorUpdate = Runnable { if (!destroyed) notifyColorsChanged() }
        private val settingsListener: () -> Unit = { refreshSettings() }
        private val animationObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) { restartFrames() }
        }
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_BATTERY_CHANGED) refreshDeviceState()
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        battery = WallpaperPowerPolicy.fromIntent(intent)
                        if (profile == resolvePowerProfile()) return
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        screenInteractive = false
                        pendingWake = true
                        userPresent = false
                        lockReveal.transitionTo(locked = true, animate = false, now = SystemClock.uptimeMillis())
                    }
                    // Wake: the artwork ignites from its ground, if the person wants that.
                    Intent.ACTION_SCREEN_ON -> { screenInteractive = true; pendingWake = true; userPresent = false }
                    Intent.ACTION_USER_PRESENT -> userPresent = true
                }
                restartFrames()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            setOffsetNotificationsEnabled(true)
            settings = currentSettings()
            night = settings.appearance.isNight(systemNight())
            store.addListener(settingsListener, preview = isPreview)
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED).apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }
            val sticky = if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(receiver, filter)
            }
            battery = WallpaperPowerPolicy.fromIntent(sticky)
            receiverRegistered = true
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, animationObserver,
            )
            animationObserverRegistered = true
            restartFrames()
        }

        private fun currentSettings(): WallpaperSettings = if (isPreview) store.loadPreview() else store.load()

        fun onSystemThemeChanged() {
            val next = settings.appearance.isNight(systemNight())
            if (next == night) return
            night = next
            invalidateColors()
            restartFrames()
        }

        private fun invalidateColors() {
            cachedColors = null
            handler.removeCallbacks(colorUpdate)
            handler.postDelayed(colorUpdate, 500)
        }

        private fun refreshSettings() {
            if (destroyed) return
            val next = currentSettings()
            if (settings == next) return
            val paletteChanged = settings.presetId != next.presetId || settings.imagePath != next.imagePath ||
                settings.imageFocusX != next.imageFocusX || settings.imageFocusY != next.imageFocusY ||
                settings.darken != next.darken || settings.intensity != next.intensity || settings.appearance != next.appearance
            if (settings.presetId != next.presetId || settings.imagePath != next.imagePath) resetQuality()
            settings = next
            night = settings.appearance.isNight(systemNight())
            if (paletteChanged) invalidateColors()
            restartFrames()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) refreshDeviceState()
            restartFrames()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            resetQuality()
            surfaceReady = true
            restartFrames()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            resetQuality()
            this.width = width.coerceAtLeast(1)
            this.height = height.coerceAtLeast(1)
            surfaceReady = true
            restartFrames()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            drawFrame(force = true)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            frames.stop()
            lastFrame = 0L
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int,
        ) {
            val nextX = pageOffset(xOffset)
            // Many launchers supply a fixed zero for an unsupported vertical axis.
            val nextY = if (yOffsetStep > 0f || yPixelOffset != 0) pageOffset(yOffset) else 0f
            if (nextX == scrollX && nextY == scrollY) return
            scrollX = nextX
            scrollY = nextY
            if (settings.parallax && motionEnabled() && profile.motionScale > 0f) requestFrame()
        }

        private fun motionEnabled() = !frameSettings.reducedMotion && !ambient

        private fun refreshDeviceState() {
            screenInteractive = powerManager.isInteractive
            systemSaver = powerManager.isPowerSaveMode
            keyguardLocked = keyguard.isKeyguardLocked
        }

        private fun isLocked(): Boolean = !isPreview && (ambient || (!userPresent && keyguardLocked))

        private fun resolvePowerProfile() = WallpaperPowerPolicy.resolve(
            settings, systemSaver, battery.percent, battery.charging, locked = isLocked(),
        )

        // The camera moves toward the next launcher page, so objects move in the opposite direction.
        private fun pageOffset(value: Float): Float =
            if (value in 0f..1f) 1f - 2f * value else 0f

        private fun resetQuality() {
            quality.reset()
            drawnDetail = 1f
        }

        private fun restartFrames() {
            quality.restartSampling()
            // Configuration and power broadcasts refresh these values outside the draw loop.
            profile = resolvePowerProfile()
            preset = WallpaperCatalog.find(settings.presetId)
            frameSettings = settings.copy(
                speed = 1f, // phase already includes the chosen speed and battery policy
                reducedMotion = settings.reducedMotion || !ValueAnimator.areAnimatorsEnabled(),
                darken = if (ambient) maxOf(settings.darken, 0.65f) else settings.darken,
            )
            if (profile.energySaving || !settings.wakeReveal || !motionEnabled()) revealStart = -1L
            frames.stop()
            lastFrame = 0L
            requestFrame()
        }

        /** Launcher offset changes share the same frame budget as the ambient motion. */
        private fun requestFrame() {
            if (visible && surfaceReady && !destroyed && !ambient) frames.request(profile.fps)
        }

        private fun reveal(now: Long): Float {
            if (revealStart < 0) return 1f
            val progress = (now - revealStart) / REVEAL_MS.toFloat()
            if (progress >= 1f) { revealStart = -1; return 1f }
            return progress.coerceAtLeast(0f)
        }

        private fun drawFrame(force: Boolean = false) {
            frames.onFrame()
            if (destroyed || !surfaceReady || ((!visible || ambient) && !force)) return
            val now = SystemClock.uptimeMillis()
            val motion = motionEnabled() && profile.motionScale > 0f
            val delta = if (lastFrame == 0L) 0f else (now - lastFrame).coerceIn(0L, 100L) / 1000f
            if (motion) phase += delta * settings.speed * profile.motionScale
            lastFrame = now
            if (visible) {
                lockReveal.transitionTo(isLocked(), settings.unlockAnimation && motion && !profile.energySaving,
                    now, wake = pendingWake && !ambient)
                if (pendingWake && !ambient) {
                    if (settings.wakeReveal && motion && !profile.energySaving) revealStart = now
                    pendingWake = false
                }
            }
            val reveal = if (motion && settings.wakeReveal) reveal(now) else 1f
            if (motion) {
                drawnScrollX = scrollX
                drawnScrollY = scrollY
            }
            val animating = motion && settings.speed > 0f
            val revealing = motion && (reveal < 1f || lockReveal.isRunning(now))
            val detail = when {
                frameSettings.reducedMotion || settings.speed == 0f -> 1f
                profile.motionScale == 0f -> drawnDetail
                else -> quality.scale
            }
            val sample = visible && animating && !force && !revealing
            val start = if (sample) SystemClock.elapsedRealtimeNanos() else 0L
            var drawn = false
            var canvas: Canvas? = null
            try {
                if (!surfaceHolder.surface.isValid) return
                canvas = lock()
                if (canvas != null) {
                    renderer.draw(
                        canvas, width, height, phase,
                        preset, frameSettings,
                        drawnScrollX, drawnScrollY,
                        night = night,
                        reveal = reveal,
                        unlockReveal = lockReveal.value(now),
                        detailScale = detail,
                    )
                    drawn = true
                }
            } catch (error: IllegalStateException) {
                Log.w("Phosphor", "Wallpaper surface changed while drawing", error)
            } catch (error: IllegalArgumentException) {
                Log.w("Phosphor", "Wallpaper surface is no longer available", error)
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                        if (drawn) {
                            drawnDetail = detail
                            if (sample) quality.posted(start, SystemClock.elapsedRealtimeNanos(), profile.fps)
                        }
                    } catch (_: IllegalStateException) {
                        // A surface can be removed between acquiring and posting its canvas.
                    } catch (_: IllegalArgumentException) {
                        // The system owns the surface lifetime.
                    }
                }
            }
            if (visible && surfaceReady && !destroyed && (animating || revealing)) {
                requestFrame()
            }
        }

        /** The GPU canvas is far cheaper for thousands of strokes; fall back once if refused. */
        private fun lock(): Canvas? {
            if (hardwareCanvas) {
                try {
                    return surfaceHolder.lockHardwareCanvas()
                } catch (error: RuntimeException) {
                    Log.w("Phosphor", "Hardware canvas unavailable; using software rendering", error)
                    hardwareCanvas = false
                }
            }
            return surfaceHolder.lockCanvas()
        }

        override fun onComputeColors(): WallpaperColors {
            cachedColors?.let { return it }
            // A stable representative frame prevents palette flicker as the animation moves.
            val bitmap = Bitmap.createBitmap(96, 192, Bitmap.Config.ARGB_8888)
            val paletteRenderer = WallpaperRenderer()
            return try {
                paletteRenderer.draw(
                    Canvas(bitmap), bitmap.width, bitmap.height, 0f,
                    WallpaperCatalog.find(settings.presetId), settings.copy(reducedMotion = true), night = night,
                )
                val measured = WallpaperColors.fromBitmap(bitmap)
                withHints(measured).also { cachedColors = it }
            } finally {
                paletteRenderer.release()
                bitmap.recycle()
            }
        }

        /**
         * State the treatment explicitly: night art asks launchers for light labels and a dark
         * theme; day art asks for dark labels. Measured colors stay as the palette seed.
         */
        private fun withHints(measured: WallpaperColors): WallpaperColors {
            // Before API 31 the hinted constructor is not public; fromBitmap already infers hints
            // from the light or dark frame, which this renderer guarantees.
            if (Build.VERSION.SDK_INT < 31) return measured
            return hinted(measured)
        }

        @RequiresApi(31)
        private fun hinted(measured: WallpaperColors): WallpaperColors {
            val hints = if (night) WallpaperColors.HINT_SUPPORTS_DARK_THEME else WallpaperColors.HINT_SUPPORTS_DARK_TEXT
            return WallpaperColors(measured.primaryColor, measured.secondaryColor, measured.tertiaryColor, hints)
        }

        @RequiresApi(36)
        override fun onApplyWallpaper(which: Int): WallpaperDescription? {
            // Android 16 provides an explicit commit callback; a dismissed preview never reaches here.
            if (isPreview) store.commitPreview()
            return null
        }

        override fun onDestroy() {
            destroyed = true
            visible = false
            frames.stop()
            engines -= this
            handler.removeCallbacksAndMessages(null)
            store.removeListener(settingsListener)
            if (receiverRegistered) unregisterReceiver(receiver)
            if (animationObserverRegistered) contentResolver.unregisterContentObserver(animationObserver)
            renderer.release()
            super.onDestroy()
        }
    }

    private companion object {
        const val REVEAL_MS = 1500L
    }
}
