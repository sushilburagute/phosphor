package dev.sush.phosphor.platform

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object WallpaperActions {
    fun previewIntent(context: Context): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(context, PhosphorWallpaperService::class.java),
        )

    fun pickerIntent(): Intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)

    fun settingsIntent(): Intent = Intent(Intent.ACTION_SET_WALLPAPER)

    fun isActive(context: Context): Boolean = try {
        WallpaperManager.getInstance(context).wallpaperInfo?.component ==
            ComponentName(context, PhosphorWallpaperService::class.java)
    } catch (_: SecurityException) {
        false
    }
}
