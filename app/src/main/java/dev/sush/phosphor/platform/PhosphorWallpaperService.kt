package dev.sush.phosphor.platform

import androidx.annotation.RequiresApi
import android.app.WallpaperColors
import android.app.wallpaper.WallpaperDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.WindowInsets
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperSettings
import dev.sush.phosphor.render.WallpaperRenderer

class PhosphorWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = PhosphorEngine()

    /** Each engine owns its renderer: the picker and the active wallpaper can coexist. */
    private inner class PhosphorEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val renderer = WallpaperRenderer()
        private val store = SettingsStore(this@PhosphorWallpaperService)
        private val power = getSystemService(PowerManager::class.java)
        private var settings = WallpaperSettings()
        private var surfaceReady = false
        private var visible = false
        private var destroyed = false
        private val ambient: Boolean get() = !power.isInteractive
        private var receiverRegistered = false
        private var width = 1
        private var height = 1
        private var bottomInsetPixels = 0
        private var elapsed = 0f
        private var lastFrame = 0L
        private var scrollX = 0f
        private var touchX = 0f
        private var touchY = 0f
        private var cachedColors: WallpaperColors? = null
        private val frame = Runnable { drawFrame() }
        private val colorUpdate = Runnable { if (!destroyed) notifyColorsChanged() }
        private val settingsListener: () -> Unit = { refreshSettings() }
        private val powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                restartFrames()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(true)
            settings = currentSettings()
            store.addListener(settingsListener)
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED).apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(powerReceiver, filter)
            }
            receiverRegistered = true
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
            if (settings.parallax && !settings.reducedMotion) restartFrames()
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (!settings.parallax || settings.reducedMotion || ambient) return
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    touchX = (event.x / width * 2f - 1f).coerceIn(-1f, 1f)
                    touchY = (event.y / height * 2f - 1f).coerceIn(-1f, 1f)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchX = 0f
                    touchY = 0f
                }
            }
            restartFrames()
        }

        private fun restartFrames() {
            handler.removeCallbacks(frame)
            lastFrame = 0L
            if (visible && surfaceReady && !destroyed) handler.post(frame)
        }

        private fun drawFrame(force: Boolean = false) {
            handler.removeCallbacks(frame)
            if (destroyed || !surfaceReady || (!visible && !force)) return
            val now = SystemClock.uptimeMillis()
            val motion = !settings.reducedMotion && !ambient
            if (lastFrame != 0L && motion) elapsed += ((now - lastFrame).coerceAtMost(100L) / 1000f)
            lastFrame = now
            var canvas: Canvas? = null
            try {
                if (!surfaceHolder.surface.isValid) return
                canvas = surfaceHolder.lockCanvas()
                if (canvas != null) {
                    val drawSettings = if (ambient) settings.copy(darken = maxOf(settings.darken, 0.65f)) else settings
                    renderer.draw(
                        canvas, width, height, if (settings.reducedMotion) 0f else elapsed,
                        WallpaperCatalog.find(settings.presetId), drawSettings,
                        if (settings.parallax && motion) (scrollX * 0.65f + touchX * 0.35f) else 0f,
                        if (settings.parallax && motion) touchY * 0.35f else 0f,
                        bottomInsetPixels = bottomInsetPixels,
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
            if (visible && surfaceReady && !destroyed && motion && settings.speed > 0f) {
                val fps = if (power.isPowerSaveMode) minOf(settings.fps, 15) else settings.fps
                val spent = SystemClock.uptimeMillis() - now
                handler.postDelayed(frame, (1000L / fps.coerceIn(15, 60) - spent).coerceAtLeast(1L))
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
            renderer.release()
            super.onDestroy()
        }
    }
}
