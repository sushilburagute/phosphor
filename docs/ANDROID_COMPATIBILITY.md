# Android compatibility

[Documentation index](README.md) · [User guide](USER_GUIDE.md) · [Testing](TESTING.md)

Phosphor uses Android's public `WallpaperService` API, rather than a launcher-specific integration. The minimum version is Android 8.1 (API 27). Pixel and Samsung One UI are intended targets; support in code is not a claim that every device/firmware combination has been tested.

## Wallpaper colors and phone themes

The engine returns `WallpaperColors` from a small, stable render of the chosen wallpaper. Changing the artwork, imported image, intensity, or dimming invalidates the cached colors and requests a system color update. Animation frames do not repeatedly change the palette. See the [Android engine API](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine).

On Android 12 and later, wallpaper colors can feed the system's dynamic theme. A live wallpaper cannot force the user to select a palette or make every app adopt it. Pixel users manage this in **Wallpaper & style**. Samsung users manage it in **Settings → Wallpaper and style → Color palette**, as described in [Samsung's wallpaper and palette guide](https://www.samsung.com/ae/support/mobile-devices/how-to-customize-a-wallpaper-and-color-palette/). Samsung firmware decides whether/how it extracts a live wallpaper's colors; no private Samsung API or theme-package injection is used. The app can export a still frame for a manual wallpaper/palette workflow if a firmware does not offer the desired palette for live content.

Home-screen and lock-screen choices belong to the system picker. Some Android/One UI versions do not offer independent lock-screen live wallpapers. One selected Phosphor configuration is shared by its active engines; independent home and lock designs are not implemented.

## Preview and apply

Editing saves a draft independently of the active wallpaper. Opening and dismissing the system preview does not overwrite the active settings. On Android 16 (API 36), `Engine.onApplyWallpaper` commits the draft when the user selects the wallpaper. On older versions, the app commits when the picker reports success. An OEM picker may not report a useful activity result; the UI then provides a manual confirmation path instead of assuming a canceled preview was accepted. See [`ACTION_CHANGE_LIVE_WALLPAPER`](https://developer.android.com/reference/android/app/WallpaperManager#ACTION_CHANGE_LIVE_WALLPAPER) and [`onApplyWallpaper`](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine#onApplyWallpaper(int)).

## Battery and rendering

- Continuous rendering stops when the engine is hidden, its surface is destroyed, or the display is non-interactive. A visible lock-screen wallpaper is capped at 15 fps, including while charging; touch and scroll requests share that frame budget.
- System Battery Saver caps animation at 15 frames per second and reduces motion. Automatic low battery mode does the same at 20% or below while unplugged, and holds a still frame at 5% or below. The automatic behavior can be disabled per wallpaper; system Battery Saver is always honored.
- Still mode and Android's disabled-animation preference draw a stationary frame and disable animated touch/launcher parallax and unlock effects.
- When the display becomes non-interactive, the engine draws a dimmed stationary frame if still visible. Availability of live content on an always-on display remains an OEM decision.
- Interaction uses launcher offsets and touch events, with eased movement and localized light/ripple responses. It does not run an accelerometer or other sensor; some launchers provide no offset or touch events.
- A visible lock-screen wallpaper settles into a dimmer, closer composition on wake and reveals its normal appearance on unlock. Screen-off draws a final locked appearance when a surface is available, then stops. Android may hide the wallpaper or turn the display off before any shutdown animation can be shown; no private keyguard APIs or overlays are used.
- Each picker/home-screen engine owns its renderer and resources. Rendering does not require network access, a foreground service, or media/storage permissions.

## Device validation checklist

Run this checklist on a physical Pixel and a physical Samsung device before claiming release compatibility. Record exact model, Android version, One UI version, launcher, refresh rate, build commit, and results. No physical-device validation has been performed by this checklist's author.

| Scenario | Expected result |
| --- | --- |
| Fresh install; open each wallpaper family | Preview displays without a crash; artwork is distinct and free of branding |
| Tap creator credit in Explore, editor, and Settings | Browser opens `https://sush.dev?utm_source=phosphor`; adjacent version matches the installed app |
| Apply from app | Correct artwork reaches system preview and home screen |
| Edit while already active, then cancel picker | Previously applied wallpaper remains unchanged |
| Apply on a picker with no result | Manual confirmation can commit the chosen settings |
| Set home and/or lock screen | Only destinations the OS offers are promised |
| Open Wallpaper & style / Samsung Color palette | System receives stable wallpaper colors; record palette availability |
| Change wallpaper family or dimming | New representative colors are supplied, without per-frame palette flicker |
| Turn screen off; open another fullscreen app | No continuing render loop while hidden |
| Toggle Battery Saver while wallpaper visible | Frame rate caps at 15 FPS and resumes configured cap after disabling |
| Unplug at 20%, then lower to 5%; reconnect power | Automatic saving reduces motion and caps at 15 FPS; critical battery holds a still; charging restores the selected behavior |
| Disable automatic low battery mode; enable system Battery Saver | Automatic threshold is bypassed but system saving remains respected |
| Enable reduced motion | One stationary frame; touch/scroll does not animate |
| Disable system animator duration scale while visible | Artwork becomes still without leaving an animation loop running; enabling animations resumes selected motion |
| Lock, wake, and unlock; repeat with still mode and unlock effects disabled | Visible lock/wake/unlock treatment follows the setting; hidden or non-interactive engines stop scheduling frames |
| Touch/drag/release with ambient speed zero | A finite eased response plays and settles; the wallpaper does not retain a continuous idle animation loop |
| Rotate / fold / unfold / change resolution | Surface fills viewport; no stale bitmap size or crash |
| Use large text and wide windows | Settings wrap and remain reachable; wide layouts use navigation rail and editor panes |
| Preview while existing live wallpaper runs | Engines render independently without shared resource recycling |
| Import large portrait and landscape photos | Bounded decoding, correct center crop, motion stays within image edges |
| Reboot; kill app process | Applied configuration and private imported image persist |
| Export still image | Saved image matches selected artwork without a watermark |
| Observe 10-minute run / Android Studio profiler | No sustained growth; record actual CPU, frame timing, and thermal behavior |

Use Android Studio's profiler and `adb shell dumpsys wallpaper` for diagnostics. Battery behavior and sustained FPS depend on device resolution and Canvas performance; no unmeasured battery-life claim is made.
