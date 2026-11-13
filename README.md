# Phosphor

Living color for Android. An offline, open-source live wallpaper app **[made by sush.dev](https://sush.dev?utm_source=phosphor)**.

Phosphor renders its artwork on the device: long-exposure orbits of light, curtains of aurora, beams resolved into characters, stirrable gradients, fluted and lens glass, filaments and etched textures. Every wallpaper glows at night and prints in ink by day, following your phone's dark theme, so icons and widgets stay readable either way. Wallpapers, previews, and exported stills are free of branding. Creator credits and the installed version appear inside the app.

## Features

- Fifteen original procedural presets across seven collections: Orbit, Aurora, ASCII, Gradients, Glass, Abstract and Textures, plus your own images.
- **Day and night treatments.** Each wallpaper has a night version (emitted light) and a day version (overprinted ink on paper). Auto follows the system dark theme. The wallpaper tells launchers which treatment is showing, so they choose light or dark labels.
- **Readable by design.** Bright features avoid typical icon rows, and veils sit behind the clock and dock. An automated test measures label contrast for every preset in both treatments.
- **Interactive.** Taps send ripples, drags pull the artwork, and each family answers in its own way. Launcher tap and icon-drop commands are supported. Touch answers even at zero motion speed and settles to idle.
- **Wake reveal.** The artwork rises from dark when the screen turns on and blooms on unlock. Screen-off visibility depends on Android and the device.
- Live editor with a touchable preview, a home-screen overlay for checking readability, speed, intensity, calm (fade toward the background), frame rate, still mode and wake reveal.
- **Battery aware.** Rendering stops when hidden or the screen is off. Lock-screen animation is capped at 15 fps, and touch/scroll requests respect the frame limit. Below 20% and unplugged, motion slows to 15 fps; at 5% it holds still. System Battery Saver is always respected. Uses the GPU canvas when available.
- Favorites, and a gallery of cached still previews with a day/night switch, so browsing reuses rendered artwork.
- Local image import with gentle drift, ripples and floating light. This is a flat-image effect, not automatic subject segmentation.
- Android's native wallpaper picker and stable wallpaper color reporting for system theming. A themed (monochrome) launcher icon, a splash screen, light and dark app themes, and optional Material You colors on Android 12+.
- Export a still PNG in either treatment without a watermark through Android's document picker.
- No account, ads, analytics, network permission, broad photo-library permission, or cloud backup.

See [the design system](docs/DESIGN_SYSTEM.md) for color, type, icons, motion, readability rules and interaction.

## Build

Requires JDK 17 or newer, Android SDK Platform 36, and SDK Build Tools 35.0.0. Android Studio can install the SDK components. Gradle 8.13 is provided through a checksum-pinned wrapper; first build downloads dependencies.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
```

Full-resolution artwork previews (both treatments), app screenshots and the launcher icon render headlessly through Robolectric. Store assets are built from them:

```sh
PHOSPHOR_PREVIEWS=1 ./gradlew testDebugUnitTest --tests '*ArtworkPreviewTest*' --tests '*ScreenshotTest*' --rerun
python tools/store_assets.py   # writes docs/store/
```

Release signing reads `PHOSPHOR_KEYSTORE`, `PHOSPHOR_KEYSTORE_PASSWORD`, `PHOSPHOR_KEY_ALIAS` and `PHOSPHOR_KEY_PASSWORD` from Gradle properties or the environment. Without them, `assembleRelease` produces an unsigned APK.

On Windows, use `gradlew.bat`. Set `ANDROID_HOME` to your SDK directory or add a local, untracked `local.properties` with `sdk.dir=...`.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Open this repository in Android Studio to run on a device or emulator. The app supports Android 8.1 (API 27) and newer and targets API 36. Release builds require your own signing configuration; no signing keys are included.

## Versioning

[`version.properties`](version.properties) is the single source for the semantic version name and Android version code. Gradle uses it for APK metadata and the version displayed beside the app's clickable creator credit. Run `./gradlew -q :app:printVersion` to read the current version.

See [release steps](docs/RELEASING.md) for version bumps, tags, signing, and CI behavior, and [CHANGELOG.md](CHANGELOG.md) for changes by version.

## Pixel and Samsung One UI

Phosphor implements Android's public `WallpaperService` and `WallpaperColors` APIs. It publishes colors from a representative frame when the artwork changes, rather than changing the phone's palette on every animation frame. The app itself uses the phone's dynamic color scheme where available.

The phone controls palette choices, themed icons, wallpaper destinations, and lock-screen behavior. On Pixel, look in **Wallpaper & style**; on Galaxy, look in **Wallpaper and style → Color palette**. A Samsung Galaxy Themes package is a separate format and is not included. This project does not use private One UI APIs.

Some launchers do not send scrolling offsets or touch events. The wallpapers continue their time-based animation without them. OEM versions may not refresh colors from a live wallpaper; exporting a still and selecting a palette from it is a manual workaround, not a guaranteed override.

See [Android compatibility and device test matrix](docs/ANDROID_COMPATIBILITY.md) for preview/commit behavior and device checks still required before release. Implementing these APIs does not establish that every Pixel or Samsung version has been physically tested.

See [validation results](docs/VALIDATION.md) for the builds, automated tests, and emulator checks performed on this implementation.

See [design and motion decisions](docs/DESIGN_PRINCIPLES.md) for the Android and Material guidance behind the layout, animation, interaction, and power policies.

## Artwork and reference images

The bundled presets are procedural code, not redistributed copies of the inspiration images. `ref-img/` is excluded from builds and version control because the supplied files have no accompanying redistribution licenses. The files remain on your local disk. You can import one on your phone to experiment with motion, or import your own artwork.

Please include provenance and a compatible license with any image asset contributed for distribution. The repository's MIT license applies to the source code and original procedural artwork; it does not grant rights to third-party reference images or personal imports.

## Project layout

```text
app/src/main/java/dev/sush/phosphor/
  model/       Presets (night and day palettes) and immutable settings
  render/      Shared Canvas renderer, one scene per family, touch field
  platform/    Wallpaper service, power policy, draft/active persistence, system intents
  ui/          Compose gallery, editor, settings, components, import/export, preview view
  ui/theme/    Design tokens: color, type, shape, spacing, motion
docs/store/    Play icon, feature graphic and screenshots
tools/         Store asset builder
```

The gallery, editor, exported stills, and actual wallpaper use the same renderer. Each live wallpaper engine owns its own renderer so a system preview can coexist with the active wallpaper. Draft settings are stored separately to protect the active wallpaper when a preview is dismissed.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). CI builds the APK and runs unit tests and Android lint. Code and original procedural artwork are [MIT licensed](LICENSE).
