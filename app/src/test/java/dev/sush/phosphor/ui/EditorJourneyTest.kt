package dev.sush.phosphor.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.sush.phosphor.model.ScenePalette
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperSettings
import dev.sush.phosphor.platform.RotationMode
import dev.sush.phosphor.platform.SavedLook
import dev.sush.phosphor.platform.SavedLooks
import dev.sush.phosphor.platform.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h840dp-notnight")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun open(title: String) = compose.onNode(hasText(title) and hasClickAction()).performClick()

    @Test
    fun savedPhotoRestoresItsCustomPaletteAfterComparingBuiltIns() {
        val preset = WallpaperCatalog.find("image-custom")
        val store = SettingsStore(compose.activity)
        val custom = listOf(0xFF172B31.toInt(), 0xFFAABB11.toInt(), 0xFFFEDC70.toInt())
        val original = WallpaperSettings(presetId = preset.id, palette = ScenePalette.CUSTOM,
            customColors = custom, reducedMotion = true)
        val library = SavedLooks(compose.activity.getSharedPreferences("phosphor_ui", Context.MODE_PRIVATE))
        library.save(SavedLook("photo", "Photo colors", original), store.load())
        compose.setContent { PhosphorApp() }
        open("Saved")
        open("Photo colors")
        compose.onNodeWithText("Shadows").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Highlights").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Floating lights") and hasClickAction()).performScrollTo().assertIsDisplayed()
        assertEquals(original, store.loadPreview())
        compose.onNodeWithText("1 color").performScrollTo().performClick()
        compose.onNodeWithText("Tint").performScrollTo().assertIsDisplayed()
        assertEquals(listOf(custom[1]), store.loadPreview().selectedColors(preset))
        compose.onNodeWithText("2 colors").performScrollTo().performClick()
        assertEquals(custom.take(2), store.loadPreview().selectedColors(preset))
        compose.onNodeWithText("3 colors").performScrollTo().performClick()
        assertEquals(original, store.loadPreview())
        compose.onNodeWithText("Lagoon").performScrollTo().performClick()
        assertEquals(ScenePalette.LAGOON.colors, store.loadPreview().resolvedColors(preset))
        compose.onNodeWithText("Original").performScrollTo().performClick()
        assertEquals(preset.colors, store.loadPreview().resolvedColors(preset))
        compose.onNodeWithText("Custom").performScrollTo().performClick()
        assertEquals(custom, store.loadPreview().resolvedColors(preset))
    }

    @Test
    fun rotationCanBeConfiguredAndDisabledFromSaved() {
        val store = SettingsStore(compose.activity)
        val prefs = compose.activity.getSharedPreferences("phosphor_ui", Context.MODE_PRIVATE)
        val library = SavedLooks(prefs)
        val active = WallpaperSettings(reducedMotion = true)
        store.save(active)
        library.save(SavedLook("glass", "My prism", active.copy(presetId = "glass-spectrum")), active)
        library.save(SavedLook("silk", "My satin", active.copy(presetId = "texture-satin")), active)
        compose.setContent { PhosphorApp() }
        open("Saved")
        open("Rotation")
        open("Daily")
        open("Save")
        assertEquals(RotationMode.DAILY, store.rotation().mode)
        assertEquals(listOf("silk", "glass"), store.rotation().lookIds)
        assertEquals(active, store.load())

        open("Rotation")
        open("Day and evening")
        open("Save")
        assertEquals(RotationMode.DAY_EVENING, store.rotation().mode)
        open("Rotation")
        open("Off")
        open("Save")
        assertEquals(RotationMode.OFF, store.rotation().mode)
    }

    @Test
    fun galleryFiltersAndSavesWithoutOpeningTheEditorAndSavedLooksRestoreTuning() {
        val store = SettingsStore(compose.activity)
        store.save(WallpaperSettings(reducedMotion = true, speed = 0.31f))
        val prefs = compose.activity.getSharedPreferences("phosphor_ui", Context.MODE_PRIVATE)
        val tuned = store.load().copy(speed = 0.18f, intensity = 0.42f, composition = 0.8f)
        SavedLooks(prefs).save(SavedLook("quiet", "Quiet orbits", tuned), store.load())
        compose.setContent { PhosphorApp() }
        compose.onNodeWithText("Import photo").assertIsDisplayed()
        open("Long exposure")
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("Speed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Expand preview").assertIsDisplayed()
        compose.onNodeWithText("Use Day").assertDoesNotExist()
        assertEquals(store.load(), store.loadPreview())
        compose.onNodeWithContentDescription("Back to wallpapers").performClick()
        compose.onNode(hasText("ASCII") and hasClickAction()).performClick()
        compose.onNodeWithText("Long exposure").assertDoesNotExist()
        compose.onNodeWithContentDescription("Save Signal bloom").performClick()
        compose.onNodeWithText("Import photo").assertIsDisplayed()
        compose.onNodeWithText("Saved").performClick()
        open("Quiet orbits")
        assertEquals(tuned, store.loadPreview())
        assertEquals(setOf("Signal bloom", "Quiet orbits"), SavedLooks(prefs).load(store.load()).map { it.name }.toSet())
    }
}
