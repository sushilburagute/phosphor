package dev.sush.phosphor.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.sush.phosphor.model.Appearance
import dev.sush.phosphor.model.WallpaperSettings
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

    private fun open(title: String) = compose.onNode(SemanticsMatcher("opens $title") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open $title"
    }).performClick()

    @Test
    fun galleryMoodStaysPreviewOnlyAndSavedLooksRestoreTheAppliedSettings() {
        val store = SettingsStore(compose.activity)
        store.save(WallpaperSettings(reducedMotion = true, speed = 0.31f))
        val prefs = compose.activity.getSharedPreferences("phosphor_ui", Context.MODE_PRIVATE)
        val tuned = store.load().copy(speed = 0.18f, intensity = 0.42f)
        SavedLooks(prefs).save(SavedLook("quiet", "Quiet orbits", tuned), store.load())
        compose.setContent { PhosphorApp() }
        compose.onNodeWithContentDescription("Night: emitted light").performClick()
        compose.mainClock.advanceTimeBy(800)
        open("Long exposure")
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("Night preview · Applies Day").assertIsDisplayed()
        compose.onNodeWithText("Speed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Expand preview").assertIsDisplayed()
        assertEquals(Appearance.AUTO, store.loadPreview().appearance)
        compose.onNodeWithText("Use Night").performClick()
        assertEquals(Appearance.NIGHT, store.loadPreview().appearance)
        compose.onNodeWithContentDescription("Back to wallpapers").performClick()
        compose.onNode(hasText("ASCII") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Save Signal bloom").performClick()
        compose.onNodeWithText("Saved").performClick()
        open("Quiet orbits")
        assertEquals(tuned, store.loadPreview())
        assertEquals(setOf("Signal bloom", "Quiet orbits"), SavedLooks(prefs).load(store.load()).map { it.name }.toSet())
    }
}
