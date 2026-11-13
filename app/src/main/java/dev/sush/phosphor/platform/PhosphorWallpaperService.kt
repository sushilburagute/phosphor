package dev.sush.phosphor.platform

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.app.wallpaper.WallpaperDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.WindowInsets
import androidx.annotation.RequiresApi
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperSettings
import dev.sush.phosphor.render.TouchField
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
        private val touch = TouchField()
        private val store = SettingsStore(this@PhosphorWallpaperService)
        private val powerManager = getSystemService(PowerManager::class.java)
        private var settings = WallpaperSettings()
        private var power = PowerState()
        private var night = true
        private var surfaceReady = false
        private var visible = false
        private var destroyed = false
        private val ambient: Boolean get() = !powerManager.isInteractive
        private var receiverRegistered = false
        private var hardwareCanvas = true
        private var width = 1
        private var height = 1
        private var bottomInsetPixels = 0
        private var phase = 0f
        private var lastFrame = 0L
        private var revealStart = -1L
        private var scrollX = 0f
        private var cachedColors: WallpaperColors? = null
        private val frame = Runnable { drawFrame() }
        private val colorUpdate = Runnable { if (!destroyed) notifyColorsChanged() }
        private val settingsListener: () -> Unit = { refreshSettings() }
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val next = PowerState.from(intent, powerManager.isPowerSaveMode)
                        if (next == power) return
                        power = next
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> power = power.copy(batterySaver = powerManager.isPowerSaveMode)
                    // Wake: the artwork ignites from its ground, if the person wants that.
                    Intent.ACTION_SCREEN_ON -> if (settings.wakeReveal && !settings.reducedMotion) revealStart = SystemClock.uptimeMillis()
                    // Unlock: a bloom rises from where the unlock gesture usually ends.
                    Intent.ACTION_USER_PRESENT -> if (settings.wakeReveal && !settings.reducedMotion) touch.tap(0.5f, 0.9f, seconds(), 1.2f)
                }
                restartFrames()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(true)
            settings = currentSettings()
            night = settings.appearance.isNight(systemNight())
            store.addListener(settingsListener)
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
            power = PowerState.from(sticky, powerManager.isPowerSaveMode)
            receiverRegistered = true
        }

        private fun currentSettings(): WallpaperSettings = if (isPreview) store.loadPreview() else store.load()

        private fun seconds(): Float = SystemClock.uptimeMillis() / 1000f

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
                settings.darken != next.darken || settings.intensity != next.intensity || settings.appearance != next.appearance
            settings = next
            night = settings.appearance.isNight(systemNight())
            if (paletteChanged) invalidateColors()
            restartFrames()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (!visible) touch.release()
            restartFrames()
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            super.onApplyWindowInsets(insets)
            bottomInsetPixels = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
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
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int,
        ) {
            scrollX = (xOffset.coerceIn(0f, 1f) - 0.5f) * 2f
            if (interactive()) restartFrames()
        }

        private fun interactive() = settings.parallax && !settings.reducedMotion && !ambient

        override fun onTouchEvent(event: MotionEvent) {
            if (!interactive()) return
            val x = event.x / width
            val y = event.y / height
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { touch.press(x, y); touch.tap(x, y, seconds(), 0.7f) }
                MotionEvent.ACTION_MOVE -> touch.move(x, y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touch.release()
            }
            restartFrames()
        }

        /** Launchers report taps on empty space and icon drops as commands, not touches. */
        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (interactive()) {
                when (action) {
                    WallpaperManager.COMMAND_TAP, WallpaperManager.COMMAND_SECONDARY_TAP -> touch.tap(x.toFloat() / width, y.toFloat() / height, seconds())
                    WallpaperManager.COMMAND_DROP -> touch.tap(x.toFloat() / width, y.toFloat() / height, seconds(), 1.3f)
                }
                restartFrames()
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        private fun restartFrames() {
            handler.removeCallbacks(frame)
            lastFrame = 0L
            if (visible && surfaceReady && !destroyed) handler.post(frame)
        }

        private fun reveal(now: Long): Float {
            if (revealStart < 0) return 1f
            val progress = (now - revealStart) / REVEAL_MS.toFloat()
            if (progress >= 1f) { revealStart = -1; return 1f }
            return progress.coerceAtLeast(0f)
        }

        private fun drawFrame(force: Boolean = false) {
            handler.removeCallbacks(frame)
            if (destroyed || !surfaceReady || (!visible && !force)) return
            val now = SystemClock.uptimeMillis()
            val budget = PowerPolicy.budget(settings, power)
            val motion = !budget.still && !ambient
            if (lastFrame != 0L && motion) phase += ((now - lastFrame).coerceAtMost(100L) / 1000f) * settings.speed * budget.motion
            lastFrame = now
            val reveal = if (ambient) 0f else reveal(now)
            var canvas: Canvas? = null
            try {
                if (!surfaceHolder.surface.isValid) return
                canvas = lock()
                if (canvas != null) {
                    // Phase already includes speed; a critical battery simply stops advancing it, freezing in place.
                    var drawSettings = settings.copy(speed = 1f)
                    if (ambient) drawSettings = drawSettings.copy(darken = maxOf(settings.darken, 0.65f))
                    renderer.draw(
                        canvas, width, height, phase,
                        WallpaperCatalog.find(settings.presetId), drawSettings,
                        if (settings.parallax && motion) scrollX * 0.8f + (touch.dragX - 0.5f) * 0.4f * touch.drag else 0f,
                        if (settings.parallax && motion) (touch.dragY - 0.5f) * 0.4f * touch.drag else 0f,
                        bottomInsetPixels = bottomInsetPixels,
                        night = night,
                        touch = if (motion || touch.isActive(now / 1000f)) touch else null,
                        realSeconds = now / 1000f,
                        reveal = reveal,
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
            val animating = motion && settings.speed > 0f
            val responding = !ambient && (touch.isActive(now / 1000f) || reveal < 1f)
            if (visible && surfaceReady && !destroyed && (animating || responding)) {
                val spent = SystemClock.uptimeMillis() - now
                // Touch answers stay smooth even when the ambient budget is low.
                val fps = if (responding && !budget.still) maxOf(budget.fps, 30) else budget.fps
                handler.postDelayed(frame, (1000L / fps.coerceIn(15, 60) - spent).coerceAtLeast(1L))
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
            engines -= this
            handler.removeCallbacksAndMessages(null)
            store.removeListener(settingsListener)
            if (receiverRegistered) unregisterReceiver(receiver)
            renderer.release()
            super.onDestroy()
        }
    }

    private companion object {
        const val REVEAL_MS = 1500L
    }
}
