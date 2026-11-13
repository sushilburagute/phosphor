# Architecture

[Documentation index](README.md) · [Contributing](../CONTRIBUTING.md) · [Testing](TESTING.md)

Phosphor is a single-module Kotlin Android app. Jetpack Compose provides the app UI, while Android Canvas draws the artwork. The gallery, editor, live wallpaper service, and PNG exporter share the same renderer.

## Source map

Paths below are relative to [`app/src/main/java/dev/sush/phosphor/`](../app/src/main/java/dev/sush/phosphor).

| Area | Entry points and responsibility |
| --- | --- |
| App entry | [`MainActivity.kt`](../app/src/main/java/dev/sush/phosphor/MainActivity.kt) hosts the Compose app. |
| Models | [`model/WallpaperModels.kt`](../app/src/main/java/dev/sush/phosphor/model/WallpaperModels.kt) defines settings, day/night appearance, scene families, and the preset catalog. |
| App flow | [`ui/PhosphorApp.kt`](../app/src/main/java/dev/sush/phosphor/ui/PhosphorApp.kt) coordinates navigation (gallery, editor, settings), drafts, and document/system picker results. |
| Screens | `ui/Gallery.kt`, `ui/Editor.kt`, and `ui/SettingsScreen.kt` provide browsing, editing, and app preferences. |
| Preview | [`ui/WallpaperPreview.kt`](../app/src/main/java/dev/sush/phosphor/ui/WallpaperPreview.kt) hosts animated Canvas previews and cached gallery stills. |
| Artwork | [`render/WallpaperRenderer.kt`](../app/src/main/java/dev/sush/phosphor/render/WallpaperRenderer.kt) resolves settings and dispatches to scene functions. `RenderKit.kt` supplies shared drawing helpers and frame inputs. |
| Interaction | `render/TouchField.kt`, `render/WallpaperMotion.kt`, and `platform/WallpaperReveal.kt` model touch, motion, and lock/unlock treatments. |
| Platform | [`platform/PhosphorWallpaperService.kt`](../app/src/main/java/dev/sush/phosphor/platform/PhosphorWallpaperService.kt) owns Android engines, surfaces, wallpaper colors, and lifecycle handling. |
| Persistence | [`platform/SettingsStore.kt`](../app/src/main/java/dev/sush/phosphor/platform/SettingsStore.kt) stores active and preview settings independently. |
| Files | [`ui/ImageFiles.kt`](../app/src/main/java/dev/sush/phosphor/ui/ImageFiles.kt) imports bounded private images, cleans obsolete imports, and exports PNGs. |
| Design | `ui/theme/PhosphorTheme.kt`, `ui/Components.kt`, and `ui/AppAttribution.kt` supply tokens, controls, and app-only credits. |

Other useful locations are [`app/src/test/`](../app/src/test) for JVM tests, [`app/src/main/res/`](../app/src/main/res) for resources, [`tools/store_assets.py`](../tools/store_assets.py) for store graphics, and [`version.properties`](../version.properties) for release metadata.

## Draft and active settings

`WallpaperSettings` is an immutable snapshot. `WallpaperCatalog` resolves stable preset IDs; unknown IDs fall back to the first preset. Existing IDs must remain stable or be migrated because settings and favorites persist them.

The normal apply flow is:

1. Selecting a preset creates a draft based on the active settings. Editor changes are saved under the `preview` key in the `phosphor` preferences.
2. The app preview and an Android engine with `isPreview == true` read the draft. Active engines read the `active` key.
3. **Set live wallpaper** saves the draft and opens Android's direct preview, with a general live-wallpaper chooser fallback.
4. On API 36, the engine's `onApplyWallpaper` commits the draft. The app also commits a successful direct picker result; a picker without a reliable result leaves a manual confirmation path.
5. A settings listener reloads the appropriate snapshot and refreshes rendering/colors. Canceling without confirmation leaves active settings intact.

`SettingsStore` serializes JSON, bounds numeric values, supplies defaults, and migrates older wake/unlock preferences. Keep this behavior compatible when adding fields. UI-only theme, dynamic-color preference, favorite IDs, and the most recent imported path live separately in `phosphor_ui` preferences.

## Shared rendering and ownership

`WallpaperRenderer.draw` takes a Canvas, dimensions, time, a preset, and settings, along with appearance and interaction inputs. Scenes build procedural artwork from these inputs. Imported images use the image rendering path.

Each live wallpaper engine owns its renderer and touch/motion state. The system preview can coexist with the applied wallpaper; sharing mutable renderer resources across those engines can cause stale state or premature recycling.

The editor uses an animated view. Gallery tiles cache a still bitmap, invalidating when content, treatment, or dimensions change, so scrolling does not continually replay procedural drawing. Exports create a temporary renderer and bitmap and release both after writing. Rendering uses a hardware Canvas when available, with a software fallback.

## Scheduling, power, and cleanup

`WallpaperFrameScheduler` coalesces input and animation requests within a frame budget. `WallpaperPowerPolicy` is used by the live engine and editor preview to derive frame-rate and motion limits from settings, battery state, Battery Saver, and lock state. Keep policy changes synchronized with the [user guide](USER_GUIDE.md#motion-and-battery) and [device checklist](ANDROID_COMPATIBILITY.md#device-validation-checklist).

- Surface readiness, engine visibility, display interactivity, and keyguard state are separate inputs.
- Hidden, non-interactive, and destroyed surfaces stop continuous scheduling. A final stationary screen-off frame may be drawn if a visible surface is available.
- Still mode, disabled system animations, and critical low battery suppress motion. Finite touch responses at zero ambient speed settle before scheduling stops.
- Power/keyguard state refreshes through broadcasts and visibility changes. Battery changes request work only when the effective policy changes.
- Preview views cancel callbacks and unregister observers when no longer visible. Engine destruction removes listeners, observers, broadcasts, callbacks, and renderer resources.

`PowerPolicy.kt` also contains the `Appearance.isNight` resolver and a separate frame-budget helper. For changes to current live/editor scheduling, start with the actual `WallpaperPowerPolicy` call sites.

## Wallpaper colors and local data

The service caches `WallpaperColors` from a representative render. Artwork, image, intensity, dimming, or appearance changes invalidate it; animation frames do not continually change the phone's palette. The OS and launcher choose how to consume those colors and hints. See [Android compatibility](ANDROID_COMPATIBILITY.md).

Imports are sampled to bound image dimensions, corrected for EXIF orientation, and copied as PNGs into private `files/wallpapers/`. Cleanup retains paths referenced by the current import, active settings, or draft. Exports go only to the destination selected through the document picker.

The [manifest](../app/src/main/AndroidManifest.xml) declares live wallpaper support and the service's `BIND_WALLPAPER` protection, with no network or broad media permission. Backup is disabled. The bundled font has its own [license](../app/src/main/assets/licenses/bricolage-grotesque-OFL.txt); reference images are not app resources.
