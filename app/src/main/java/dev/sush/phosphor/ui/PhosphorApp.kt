package dev.sush.phosphor.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Canvas as AndroidCanvas
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperKind
import dev.sush.phosphor.model.WallpaperPreset
import dev.sush.phosphor.model.WallpaperSettings
import dev.sush.phosphor.platform.SettingsStore
import dev.sush.phosphor.platform.WallpaperActions
import dev.sush.phosphor.render.WallpaperRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private val Ink = Color(0xFF11131A)
private val Fog = Color(0xFFF2F1F6)
private val Lavender = Color(0xFFC6B9EE)

@Composable
fun PhosphorApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("phosphor_ui", Context.MODE_PRIVATE) }
    val store = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    var theme by rememberSaveable { mutableStateOf(prefs.getString("theme", "System") ?: "System") }
    var dynamic by rememberSaveable { mutableStateOf(prefs.getBoolean("dynamic", true)) }
    val dark = when (theme) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Lavender, onPrimary = Color(0xFF302448), background = Ink, surface = Ink, surfaceVariant = Color(0xFF252630), onBackground = Fog, onSurface = Fog)
        else -> lightColorScheme(primary = Color(0xFF67548E), background = Fog, surface = Fog, surfaceVariant = Color(0xFFE7E3EF), onBackground = Ink, onSurface = Ink)
    }
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    var page by rememberSaveable { mutableStateOf("Explore") }
    var category by rememberSaveable { mutableStateOf("All") }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())!!.toSet()) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf(store.loadPreview()) }
    var active by remember { mutableStateOf(store.load()) }
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

    val applyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && directPicker) {
            store.save(store.loadPreview())
            active = store.load()
            uncertainApply = false
            message("Wallpaper applied")
        } else {
            active = store.load()
            uncertainApply = active != store.loadPreview()
            if (!uncertainApply && Build.VERSION.SDK_INT >= 36) message("Wallpaper settings saved")
        }
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
            } catch (_: Exception) { message("Couldn’t open this image. Choose a JPEG, PNG, or WebP file.") }
            catch (_: OutOfMemoryError) { message("This image needs too much memory. Try a smaller image.") }
            finally { busy = false }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) {
            val snapshot = draft
            scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) { exportFrame(context, uri, snapshot) }
                    message("Still frame saved")
                } catch (_: Exception) { message("Couldn’t save the image. Try another destination.") }
                finally { busy = false }
            }
        }
    }
    BackHandler(selectedId != null) { selectedId = null; uncertainApply = false }

    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (selectedId == null) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf("Explore", "Saved", "Settings").forEach { item ->
                        NavigationBarItem(
                            selected = page == item, onClick = { page = item },
                            icon = { NavigationMark(item) }, label = { Text(item) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val selected = selectedId?.let(WallpaperCatalog::find)
                if (selected != null) {
                    Editor(
                        selected, draft, selected.id in favorites, uncertainApply,
                        onBack = { selectedId = null; uncertainApply = false },
                        onFavorite = { favorite(selected.id) },
                        onChange = { draft = it; store.savePreview(it) },
                        onApply = {
                            store.savePreview(draft)
                            directPicker = true
                            try { applyLauncher.launch(WallpaperActions.previewIntent(context)) }
                            catch (_: ActivityNotFoundException) {
                                directPicker = false
                                try { applyLauncher.launch(WallpaperActions.pickerIntent()) }
                                catch (_: ActivityNotFoundException) { message("This device has no live wallpaper picker.") }
                            }
                        },
                        onConfirm = { store.save(draft); active = store.load(); uncertainApply = false; message("Wallpaper settings saved") },
                        onExport = { exportLauncher.launch("phosphor-${selected.id}.png") },
                        onPaletteHelp = { showPaletteHelp = true },
                    )
                } else if (page == "Settings") {
                    SettingsScreen(theme, dynamic, onTheme = { theme = it; prefs.edit().putString("theme", it).apply() }, onDynamic = { dynamic = it; prefs.edit().putBoolean("dynamic", it).apply() }, onPaletteHelp = { showPaletteHelp = true })
                } else {
                    val available = WallpaperCatalog.presets.filter { it.kind != WallpaperKind.IMAGE || importedPath != null }
                    val displayed = available.filter { (page != "Saved" || it.id in favorites) && (category == "All" || it.kind.label() == category) }
                    Gallery(page == "Saved", category, displayed, favorites, active, importedPath,
                        onCategory = { category = it }, onSelect = ::edit, onFavorite = ::favorite,
                        onImport = { importLauncher.launch(arrayOf("image/*")) },
                        onExplore = { page = "Explore"; category = "All" },
                    )
                }
                if (busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)).clickable(enabled = true) {}, contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(24.dp)) { Row(Modifier.padding(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(Modifier.size(24.dp)); Text("Preparing your image…") } }
                }
            }
        }
        if (showPaletteHelp) AlertDialog(
            onDismissRequest = { showPaletteHelp = false },
            title = { Text("Colors that follow your wallpaper") },
            text = { Text("Phosphor shares a stable color palette with Android. On Pixel, choose wallpaper colors in Wallpaper & style. On Samsung, open Wallpaper and style → Color palette.\n\nAvailable palettes and lock-screen choices depend on your Android and One UI version. If your phone ignores live wallpaper colors, save a still frame, set it as a photo wallpaper, choose its palette, then reapply the live wallpaper. Some versions may still reset the palette.") },
            confirmButton = { TextButton(onClick = { showPaletteHelp = false }) { Text("Got it") } },
        )
    }
}

