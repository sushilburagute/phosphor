package dev.sush.phosphor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sush.phosphor.BuildConfig
import dev.sush.phosphor.R

/** App chrome only: attribution is never drawn into wallpaper artwork. */
@Composable
internal fun AppAttribution(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val creatorUrl = stringResource(R.string.creator_url)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = { uriHandler.openUri(creatorUrl) },
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text(stringResource(R.string.creator_credit))
        }
        Text(
            stringResource(R.string.app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
