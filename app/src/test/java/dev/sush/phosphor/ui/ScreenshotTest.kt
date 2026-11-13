package dev.sush.phosphor.ui

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Canvas
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Screens rendered headlessly for design review and store listings. Opt-in like the artwork
 * previews: PHOSPHOR_PREVIEWS=1 ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test @Config(sdk = [35], qualifiers = "w400dp-h880dp-notnight-xhdpi")
    fun day() = capture("day")

    @Test @Config(sdk = [35], qualifiers = "w400dp-h880dp-night-xhdpi")
    fun night() = capture("night")

    /** The adaptive launcher icon, rendered from its vector layers: preview and Play icon. */
    @Test @Config(sdk = [35])
    fun launcherIcon() {
        assumeTrue(System.getenv("PHOSPHOR_PREVIEWS") == "1")
        val context = compose.activity
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Layers are 108dp with the outer 18dp reserved for masking; Play applies its own mask.
        val bleed = (size * 108f / 72f).toInt()
        val offset = (size - bleed) / 2
        for (id in listOf(dev.sush.phosphor.R.drawable.ic_launcher_background, dev.sush.phosphor.R.drawable.ic_launcher_foreground)) {
            val layer = context.getDrawable(id)!!
            layer.setBounds(offset, offset, offset + bleed, offset + bleed)
            layer.draw(canvas)
        }
        val directory = File("build/reports/screens").apply { mkdirs() }
        FileOutputStream(File(directory, "play-icon-512.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val mono = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val monoCanvas = Canvas(mono)
        monoCanvas.drawColor(0xFF1D2A5C.toInt())
        context.getDrawable(dev.sush.phosphor.R.drawable.ic_launcher_monochrome)!!.apply {
            setTint(0xFFDCE4FF.toInt()); setBounds(offset, offset, offset + bleed, offset + bleed); draw(monoCanvas)
        }
        FileOutputStream(File(directory, "themed-icon-preview.png")).use { mono.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun capture(mood: String) {
        assumeTrue(System.getenv("PHOSPHOR_PREVIEWS") == "1")
        compose.setContent { PhosphorApp() }
        compose.waitForIdle()
        save("gallery-$mood")
        compose.onNode(SemanticsMatcher("opens Long exposure") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open Long exposure" }).performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        save("editor-$mood")
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Show home screen preview")).performClick()
        compose.mainClock.advanceTimeBy(800)
        save("editor-home-$mood")
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Back to wallpapers")).performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("Settings").performClick()
        compose.mainClock.advanceTimeBy(800)
        save("settings-$mood")
    }

    private fun save(name: String) {
        compose.waitForIdle()
        // Draw the real view tree, including the AndroidView wallpaper previews.
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val directory = File("build/reports/screens").apply { mkdirs() }
        FileOutputStream(File(directory, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
