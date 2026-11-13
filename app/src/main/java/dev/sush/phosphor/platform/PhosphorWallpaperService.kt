package dev.sush.phosphor.platform

import androidx.annotation.RequiresApi
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.app.WallpaperColors
import android.app.wallpaper.WallpaperDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.view.MotionEvent
import android.view.SurfaceHolder
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperSettings
import dev.sush.phosphor.render.WallpaperRenderer
import dev.sush.phosphor.render.WallpaperInteraction
import dev.sush.phosphor.render.WallpaperMotion

class PhosphorWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = PhosphorEngine()

    /** Each engine owns its renderer: the picker and the active wallpaper can coexist. */
    private inner class PhosphorEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val renderer = WallpaperRenderer()
        private val store = SettingsStore(this@PhosphorWallpaperService)
        private val power = getSystemService(PowerManager::class.java)
        private val keyguard = getSystemService(KeyguardManager::class.java)
        private val interaction = WallpaperMotion()
        private val reveal = WallpaperReveal()
        private var battery = WallpaperBatteryState()
        private var settings = WallpaperSettings()
        private var surfaceReady = false
        private var visible = false
        private var destroyed = false
        private val ambient: Boolean get() = !power.isInteractive
        private var receiverRegistered = false
        private var animationObserverRegistered = false
        private var pendingWake = !power.isInteractive
        private var userPresent = false
        private var width = 1
        private var height = 1
        private var elapsed = 0f
        private var lastFrame = 0L
        private var scrollX = 0f
        private var cachedColors: WallpaperColors? = null
        private val frame = Runnable { drawFrame() }
        private val colorUpdate = Runnable { if (!destroyed) notifyColorsChanged() }
        private val settingsListener: () -> Unit = { refreshSettings() }
        private val animationObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) { restartFrames() }
        }
        private val powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> battery = WallpaperPowerPolicy.fromIntent(intent)
                    Intent.ACTION_SCREEN_OFF -> {
                        pendingWake = true
                        userPresent = false
                        interaction.reset()
                        // Android owns screen shutdown. Never keep a hidden surface animating.
                        reveal.transitionTo(locked = true, animate = false, now = SystemClock.uptimeMillis())
                    }
                    Intent.ACTION_SCREEN_ON -> { pendingWake = true; userPresent = false }
                    Intent.ACTION_USER_PRESENT -> userPresent = true
                }
                restartFrames()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(true)
            settings = currentSettings()
            battery = WallpaperPowerPolicy.readBattery(this@PhosphorWallpaperService)
            store.addListener(settingsListener)
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED).apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(powerReceiver, filter)
            }
            receiverRegistered = true
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, animationObserver,
            )
            animationObserverRegistered = true
        }

        private fun currentSettings(): WallpaperSettings = if (isPreview) store.loadPreview() else store.load()

        private fun refreshSettings() {
            if (destroyed) return
            val next = currentSettings()
            if (settings == next) return
            val paletteChanged = settings.presetId != next.presetId || settings.imagePath != next.imagePath ||
                settings.darken != next.darken || settings.intensity != next.intensity
            settings = next
            if (paletteChanged) {
                cachedColors = null
                handler.removeCallbacks(colorUpdate)
                handler.postDelayed(colorUpdate, 500)
            }
            restartFrames()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (!visible) interaction.reset()
            restartFrames()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceReady = true
            restartFrames()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
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
            handler.removeCallbacks(frame)
            lastFrame = 0L
            interaction.reset()
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int,
        ) {
            scrollX = (xOffset.coerceIn(0f, 1f) - 0.5f) * 2f
            if (settings.parallax && motionEnabled()) requestFrame()
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (!visible || !settings.parallax || !motionEnabled() || powerProfile().motionScale == 0f) return
            interaction.onTouch(
                event.actionMasked, event.x / width.coerceAtLeast(1), event.y / height.coerceAtLeast(1),
            )
            requestFrame()
        }

        private fun restartFrames() {
            handler.removeCallbacks(frame)
            lastFrame = 0L
            if (!settings.parallax || !motionEnabled() || powerProfile().motionScale == 0f) interaction.reset()
            if (visible && surfaceReady && !destroyed) handler.post(frame)
        }

        /** Touches must not reset the frame clock, otherwise a drag can freeze elapsed time. */
        private fun requestFrame() {
            handler.removeCallbacks(frame)
            if (visible && surfaceReady && !destroyed) handler.post(frame)
        }

        private fun powerProfile(): WallpaperPowerProfile = WallpaperPowerPolicy.resolve(
            settings, power.isPowerSaveMode, battery.percent, battery.charging,
        )

        private fun motionEnabled(): Boolean = !settings.reducedMotion && !ambient && ValueAnimator.areAnimatorsEnabled()

        private fun drawFrame(force: Boolean = false) {
            handler.removeCallbacks(frame)
            if (destroyed || !surfaceReady || (!visible && !force)) return
            val now = SystemClock.uptimeMillis()
            val profile = powerProfile()
            val motion = motionEnabled() && profile.motionScale > 0f
            val delta = if (lastFrame == 0L) 0f else (now - lastFrame).coerceIn(0L, 100L) / 1000f
            if (motion) elapsed += delta * profile.motionScale
            lastFrame = now
            if (visible) {
                // USER_PRESENT is authoritative even if the keyguard query briefly lags it.
                val locked = !isPreview && (ambient || (!userPresent && keyguard.isKeyguardLocked))
                reveal.transitionTo(
                    locked, settings.unlockAnimation && motion && !profile.energySaving,
                    now, wake = pendingWake && !ambient,
                )
                if (!ambient) pendingWake = false
            }
            val touch = if (motion && settings.parallax) interaction.advance(delta) else WallpaperInteraction()
            var canvas: Canvas? = null
            try {
                if (!surfaceHolder.surface.isValid) return
                canvas = surfaceHolder.lockCanvas()
                if (canvas != null) {
                    val drawSettings = if (ambient) settings.copy(darken = maxOf(settings.darken, 0.65f)) else settings
                    renderer.draw(
                        canvas, width, height, if (settings.reducedMotion) 0f else elapsed,
                        WallpaperCatalog.find(settings.presetId), drawSettings,
                        if (settings.parallax && motion) scrollX * 0.65f else 0f,
                        0f,
                        reveal = if (settings.unlockAnimation && !settings.reducedMotion) reveal.value(now) else 1f,
                        interaction = touch,
                    )
                }
            } catch (error: IllegalStateException) {
                Log.w("Phosphor", "Wallpaper surface changed while drawing", error)
            } catch (error: IllegalArgumentException) {
                Log.w("Phosphor", "Wallpaper surface is no longer available", error)
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    } catch (_: IllegalStateException) {
                        // A surface can be removed between acquiring and posting its canvas.
                    } catch (_: IllegalArgumentException) {
                        // The system owns the surface lifetime.
                    }
                }
            }
            val needsMotionFrame = motion && (settings.speed > 0f || interaction.isActive || reveal.isRunning(now))
            if (visible && surfaceReady && !destroyed && needsMotionFrame) {
                val spent = SystemClock.uptimeMillis() - now
                handler.postDelayed(frame, (1000L / profile.fps - spent).coerceAtLeast(1L))
            }
        }

        override fun onComputeColors(): WallpaperColors {
            cachedColors?.let { return it }
            // A stable representative frame prevents palette flicker as the animation moves.
            val bitmap = Bitmap.createBitmap(96, 192, Bitmap.Config.ARGB_8888)
            val paletteRenderer = WallpaperRenderer()
            return try {
                rendererColors(paletteRenderer, bitmap).also { cachedColors = it }
            } finally {
                paletteRenderer.release()
                bitmap.recycle()
            }
        }

        private fun rendererColors(paletteRenderer: WallpaperRenderer, bitmap: Bitmap): WallpaperColors {
            paletteRenderer.draw(
                Canvas(bitmap), bitmap.width, bitmap.height, 0f,
                WallpaperCatalog.find(settings.presetId), settings,
            )
            return WallpaperColors.fromBitmap(bitmap)
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
            handler.removeCallbacksAndMessages(null)
            store.removeListener(settingsListener)
            if (receiverRegistered) unregisterReceiver(powerReceiver)
            if (animationObserverRegistered) contentResolver.unregisterContentObserver(animationObserver)
            renderer.release()
            super.onDestroy()
        }
    }
}
