package dev.sush.phosphor.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.sush.phosphor.BuildConfig
import dev.sush.phosphor.platform.RELEASES_URL
import dev.sush.phosphor.platform.isNewerRelease
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateCheckTest {
    @get:Rule val compose = createComposeRule()

    @Test fun comparesSemanticVersionsNumericallyAndIgnoresBuildMetadata() {
        assertTrue(isNewerRelease("0.10.0", "0.9.12+gabc123"))
        assertFalse(isNewerRelease("1.2.3", "1.2.3+gabc123"))
        assertFalse(isNewerRelease("1.12.3", "2.0.0"))
    }

    @Test fun retriesOfflineCheckAndOpensTheNewerRelease() {
        val next = BuildConfig.VERSION_NAME.substringBefore('.').toBigInteger().inc().toString() + ".0.0"
        var calls = 0
        var opened: String? = null
        val browser = object : UriHandler {
            override fun openUri(uri: String) { opened = uri }
        }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides browser) {
                UpdateCheck {
                    when (++calls) {
                        1 -> throw IOException("offline")
                        2 -> null
                        3 -> BuildConfig.VERSION_NAME.substringBefore('+')
                        else -> next
                    }
                }
            }
        }
        for (message in listOf("Couldn’t check for updates. Try again later.", "No releases have been published yet.", "You’re up to date.")) {
            compose.onNodeWithText(message).assertExists()
            compose.onNodeWithText("Check again").performClick()
        }
        compose.onNodeWithText("Phosphor $next is available.").assertExists()
        compose.onNodeWithText("View release and download APK").performClick()
        assertEquals("$RELEASES_URL/tag/v$next", opened)
    }
}
