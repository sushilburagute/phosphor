package dev.sush.phosphor.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sush.phosphor.BuildConfig
import dev.sush.phosphor.R

@Composable
fun SettingsScreen(theme: String, dynamic: Boolean, onTheme: (String) -> Unit, onDynamic: (Boolean) -> Unit, onPaletteHelp: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp).widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.displayMedium)

        Section("App appearance", R.drawable.ic_auto) {
            Segmented(
                listOf(
                    Triple("System", "System", R.drawable.ic_auto),
                    Triple("Light", "Light", R.drawable.ic_sun),
                    Triple("Dark", "Dark", R.drawable.ic_moon),
                ),
                theme, onTheme,
            )
            ToggleRow(
                "Use your phone’s colors",
                if (Build.VERSION.SDK_INT >= 31) "Tint the app with your Material You palette instead of Phosphor’s ink and phosphor blue."
                else "Available on Android 12 and later.",
                dynamic, onDynamic, Build.VERSION.SDK_INT >= 31,
            )
            Text("This changes the app only. Each wallpaper has its own day and night setting.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
        Section("Your phone", R.drawable.ic_palette) {
            Text(
                "Phosphor uses Android’s live wallpaper and color APIs. It tells your launcher whether the artwork is light or dark so labels stay readable. Your phone decides home-screen, lock-screen and palette options.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onPaletteHelp) { Text("Pixel and Samsung color guide") }
        }
        HorizontalDivider()
        Section("Private by design", R.drawable.ic_info) {
            Text(
                "Every wallpaper is drawn on your phone. Imported images stay on your phone. No account, ads, trackers or network access.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Open source under the MIT license. Typeset in Bricolage Grotesque, used under the SIL Open Font License 1.1. Imported artwork keeps its original ownership.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Glyph(R.drawable.ic_explore, null, Modifier.size(28.dp), MaterialTheme.colorScheme.primary)
            Text("Phosphor ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall)
            Text("made by sush.dev", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
