package dev.sush.phosphor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import dev.sush.phosphor.BuildConfig
import dev.sush.phosphor.R
import dev.sush.phosphor.platform.RELEASES_URL
import dev.sush.phosphor.platform.isNewerRelease
import dev.sush.phosphor.platform.latestReleaseVersion
import kotlinx.coroutines.CancellationException

@Composable
internal fun UpdateCheck(fetchLatest: suspend () -> String? = ::latestReleaseVersion) {
    var attempt by remember { mutableIntStateOf(0) }
    var status by remember { mutableIntStateOf(R.string.update_checking) }
    var available by remember { mutableStateOf<String?>(null) }
    val browser = LocalUriHandler.current
    LaunchedEffect(attempt) {
        try {
            val latest = fetchLatest()
            when {
                latest == null -> status = R.string.update_no_releases
                isNewerRelease(latest, BuildConfig.VERSION_NAME) -> available = latest
                else -> status = R.string.update_current
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            status = R.string.update_unavailable
        }
    }
    val version = available
    if (version != null) {
        Text(stringResource(R.string.update_available, version), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { browser.openUri("$RELEASES_URL/tag/v$version") }) {
            Text(stringResource(R.string.update_view))
        }
    } else {
        Text(stringResource(status), style = MaterialTheme.typography.bodyMedium)
        if (status != R.string.update_checking) {
            OutlinedButton(onClick = {
                status = R.string.update_checking
                attempt++
            }) {
                Text(stringResource(R.string.update_retry))
            }
        }
    }
}
