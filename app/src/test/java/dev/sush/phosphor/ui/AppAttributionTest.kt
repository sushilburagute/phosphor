package dev.sush.phosphor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.sush.phosphor.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppAttributionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun creditOpensTrackedCreatorUrlAndShowsInstalledVersion() {
        var openedUrl: String? = null
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) { openedUrl = uri }
        }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                MaterialTheme { AppAttribution() }
            }
        }
        compose.onNodeWithText("made by sush.dev").assertHasClickAction().performClick()
        assertEquals("https://sush.dev?utm_source=phosphor", openedUrl)
        compose.onNodeWithText("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})").assertExists()
    }
}