@Composable
private fun Gallery(saved: Boolean, category: String, wallpapers: List<WallpaperPreset>, favorites: Set<String>, active: WallpaperSettings, imagePath: String?, onCategory: (String) -> Unit, onSelect: (WallpaperPreset) -> Unit, onFavorite: (String) -> Unit, onImport: () -> Unit, onExplore: () -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(156.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(if (saved) "Your collection" else "Phosphor", fontSize = 36.sp, letterSpacing = (-1.5).sp, fontWeight = FontWeight.Medium)
                        Text(if (saved) "Keep your favorites close." else "A little movement. A different feeling.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                    if (!saved) Canvas(Modifier.size(38.dp)) {
                        val color = Lavender
                        for (i in 0..4) drawLine(color.copy(alpha = .3f + i * .15f), Offset(size.width * .15f + i * 5f, size.height * .8f), Offset(size.width * .45f + i * 5f, size.height * .2f), 3f)
                    }
                }
                if (!saved) Text("made by sush.dev", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp))
            }
        }
        if (!saved && category == "All") item(span = { GridItemSpan(maxLineSpan) }) {
            val featured = WallpaperCatalog.find("aurora-veil")
            Box(Modifier.fillMaxWidth().height(285.dp).clip(RoundedCornerShape(28.dp)).clickable { onSelect(featured) }) {
                WallpaperPreview(featured, active.copy(presetId = featured.id, imagePath = null), Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(24.dp).padding(bottom = 6.dp)) {
                    Text("Light, in slow motion", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = (-.8).sp)
                    Text("Explore the aurora collection", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 7.dp))
                }
                Surface(Modifier.align(Alignment.TopEnd).padding(18.dp), color = Color.Black.copy(alpha = .3f), shape = CircleShape) { Text("Live art", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (saved) "Saved wallpapers" else "Find your atmosphere", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All", "Aurora", "ASCII", "Gradients", "Glass", "Abstract", "Textures", "Your images")) { label ->
                        FilterChip(selected = category == label, onClick = { onCategory(label) }, label = { Text(label) })
                    }
                }
                if (!saved) OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) { Text("+  Bring an image to life") }
            }
        }
        if (wallpapers.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (saved) "A space for your favorites" else "Start with your own image", style = MaterialTheme.typography.titleMedium)
                Text(if (saved) "Tap the heart on a wallpaper to save it here." else "Import a photo or artwork and add a little motion.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = if (saved) onExplore else onImport) { Text(if (saved) "Explore wallpapers" else "Choose an image") }
            }
        }
        items(wallpapers, key = { it.id }) { preset ->
            Column {
                Box(Modifier.fillMaxWidth().aspectRatio(.77f).clip(RoundedCornerShape(20.dp)).clickable { onSelect(preset) }) {
                    WallpaperPreview(preset, active.copy(presetId = preset.id, imagePath = if (preset.kind == WallpaperKind.IMAGE) imagePath else null), Modifier.fillMaxSize())
                    Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), shape = CircleShape, color = Color.Black.copy(alpha = .35f)) {
                        IconButton(onClick = { onFavorite(preset.id) }, modifier = Modifier.semantics { contentDescription = if (preset.id in favorites) "Remove ${preset.name} from saved" else "Save ${preset.name}" }) { Heart(preset.id in favorites, Color.White) }
                    }
                }
                Text(preset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 10.dp))
                Text(preset.kind.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Editor(preset: WallpaperPreset, settings: WallpaperSettings, favorite: Boolean, uncertainApply: Boolean, onBack: () -> Unit, onFavorite: () -> Unit, onChange: (WallpaperSettings) -> Unit, onApply: () -> Unit, onConfirm: () -> Unit, onExport: () -> Unit, onPaletteHelp: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹  Back") }
            Text(preset.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
            IconButton(onClick = onFavorite, modifier = Modifier.semantics { contentDescription = if (favorite) "Remove from saved" else "Save wallpaper" }) { Heart(favorite, MaterialTheme.colorScheme.primary) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (uncertainApply) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("If you selected Phosphor in the wallpaper picker, save these settings to finish applying it. Otherwise, your previous wallpaper settings are unchanged.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onConfirm) { Text("Save wallpaper settings") }
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(max = 510.dp).aspectRatio(.72f).clip(RoundedCornerShape(30.dp))) {
                WallpaperPreview(preset, settings, Modifier.fillMaxSize(), animate = true)
                if (settings.parallax && !settings.reducedMotion) Surface(Modifier.align(Alignment.TopCenter).padding(18.dp), color = Color.Black.copy(alpha = .35f), shape = CircleShape) { Text("Touch to move", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(preset.description, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    preset.colors.take(5).forEach { color -> Box(Modifier.size(18.dp).clip(CircleShape).background(Color(color))) }
                    TextButton(onClick = onPaletteHelp) { Text("System colors") }
                }
            }
            Text("Make it yours", style = MaterialTheme.typography.titleLarge)
            LabeledSlider("Motion speed", settings.speed, enabled = !settings.reducedMotion) { onChange(settings.copy(speed = it)) }
            LabeledSlider("Intensity", settings.intensity) { onChange(settings.copy(intensity = it)) }
            LabeledSlider("Dim wallpaper", settings.darken) { onChange(settings.copy(darken = it)) }
            ToggleRow("Interactive motion", "Respond to touch and home-screen scrolling", settings.parallax, { onChange(settings.copy(parallax = it)) }, !settings.reducedMotion)
            ToggleRow("Still mode", "Keep the artwork, pause its animation", settings.reducedMotion, { onChange(settings.copy(reducedMotion = it)) })
            Column {
                Text("Frame rate", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { fps -> FilterChip(selected = settings.fps == fps, onClick = { onChange(settings.copy(fps = fps)) }, enabled = !settings.reducedMotion, label = { Text("$fps fps") }) }
                }
                Text("30 fps balances smooth motion and battery use. Animation pauses when hidden and slows in Battery Saver.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Save a still frame") }
            Text("made by sush.dev", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp))
        }
        Surface(shadowElevation = 4.dp) {
            Button(onClick = onApply, modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), contentPadding = PaddingValues(16.dp), shape = RoundedCornerShape(18.dp)) { Text("Set live wallpaper", style = MaterialTheme.typography.titleSmall) }
        }
    }
}

@Composable
private fun SettingsScreen(theme: String, dynamic: Boolean, onTheme: (String) -> Unit, onDynamic: (Boolean) -> Unit, onPaletteHelp: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Settings", fontSize = 34.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("System", "Light", "Dark").forEach { option -> FilterChip(selected = theme == option, onClick = { onTheme(option) }, label = { Text(option) }) } }
            ToggleRow("Use system colors", "Match your phone’s palette on Android 12 and later", dynamic, onDynamic, Build.VERSION.SDK_INT >= 31)
        }
        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Made for your phone", style = MaterialTheme.typography.titleLarge)
            Text("Phosphor uses Android’s live wallpaper and color APIs. Your phone controls home-screen, lock-screen, and system-palette options.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onPaletteHelp) { Text("Pixel & Samsung color guide") }
        }
        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Small footprint. No account.", style = MaterialTheme.typography.titleLarge)
            Text("Artwork is rendered on your device. Imported images stay on your phone. There are no ads, trackers, or network requests.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Open source under the MIT license. The included procedural wallpapers are created in code; imported artwork keeps its original ownership.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Text("Phosphor", fontSize = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
        Text("made by sush.dev", color = MaterialTheme.colorScheme.primary)
        Text("Live art, quietly yours.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, enabled: Boolean = true, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun Heart(filled: Boolean, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val path = Path().apply {
            moveTo(size.width * .5f, size.height * .88f)
            cubicTo(-size.width * .15f, size.height * .42f, size.width * .08f, -size.height * .04f, size.width * .5f, size.height * .27f)
            cubicTo(size.width * .92f, -size.height * .04f, size.width * 1.15f, size.height * .42f, size.width * .5f, size.height * .88f)
            close()
        }
        if (filled) drawPath(path, color) else drawPath(path, color, style = Stroke(1.8.dp.toPx()))
    }
}

