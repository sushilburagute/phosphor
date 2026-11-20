package dev.sush.phosphor.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sush.phosphor.platform.EchoMusicInput
import dev.sush.phosphor.platform.MusicStatus

@Composable
internal fun MusicResponseControl(reactive: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val permission = Manifest.permission.RECORD_AUDIO
    fun hasPermission() = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasPermission()) }
    var denied by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = hasPermission()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        denied = !it
        onChange(it)
    }
    val status by EchoMusicInput.status.collectAsState()
    val description = when {
        !granted && (denied || reactive) -> "Allow microphone permission to visualize device playback. The ambient trace is active."
        !reactive -> "Follow audio playing on this device. Android asks for microphone permission to read playback frequencies."
        status == MusicStatus.RECEIVING -> "Following playback. Bass is on the left and treble is on the right."
        status == MusicStatus.UNAVAILABLE -> "This audio output is unavailable. The ambient trace is active."
        status == MusicStatus.WAITING -> "Waiting for playback. The trace drifts until a signal arrives."
        else -> "Music response pauses in Still mode, at zero speed, and while saving battery."
    }
    ToggleRow("React to music", description, reactive && granted, { enabled ->
        if (enabled && !hasPermission()) request.launch(permission) else onChange(enabled)
    })
    if (!granted && (denied || reactive)) TextButton(onClick = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }) { Text("Open app permissions") }
}
