package dev.sush.phosphor.platform

import android.content.Context
import android.os.Looper
import dev.sush.phosphor.model.WallpaperSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsStoreTest {
    private lateinit var context: Context
    private lateinit var store: SettingsStore

    @Before
    fun resetPreferences() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("phosphor", Context.MODE_PRIVATE).edit().clear().commit()
        store = SettingsStore(context)
    }

    @Test
    fun canceledPreviewDoesNotChangeActiveWallpaperEvenAcrossStoreInstances() {
        val active = WallpaperSettings(presetId = "aurora-veil", intensity = 0.3f)
        val draft = active.copy(presetId = "different-wallpaper", intensity = 0.9f, imagePath = "/private/art.jpg")
        store.save(active)
        store.savePreview(draft)

        val recreatedStore = SettingsStore(context)
        assertEquals(active, recreatedStore.load())
        assertEquals(draft, recreatedStore.loadPreview())
        // No commit occurs when the picker is dismissed. Later activity recreation remains safe.
        assertEquals(active, SettingsStore(context).load())
    }

    @Test
    fun confirmingPreviewPromotesWholeDraftAndPersistsIt() {
        val draft = WallpaperSettings(
            presetId = "photo", speed = 0.2f, intensity = 0.4f, parallax = false,
            fps = 24, darken = 0.25f, imagePath = "/private/photo.png", reducedMotion = true,
            appearance = dev.sush.phosphor.model.Appearance.DAY, wakeReveal = false, lowBatteryMode = false,
        )
        store.savePreview(draft)
        store.commitPreview()

        assertEquals(draft, store.load())
        assertEquals(draft, SettingsStore(context).load())
    }

    @Test
    fun previewInitiallyUsesActiveConfiguration() {
        val active = WallpaperSettings(speed = 0.7f, reducedMotion = true)
        store.save(active)
        assertEquals(active, store.loadPreview())
    }

    @Test
    fun corruptStoredJsonFallsBackWithoutCrashing() {
        context.getSharedPreferences("phosphor", Context.MODE_PRIVATE).edit()
            .putString("active", "{broken json").commit()
        assertEquals(WallpaperSettings(), store.load())
    }

    @Test
    fun legacyOrTamperedNumericValuesAreClampedOnRead() {
        context.getSharedPreferences("phosphor", Context.MODE_PRIVATE).edit().putString(
            "active",
            """{"speed":-8,"intensity":42,"fps":0,"darken":3,"imagePath":null}""",
        ).commit()

        val settings = store.load()
        assertEquals(0f, settings.speed)
        assertEquals(1f, settings.intensity)
        assertEquals(15, settings.fps)
        assertEquals(1f, settings.darken)
        assertNull(settings.imagePath)
    }

    @Test
    fun nonFiniteSettingsCannotProduceInvalidJsonOrUnsafeFrameRate() {
        store.save(WallpaperSettings(speed = Float.NaN, intensity = Float.POSITIVE_INFINITY, darken = -5f, fps = 500))
        val settings = store.load()
        assertEquals(0.5f, settings.speed)
        assertEquals(0.6f, settings.intensity)
        assertEquals(0f, settings.darken)
        assertEquals(60, settings.fps)
    }

    @Test
    fun engineReceivesCrossInstanceUpdatesAndCanUnregister() {
        val engineStore = SettingsStore(context)
        var changes = 0
        val listener: () -> Unit = { changes += 1 }
        engineStore.addListener(listener)
        engineStore.addListener(listener) // Registering twice must not duplicate callbacks.

        store.savePreview(WallpaperSettings(speed = 0.1f))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, changes)
        store.commitPreview()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, changes)
        assertEquals(0.1f, engineStore.load().speed)

        engineStore.removeListener(listener)
        store.save(WallpaperSettings(speed = 0.8f))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, changes)
    }
}
