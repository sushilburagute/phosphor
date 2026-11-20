package dev.sush.phosphor.ui

import android.content.Context
import dev.sush.phosphor.model.Appearance
import dev.sush.phosphor.model.WallpaperCatalog
import dev.sush.phosphor.model.WallpaperSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SavedLooksTest {
    private val preferences = RuntimeEnvironment.getApplication()
        .getSharedPreferences("look_test", Context.MODE_PRIVATE)

    @Test
    fun oldFavoritesCaptureTheirOriginalPhotoAndTuningOnce() {
        preferences.edit().clear().putStringSet("favorites", setOf("ascii-signal", "image-custom"))
            .putString("image_path", "/private/first.png").commit()
        val active = WallpaperSettings(speed = 0.24f, darken = 0.3f)
        val migrated = SavedLooks(preferences).load(active)
        assertEquals(listOf("ascii-signal", "image-custom"), migrated.map { it.settings.presetId })
        assertEquals("/private/first.png", migrated.last().settings.imagePath)
        assertEquals(active.speed, migrated.first().settings.speed)
        assertFalse(preferences.contains("favorites"))
        preferences.edit().putString("image_path", "/private/next.png").commit()
        assertEquals(migrated, SavedLooks(preferences).load(WallpaperSettings()))
    }

    @Test
    fun multipleNamedPhotosRetainTheirOwnCropAndAppearanceAcrossReload() {
        val photo = WallpaperSettings(presetId = "image-custom", imagePath = "/private/first.png",
            imageFocusX = 0.2f, imageFocusY = 0.8f, appearance = Appearance.DAY)
        val looks = listOf(
            SavedLook("a", "Left bank", photo),
            SavedLook("b", "Right bank", photo.copy(imageFocusX = 0.9f, speed = 0.1f)),
            SavedLook("c", "Dusk", photo.copy(imagePath = "/private/second.png", appearance = Appearance.NIGHT)),
        )
        preferences.edit().clear().commit()
        looks.reversed().forEach { SavedLooks(preferences).save(it, WallpaperSettings()) }
        val restored = SavedLooks(preferences).load(WallpaperSettings())
        assertEquals(looks, restored)
        val saved = galleryWallpapers(true, restored, WallpaperSettings(), "/private/unrelated.png")
        assertEquals(looks.map { it.name }, saved.map { it.title })
        assertEquals(looks.map { it.settings }, saved.map { it.settings })
        val next = photo.forPreset(WallpaperCatalog.find("image-custom"), "/private/next.png")
        assertEquals(0.5f, next.imageFocusX)
        assertEquals(0.5f, next.imageFocusY)
    }

    @Test
    fun exploreStartsWithThreeDifferentCollectionsAndKeepsTheRestOfTheCatalog() {
        val items = galleryWallpapers(false, emptyList(), WallpaperSettings(), "latest-image")
        val ids = items.map { it.preset.id }
        assertEquals(listOf("orbit-exposure", "ascii-signal", "glass-orbit"), ids.take(3))
        assertEquals(WallpaperCatalog.presets.size, ids.size)
        assertEquals(WallpaperCatalog.presets.map { it.id }.toSet(), ids.toSet())
    }

    @Test
    fun importCleanupProtectsActiveDraftLatestAndAllNamedLooks() {
        val context = RuntimeEnvironment.getApplication()
        preferences.edit().clear().commit()
        val directory = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val images = List(5) { File(directory, "import-$it.png").apply { writeBytes(byteArrayOf(1)) } }
        val active = WallpaperSettings(presetId = "image-custom", imagePath = images[0].absolutePath)
        val draft = active.copy(imagePath = images[1].absolutePath)
        val library = SavedLooks(preferences)
        library.save(SavedLook("named", "Last summer", active.copy(imagePath = images[2].absolutePath)), active)
        cleanOldImports(context, library.retainedImagePaths(active, draft, images[3].absolutePath))
        assertTrue(images.take(4).all { it.exists() })
        assertFalse(images[4].exists())
        library.remove("named", active)
        cleanOldImports(context, library.retainedImagePaths(active, draft, images[3].absolutePath))
        assertFalse(images[2].exists())
    }
}
