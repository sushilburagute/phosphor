# Phosphor

Living color for Android. An offline, open-source live wallpaper app **made by sush.dev**.

Phosphor renders its artwork on the device: curtains of aurora light, luminous ASCII beams, flowing gradients, refracted glass, abstract filaments, and grainy terrain. Every wallpaper includes the `made by sush.dev` watermark.

## Features

- Twelve original procedural presets across six collections.
- Live editor with motion speed, intensity, dimming, parallax, reduced motion, and frame-rate controls.
- Favorites and a gallery of still previews, so browsing does not run a dozen animations.
- Local image import with gentle drift and a separate layer of floating light. This is a flat-image effect, not automatic subject segmentation.
- Android's native wallpaper picker and stable wallpaper color reporting for system theming.
- Material dynamic colors on Android 12+, a themed launcher icon, and light/dark app themes.
- Export a still PNG, including the watermark, through Android's document picker.
- Rendering stops when the wallpaper is hidden; Battery Saver caps the wallpaper at 15 fps.
- No account, ads, analytics, network permission, or broad photo-library permission.

## Build

Requires JDK 17 or newer, Android SDK Platform 36, and SDK Build Tools 35.0.0. Android Studio can install the SDK components. Gradle 8.13 is provided through a checksum-pinned wrapper; first build downloads dependencies.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
```

On Windows, use `gradlew.bat`. Set `ANDROID_HOME` to your SDK directory or add a local, untracked `local.properties` with `sdk.dir=...`.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Open this repository in Android Studio to run on a device or emulator. The app supports Android 8.1 (API 27) and newer and targets API 36. Release builds require your own signing configuration; no signing keys are included.

## Pixel and Samsung One UI

Phosphor implements Android's public `WallpaperService` and `WallpaperColors` APIs. It publishes colors from a representative frame when the artwork changes, rather than changing the phone's palette on every animation frame. The app itself uses the phone's dynamic color scheme where available.

The phone controls palette choices, themed icons, wallpaper destinations, and lock-screen behavior. On Pixel, look in **Wallpaper & style**; on Galaxy, look in **Wallpaper and style → Color palette**. A Samsung Galaxy Themes package is a separate format and is not included. This project does not use private One UI APIs.

Some launchers do not send scrolling offsets or touch events. The wallpapers continue their time-based animation without them. OEM versions may not refresh colors from a live wallpaper; exporting a still and selecting a palette from it is a manual workaround, not a guaranteed override.

See [Android compatibility and device test matrix](docs/ANDROID_COMPATIBILITY.md) for preview/commit behavior and device checks still required before release. Implementing these APIs does not establish that every Pixel or Samsung version has been physically tested.

See [validation results](docs/VALIDATION.md) for the builds, automated tests, and emulator checks performed on this implementation.

## Artwork and reference images

The bundled presets are procedural code, not redistributed copies of the inspiration images. `ref-img/` is excluded from builds and version control because the supplied files have no accompanying redistribution licenses. The files remain on your local disk. You can import one on your phone to experiment with motion, or import your own artwork.

Please include provenance and a compatible license with any image asset contributed for distribution. The repository's MIT license applies to the source code and original procedural artwork; it does not grant rights to third-party reference images or personal imports.

## Project layout

```text
app/src/main/java/dev/sush/phosphor/
  model/       Presets and immutable settings
  render/      Shared Canvas artwork renderer
  platform/    Wallpaper service, draft/active persistence, system intents
  ui/          Compose gallery, editor, import/export, preview view
```

The gallery, editor, exported stills, and actual wallpaper use the same renderer. Each live wallpaper engine owns its own renderer so a system preview can coexist with the active wallpaper. Draft settings are stored separately to protect the active wallpaper when a preview is dismissed.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). CI builds the APK and runs unit tests and Android lint. Code and original procedural artwork are [MIT licensed](LICENSE).
