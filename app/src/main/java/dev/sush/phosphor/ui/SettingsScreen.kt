package dev.sush.phosphor.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sush.phosphor.BuildConfig
import dev.sush.phosphor.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    theme: String, dynamic: Boolean,
    onBack: () -> Unit, onTheme: (String) -> Unit, onDynamic: (Boolean) -> Unit, onPaletteHelp: () -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = scheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Glyph(R.drawable.ic_back, "Back to wallpapers") } },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = scheme.background, scrolledContainerColor = scheme.surfaceContainerLow),
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val gutter = WindowWidth.of(maxWidth).gutter
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = gutter).padding(top = 8.dp, bottom = 40.dp),
            ) {
                SectionLabel("App")
                Group {
                    item {
                        ChoiceItem("Theme", "Changes the app only. Each wallpaper has its own day and night setting.") {
                            Segmented(listOf(Triple("System", "System", null), Triple("Light", "Light", null), Triple("Dark", "Dark", null)), theme, onTheme)
                        }
                    }
                    item {
                        ToggleItem(
                            "Use your phone’s colors",
                            if (Build.VERSION.SDK_INT >= 31) "Tint the app with your Material You palette." else "Available on Android 12 and later.",
                            dynamic, onDynamic, Build.VERSION.SDK_INT >= 31,
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
                SectionLabel("Your phone")
                Group {
                    item {
                        LinkItem("Match your phone’s colors", "How Pixel and Samsung pick up Phosphor’s colors, and readable labels.", onClick = onPaletteHelp)
                    }
                }
                Spacer(Modifier.height(28.dp))
                SectionLabel("About")
                Group {
                    item { LinkItem("Private by design", "Drawn on your phone. Imported images stay on your phone. No account, ads, trackers or network access.") }
                    item { LinkItem("Open source", "MIT license. Typeset in Bricolage Grotesque under the SIL Open Font License 1.1.") }
                    item { LinkItem("Version", trailing = BuildConfig.VERSION_NAME) }
                }
                Spacer(Modifier.height(32.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Phosphor", style = MaterialTheme.typography.titleLarge)
                        Text("made by sush.dev", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