@Composable
private fun NavigationMark(page: String) {
    val color = LocalContentColor.current
    if (page == "Saved") Heart(false, color) else Canvas(Modifier.size(23.dp)) {
        if (page == "Explore") {
            for (x in 0..1) for (y in 0..1) drawRoundRect(color, Offset(x * size.width * .57f, y * size.height * .57f), androidx.compose.ui.geometry.Size(size.width * .36f, size.height * .36f), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = Stroke(1.6.dp.toPx()))
        } else {
            for (i in 0..2) {
                val y = size.height * (.2f + i * .3f)
                drawLine(color, Offset(0f, y), Offset(size.width, y), 1.6.dp.toPx())
                drawCircle(color, 3.dp.toPx(), Offset(size.width * if (i == 1) .7f else .3f, y))
            }
        }
    }
}

private fun WallpaperKind.label(): String = when (this) {
    WallpaperKind.AURORA -> "Aurora"
    WallpaperKind.ASCII -> "ASCII"
    WallpaperKind.GRADIENT -> "Gradients"
    WallpaperKind.GLASS -> "Glass"
    WallpaperKind.ABSTRACT -> "Abstract"
    WallpaperKind.TEXTURE -> "Textures"
    WallpaperKind.IMAGE -> "Your images"
}

/** Decode a bounded, private copy so a revoked document permission cannot break the wallpaper. */
internal fun importImage(context: Context, uri: Uri): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    (context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read image")).use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unsupported image")
    var sample = 1
    while (bounds.outWidth / sample > 2560 || bounds.outHeight / sample > 2560) sample *= 2
    val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: throw IOException("Cannot decode image")
    var bitmap = decoded
    val directory = File(context.filesDir, "wallpapers").apply { mkdirs() }
    val target = File(directory, "import-${System.currentTimeMillis()}.png")
    var saved = false
    try {
      val orientation = try {
        context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (_: IOException) { ExifInterface.ORIENTATION_NORMAL }
      val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
    }
        if (!matrix.isIdentity) bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (bitmap !== decoded) decoded.recycle()
        target.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Cannot save image") }
        saved = true
        return target.absolutePath
    } finally {
        if (!decoded.isRecycled) decoded.recycle()
        if (!bitmap.isRecycled) bitmap.recycle()
        if (!saved) target.delete()
    }
}

private fun cleanOldImports(context: Context, keep: Set<String>) {
    val directory = File(context.filesDir, "wallpapers")
    val root = directory.canonicalFile
    directory.listFiles()?.filter { it.name.startsWith("import-") && it.extension == "png" }?.forEach { file ->
        if (file.canonicalFile.parentFile == root && file.absolutePath !in keep) file.delete()
    }
}

internal fun exportFrame(context: Context, uri: Uri, settings: WallpaperSettings) {
    val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
    val renderer = WallpaperRenderer()
    try {
        renderer.draw(AndroidCanvas(bitmap), bitmap.width, bitmap.height, 12f, WallpaperCatalog.find(settings.presetId), settings)
        context.contentResolver.openOutputStream(uri, "w")?.use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Cannot encode frame") } ?: throw IOException("Cannot write destination")
    } finally { renderer.release(); bitmap.recycle() }
}
