package dev.sush.phosphor.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import dev.sush.phosphor.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [27, 35])
class SplashScreenTest {
    @Test
    @Config(qualifiers = "notnight")
    fun dayLaunch() = checkLaunchResources()

    @Test
    @Config(qualifiers = "night")
    fun nightLaunch() = checkLaunchResources()

    private fun checkLaunchResources() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Phosphor_Starting,
        )
        val attributes = context.obtainStyledAttributes(intArrayOf(
            androidx.core.splashscreen.R.attr.windowSplashScreenBackground,
            androidx.core.splashscreen.R.attr.windowSplashScreenAnimatedIcon,
            androidx.core.splashscreen.R.attr.postSplashScreenTheme,
            android.R.attr.windowLightStatusBar,
            android.R.attr.windowLightNavigationBar,
        ))
        try {
            assertEquals(context.getColor(R.color.sky), attributes.getColor(0, 0))
            assertEquals(R.style.Theme_Phosphor, attributes.getResourceId(2, 0))
            assertTrue(!attributes.getBoolean(3, true))
            assertTrue(!attributes.getBoolean(4, true))
            val icon = requireNotNull(attributes.getDrawable(1))
            // Exercise resource selection and inflation on both sides of Android 12.
            assertEquals(Build.VERSION.SDK_INT >= 31, icon is AnimatedVectorDrawable)
            val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, 432, 432)
            icon.draw(Canvas(bitmap))
            var painted = 0
            for (y in 0 until 432) for (x in 0 until 432) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 0) {
                    painted++
                    // Android masks the outer third of splash artwork. Keep the whole mark
                    // inside that safe circle, including the horizon and rounded strokes.
                    assertTrue((x - 216) * (x - 216) + (y - 216) * (y - 216) < 144 * 144)
                }
            }
            assertTrue("The launch mark must be visible", painted > 1000)
            if (System.getenv("PHOSPHOR_PREVIEWS") == "1") {
                val preview = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
                Canvas(preview).apply {
                    drawColor(context.getColor(R.color.sky))
                    drawBitmap(bitmap, 0f, 0f, null)
                }
                val output = File("build/reports/screens/splash-${Build.VERSION.SDK_INT}.png")
                output.parentFile?.mkdirs()
                output.outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            bitmap.recycle()
        } finally {
            attributes.recycle()
        }
    }
}
