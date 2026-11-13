# Android compatibility

Phosphor uses Android's public `WallpaperService` API, rather than a launcher-specific integration. The minimum version is Android 8.1 (API 27). Pixel and Samsung One UI are intended targets; support in code is not a claim that every device/firmware combination has been tested.

## Wallpaper colors and phone themes

The engine returns `WallpaperColors` from a small, stable render of the chosen wallpaper. Changing the artwork, imported image, intensity, or dimming invalidates the cached colors and requests a system color update. Animation frames do not repeatedly change the palette. See the [Android engine API](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine).

On Android 12 and later, wallpaper colors can feed the system's dynamic theme. A live wallpaper cannot force the user to select a palette or make every app adopt it. Pixel users manage this in **Wallpaper & style**. Samsung users manage it in **Settings → Wallpaper and style → Color palette**, as described in [Samsung's wallpaper and palette guide](https://www.samsung.com/ae/support/mobile-devices/how-to-customize-a-wallpaper-and-color-palette/). Samsung firmware decides whether/how it extracts a live wallpaper's colors; no private Samsung API or theme-package injection is used. The app can export a still frame for a manual wallpaper/palette workflow if a firmware does not offer the desired palette for live content.

Home-screen and lock-screen choices belong to the system picker. Some Android/One UI versions do not offer independent lock-screen live wallpapers. One selected Phosphor configuration is shared by its active engines; independent home and lock designs are not implemented.

## Preview and apply

Editing saves a draft independently of the active wallpaper. Opening and dismissing the system preview does not overwrite the active settings. On Android 16 (API 36), `Engine.onApplyWallpaper` commits the draft when the user selects the wallpaper. On older versions, the app commits when the picker reports success. An OEM picker may not report a useful activity result; the UI then provides a manual confirmation path instead of assuming a canceled preview was accepted. See [`ACTION_CHANGE_LIVE_WALLPAPER`](https://developer.android.com/reference/android/app/WallpaperManager#ACTION_CHANGE_LIVE_WALLPAPER) and [`onApplyWallpaper`](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine#onApplyWallpaper(int)).

## Battery and rendering

- Rendering stops when the engine is hidden or its surface is destroyed.
- Battery Saver caps the animation at 15 frames per second.
- Reduced motion draws a stationary frame and disables touch/launcher parallax.
- When the display becomes non-interactive, the engine draws a dimmed stationary frame if still visible. Availability of live content on an always-on display remains an OEM decision.
- Parallax uses launcher offsets and touch events. It does not run an accelerometer or other sensor; some launchers provide no offset events.
- Each picker/home-screen engine owns its renderer and resources. Rendering does not require network access, a foreground service, or media/storage permissions.

## Device validation checklist

Run this checklist on a physical Pixel and a physical Samsung device before claiming release compatibility. Record exact model, Android version, One UI version, launcher, refresh rate, build commit, and results. No physical-device validation has been performed by this checklist's author.

| Scenario | Expected result |
| --- | --- |
| Fresh install; open each wallpaper family | Preview displays without a crash; artwork is distinct; watermark is legible |
| Apply from app | Correct artwork reaches system preview and home screen |
| Edit while already active, then cancel picker | Previously applied wallpaper remains unchanged |
| Apply on a picker with no result | Manual confirmation can commit the chosen settings |
| Set home and/or lock screen | Only destinations the OS offers are promised |
| Open Wallpaper & style / Samsung Color palette | System receives stable wallpaper colors; record palette availability |
| Change wallpaper family or dimming | New representative colors are supplied, without per-frame palette flicker |
| Turn screen off; open another fullscreen app | No continuing render loop while hidden |
| Toggle Battery Saver while wallpaper visible | Frame rate caps at 15 FPS and resumes configured cap after disabling |
| Enable reduced motion | One stationary frame; touch/scroll does not animate |
| Rotate / fold / unfold / change resolution | Surface fills viewport; no stale bitmap size or crash |
| Preview while existing live wallpaper runs | Engines render independently without shared resource recycling |
| Import large portrait and landscape photos | Bounded decoding, correct center crop, motion stays within image edges |
| Reboot; kill app process | Applied configuration and private imported image persist |
| Export still image | Saved image matches selected artwork and includes attribution |
| Observe 10-minute run / Android Studio profiler | No sustained growth; record actual CPU, frame timing, and thermal behavior |

Use Android Studio's profiler and `adb shell dumpsys wallpaper` for diagnostics. Battery behavior and sustained FPS depend on device resolution and Canvas performance; no unmeasured battery-life claim is made.
