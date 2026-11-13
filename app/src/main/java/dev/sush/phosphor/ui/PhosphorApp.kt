package dev.sush.phosphor.ui

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sush.phosphor.R
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperKind
import dev.sush.phosphor.model.WallpaperPreset
import dev.sush.phosphor.platform.SettingsStore
import dev.sush.phosphor.platform.WallpaperActions
import dev.sush.phosphor.platform.isNight
import dev.sush.phosphor.ui.theme.Motion
import dev.sush.phosphor.ui.theme.PhosphorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Page(val label: String, val icon: Int) {
    Explore("Explore", R.drawable.ic_explore),
    Saved("Saved", R.drawable.ic_saved),
    Settings("Settings", R.drawable.ic_settings),
}

@Composable
fun PhosphorApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("phosphor_ui", Context.MODE_PRIVATE) }
    val store = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    var theme by rememberSaveable { mutableStateOf(prefs.getString("theme", "System") ?: "System") }
    // Phosphor's own palette is the default; Material You remains one switch away.
    var dynamic by rememberSaveable { mutableStateOf(prefs.getBoolean("dynamic", false)) }
    val systemNight = isSystemInDarkTheme()
    val dark = when (theme) { "Dark" -> true; "Light" -> false; else -> systemNight }
    // The gallery's day/night switch starts on whatever the wallpaper would show right now.
    var galleryNight by rememberSaveable { mutableStateOf(systemNight) }
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    var page by rememberSaveable { mutableStateOf(Page.Explore) }
    var category by rememberSaveable { mutableStateOf("All") }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())!!.toSet()) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf(store.loadPreview()) }
    var active by remember { mutableStateOf(store.load()) }
    var live by remember { mutableStateOf(isPhosphorLive(context)) }
    var importedPath by remember { mutableStateOf(prefs.getString("image_path", null)) }
    var busy by remember { mutableStateOf(false) }
    var uncertainApply by rememberSaveable { mutableStateOf(false) }
    var directPicker by rememberSaveable { mutableStateOf(true) }
    var showPaletteHelp by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }
    fun edit(preset: WallpaperPreset) {
        draft = active.copy(presetId = preset.id, imagePath = if (preset.kind == WallpaperKind.IMAGE) importedPath else null)
        store.savePreview(draft)
        selectedId = preset.id
        uncertainApply = false
    }
    fun favorite(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
        prefs.edit().putStringSet("favorites", favorites).apply()
    }

    DisposableEffect(store) {
        val listener: () -> Unit = { active = store.load() }
        store.addListener(listener)
        onDispose { store.removeListener(listener) }
    }
    // Returning from the system picker or settings: re-check which wallpaper is live.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) live = isPhosphorLive(context) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val applyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && directPicker) {
            store.save(store.loadPreview())
            active = store.load()
            uncertainApply = false
            message("Live wallpaper set")
        } else {
            active = store.load()
            uncertainApply = active != store.loadPreview()
            if (!uncertainApply && Build.VERSION.SDK_INT >= 36) message("Wallpaper settings saved")
        }
        live = isPhosphorLive(context)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val path = withContext(Dispatchers.IO) { importImage(context, uri) }
                importedPath = path
                prefs.edit().putString("image_path", path).apply()
                draft = active.copy(presetId = "image-custom", imagePath = path)
                store.savePreview(draft)
                selectedId = "image-custom"
                uncertainApply = false
                val keep = setOfNotNull(path, store.load().imagePath, store.loadPreview().imagePath)
                withContext(Dispatchers.IO) { cleanOldImports(context, keep) }
            } catch (_: Exception) { message("This image couldn’t be opened. Choose a JPEG, PNG or WebP file.") }
            catch (_: OutOfMemoryError) { message("This image is too large to open. Try a smaller one.") }
            finally { busy = false }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) {
            val snapshot = draft
            val night = snapshot.appearance.isNight(systemNight)
            scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) { exportFrame(context, uri, snapshot, night) }
                    message("Still image saved")
                } catch (_: Exception) { message("The image couldn’t be saved there. Try another folder.") }
                finally { busy = false }
            }
        }
    }
    BackHandler(selectedId != null) { selectedId = null; uncertainApply = false }

    val savedScreens = rememberSaveableStateHolder()
    val motionEnabled = !draft.reducedMotion && !active.reducedMotion
    PhosphorTheme(night = dark, dynamic = dynamic) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rail = maxWidth >= 600.dp && maxHeight >= 480.dp && selectedId == null
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (selectedId == null && !rail) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Page.entries.forEach { item ->
                            NavigationBarItem(
                                selected = page == item, onClick = { page = item },
                                icon = { Glyph(if (item == Page.Saved && page == item) R.drawable.ic_saved_filled else item.icon, null) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    if (rail) NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Page.entries.forEach { item ->
                            NavigationRailItem(
                                selected = page == item, onClick = { page = item },
                                icon = { Glyph(item.icon, null) }, label = { Text(item.label) },
                            )
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        AnimatedContent(
                            targetState = selectedId ?: page.name,
                            transitionSpec = {
                                val duration = if (motionEnabled) Motion.CALM else 0
                                val quick = if (motionEnabled) Motion.QUICK else 0
                                when {
                                    WallpaperCatalog.presets.any { it.id == targetState } ->
                                        (fadeIn(tween(duration)) + slideInHorizontally(tween(duration, easing = Motion.Arrive)) { it / 12 }) togetherWith fadeOut(tween(quick))
                                    WallpaperCatalog.presets.any { it.id == initialState } ->
                                        fadeIn(tween(duration)) togetherWith (fadeOut(tween(quick)) + slideOutHorizontally(tween(duration, easing = Motion.Arrive)) { it / 12 })
                                    else -> fadeIn(tween(duration)) togetherWith fadeOut(tween(quick))
                                }
                            },
                            label = "screen",
                        ) { target ->
                            savedScreens.SaveableStateProvider(target) {
                                val shown = WallpaperCatalog.presets.firstOrNull { it.id == target }
                                when {
                                    shown != null -> Editor(
                                        shown, draft, shown.id in favorites, uncertainApply, systemNight,
                                        onBack = { selectedId = null; uncertainApply = false },
                                        onFavorite = { favorite(shown.id) },
                                        onChange = { draft = it; store.savePreview(it) },
                                        onApply = {
                                            store.savePreview(draft)
                                            directPicker = true
                                            try { applyLauncher.launch(WallpaperActions.previewIntent(context)) }
                                            catch (_: ActivityNotFoundException) {
                                                directPicker = false
                                                try { applyLauncher.launch(WallpaperActions.pickerIntent()) }
                                                catch (_: ActivityNotFoundException) { message("This phone has no live wallpaper picker.") }
                                            }
                                        },
                                        onConfirm = { store.save(draft); active = store.load(); uncertainApply = false; message("Wallpaper settings saved") },
                                        onExport = { exportLauncher.launch("phosphor-${shown.id}.png") },
                                        onPaletteHelp = { showPaletteHelp = true },
                                    )
                                    target == Page.Settings.name -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                        SettingsScreen(
                                            theme, dynamic,
                                            onTheme = { theme = it; prefs.edit().putString("theme", it).apply() },
                                            onDynamic = { dynamic = it; prefs.edit().putBoolean("dynamic", it).apply() },
                                            onPaletteHelp = { showPaletteHelp = true },
                                        )
                                    }
                                    else -> {
                                        val saved = target == Page.Saved.name
                                        val available = WallpaperCatalog.presets.filter { it.kind != WallpaperKind.IMAGE || importedPath != null }
                                        val displayed = available.filter { (!saved || it.id in favorites) && (category == "All" || it.kind.label() == category) }
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                            Gallery(
                                                saved, category, displayed, favorites, active, importedPath,
                                                liveId = if (live) active.presetId else null,
                                                night = galleryNight, onNight = { galleryNight = it },
                                                onCategory = { category = it }, onSelect = ::edit, onFavorite = ::favorite,
                                                onImport = { importLauncher.launch(arrayOf("image/*")) },
                                                onExplore = { page = Page.Explore; category = "All" },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (busy) Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Surface(shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text("Working on your image…")
                }
            }
        }
        if (showPaletteHelp) AlertDialog(
            onDismissRequest = { showPaletteHelp = false },
            icon = { Glyph(R.drawable.ic_palette, null) },
            title = { Text("Match your phone’s colors") },
            text = {
                Text(
                    "Phosphor tells Android its colors, and whether it’s showing day or night, so your launcher can choose readable labels.\n\n" +
                        "On Pixel, choose colors in Wallpaper & style. On Samsung, open Wallpaper and style, then Color palette.\n\n" +
                        "Some phones ignore live wallpaper colors. If yours does, save a still image, set it as a photo wallpaper, choose its palette, then set Phosphor again.",
                )
            },
            confirmButton = { TextButton(onClick = { showPaletteHelp = false }) { Text("Done") } },
        )
    }
}

/** True when Phosphor's service is the current home-screen wallpaper. */
private fun isPhosphorLive(context: Context): Boolean = try {
    WallpaperManager.getInstance(context).wallpaperInfo?.packageName == context.packageName
} catch (_: RuntimeException) {
    false
}
