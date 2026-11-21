package dev.sush.phosphor.platform

import android.content.Context
import android.os.Looper
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.ScenePalette
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
        val draft = active.copy(presetId = "image-custom", intensity = 0.9f, imagePath = "/private/art.jpg")
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
            presetId = "image-custom", speed = 0.2f, intensity = 0.4f, parallax = false,
            fps = 24, darken = 0.25f, imagePath = "/private/photo.png", imageFocusX = 0.15f, imageFocusY = 0.72f, reducedMotion = true,
            wakeReveal = false, lowBatteryMode = false, unlockAnimation = false, tiltParallax = true, composition = 0.81f,
            palette = ScenePalette.CUSTOM, customColors = listOf(0xFF112233.toInt(), 0xFF77AA99.toInt(), 0xFFCC8800.toInt()),
        )
        store.savePreview(draft)
        store.commitPreview()

        assertEquals(draft, store.load())
        assertEquals(draft, SettingsStore(context).load())
    }

    @Test
    fun olderInstallationsReceiveBatteryAndUnlockDefaultsWithoutLosingArtwork() {
        context.getSharedPreferences("phosphor", Context.MODE_PRIVATE).edit().putString(
            "active", """{"presetId":"glass-prism","speed":0.25}""",
        ).commit()

        val settings = store.load()
        assertEquals("glass-prism", settings.presetId)
        assertEquals(0.25f, settings.speed)
        assertEquals(0.5f, settings.imageFocusX)
        assertEquals(0.5f, settings.imageFocusY)
        assertEquals(true, settings.lowBatteryMode)
        assertEquals(true, settings.unlockAnimation)
        assertEquals(false, settings.tiltParallax)
    }

    @Test
    fun retiredPresetsUseTheDefaultArtworkAndKeepTuningOnUpgrade() {
        val preferences = context.getSharedPreferences("phosphor", Context.MODE_PRIVATE)
        for (retired in listOf("glass-orbit", "texture-dune", "ascii-terminal")) {
            preferences.edit()
                .putString("active", """{"presetId":"$retired","appearance":"DAY","speed":0.25}""")
                .putString("preview", """{"presetId":"$retired","appearance":"AUTO","intensity":0.75}""")
                .commit()
            assertEquals(WallpaperSettings(speed = 0.25f), store.load())
            assertEquals(WallpaperSettings(intensity = 0.75f), store.loadPreview())
        }
    }

    @Test
    fun disabledRevealPreferencesFromEitherVersionStayDisabledAfterUpgrade() {
        for (key in listOf("wakeReveal", "unlockAnimation")) {
            context.getSharedPreferences("phosphor", Context.MODE_PRIVATE).edit().putString(
                "active", """{"presetId":"glass-prism","$key":false}""",
            ).commit()
            val settings = store.load()
            assertEquals("glass-prism", settings.presetId)
            assertEquals(false, settings.wakeReveal)
            assertEquals(false, settings.unlockAnimation)
        }
        val independent = WallpaperSettings(wakeReveal = false, unlockAnimation = true)
        store.save(independent)
        assertEquals(independent, SettingsStore(context).load())
    }

    @Test
    fun applyingFromPickerAtAnUnseenBoundaryKeepsTheChosenDraftUntilTheNextChange() {
        val library = SavedLooks(context.getSharedPreferences("phosphor_ui", Context.MODE_PRIVATE))
        val scheduled = SavedLook("glass", "Prism", WallpaperSettings(presetId = "glass-spectrum"))
        library.save(scheduled, store.load())
        store.saveRotation(LookRotation(RotationMode.DAILY, listOf(scheduled.id)))
        val manual = WallpaperSettings(presetId = "ascii-echo", palette = ScenePalette.EMBER)
        store.savePreview(manual)
        store.commitPreview()
        val recreated = SettingsStore(context)
        val current = recreated.currentSlot()!!
        recreated.applySlot(current)
        assertEquals(manual, recreated.load())
        recreated.applySlot(current.copy(startsAt = current.endsAt))
        assertEquals(scheduled.settings, recreated.load())
        assertEquals(manual, recreated.loadPreview())
    }

    @Test
    fun musicResponseIsOptInAndOnlyFollowsAnEchoTraceLook() {
        val echo = WallpaperCatalog.find("ascii-echo")
        val settings = WallpaperSettings(presetId = echo.id, musicReactive = true)
        assertEquals(false, SettingsStore.decode(org.json.JSONObject("""{"presetId":"ascii-echo"}""")).musicReactive)
        store.savePreview(settings)
        store.commitPreview()
        assertEquals(settings, SettingsStore(context).load())
        assertEquals(settings, settings.forPreset(echo, null))
        val other = settings.forPreset(WallpaperCatalog.find("ascii-signal"), null)
        assertEquals(false, other.musicReactive)
        val normal = WallpaperPowerProfile(30, 1f, false)
        assertEquals(true, settings.allowsMusic(normal))
        assertEquals(false, other.copy(musicReactive = true).allowsMusic(normal))
        assertEquals(false, settings.copy(reducedMotion = true).allowsMusic(normal))
        assertEquals(false, settings.copy(speed = 0f).allowsMusic(normal))
        assertEquals(false, settings.allowsMusic(normal.copy(energySaving = true)))
    }

    @Test
    fun malformedRotationAndUnknownPalettesRecoverSafely() {
        assertEquals(LookRotation(), LookRotation.decode("broken"))
        assertEquals(LookRotation(), LookRotation.decode("""{"mode":"DAY_EVENING","ids":["a","b"],"day":90,"evening":80}"""))
        val settings = SettingsStore.decode(org.json.JSONObject("""{"palette":"retired","composition":100}"""))
        assertEquals(ScenePalette.ORIGINAL, settings.palette)
        assertEquals(1f, settings.composition)
    }

    @Test
    fun malformedOrMissingCustomColorsUseTheOriginalColorsForThatArtwork() {
        val preset = WallpaperCatalog.find("glass-prism")
        val record = org.json.JSONObject().put("presetId", preset.id).put("palette", "CUSTOM")
        for (stored in listOf(null, "[]", "[-1,-1]", "[-1,-1,-1,-1]", "[-1,0,-1]", "[-1,\"red\",-1]")) {
            if (stored != null) record.put("customColors", org.json.JSONArray(stored))
            val settings = SettingsStore.decode(record)
            assertEquals(preset.colors, settings.customColors)
            assertEquals(preset.colors, settings.resolvedColors(preset))
        }
    }

    @Test
    fun rotationUpdatesTheActiveLookOncePerSlotAndPreservesDraftAndManualApplies() {
        val look = SavedLook("a", "Smoke", WallpaperSettings(presetId = "texture-satin", composition = 0.8f))
        val slot = RotationSlot(look, 100L, 200L)
        val draft = WallpaperSettings(presetId = "ascii-echo")
        store.savePreview(draft)
        store.applySlot(slot)
        assertEquals(look.settings, store.load())
        assertEquals(draft, store.loadPreview())
        store.commitPreview()
        SettingsStore(context).applySlot(slot)
        assertEquals(draft, store.load())
        store.applySlot(slot.copy(startsAt = 200L, endsAt = 300L))
        assertEquals(look.settings, store.load())
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
            """{"speed":-8,"intensity":42,"fps":0,"darken":3,"imagePath":null,"imageFocusX":-99,"imageFocusY":4}""",
        ).commit()

        val settings = store.load()
        assertEquals(0f, settings.speed)
        assertEquals(1f, settings.intensity)
        assertEquals(15, settings.fps)
        assertEquals(1f, settings.darken)
        assertNull(settings.imagePath)
        assertEquals(0f, settings.imageFocusX)
        assertEquals(1f, settings.imageFocusY)
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
    fun activeAndPreviewListenersOnlyReloadWhenTheirOwnConfigurationCanChange() {
        var activeEvents = 0
        var previewEvents = 0
        store.addListener({ activeEvents++ }, preview = false)
        store.addListener({ previewEvents++ }, preview = true)
        store.save(WallpaperSettings(speed = 0.2f))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, activeEvents)
        assertEquals(1, previewEvents) // The preview still falls back to active.

        store.savePreview(WallpaperSettings(speed = 0.8f))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, activeEvents)
        assertEquals(2, previewEvents)

        store.save(WallpaperSettings(speed = 0.4f))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, activeEvents)
        assertEquals(2, previewEvents)
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
