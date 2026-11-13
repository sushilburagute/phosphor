# Contributing to Phosphor

Start with [local setup](docs/GETTING_STARTED.md) and the [architecture guide](docs/ARCHITECTURE.md). Use the checked-in Gradle wrapper with JDK 17, SDK Platform 36, and Build Tools 35.0.0.

## Make a change

1. Create a branch from the current `main`.
2. Keep the change focused and use the existing Kotlin and Compose conventions.
3. Run the checks below. For artwork or UI changes, also generate and inspect the [opt-in previews](docs/TESTING.md#generate-artwork-and-app-previews).
4. Open a pull request explaining the problem, resulting behavior, checks run, and any remaining device checks. Include images for visual changes and the exact device/OS/launcher for on-device checks.

macOS/Linux:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Windows PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Release builds are unsigned unless you supply [release signing](docs/RELEASING.md#configure-signing). The [testing guide](docs/TESTING.md) explains reports, focused runs, and what JVM tests cannot establish. For documentation-only changes, check commands against their source configuration and verify relative links; state if app tests were not run.

## Preserve these behaviors

- Keep the app offline and permissions minimal. Avoid per-frame disk access, image decoding, and unbounded allocations.
- Give each wallpaper engine its own renderer. Release resources and stop scheduling frames when hidden or non-interactive; preserve frame caps for touch and scroll requests too.
- Keep draft settings separate from the applied wallpaper. Dismissing a system preview must not apply the draft.
- Respect still mode, disabled system animations, system Battery Saver, and automatic low-battery settings.
- Keep wallpapers, artwork previews, and exports free of branding. Preserve the clickable `made by sush.dev` credit and installed version in Explore, the editor, and Settings.
- Keep persisted preset IDs stable. Renaming an ID requires a migration, including any saved favorites.

## Add a wallpaper preset

1. Add a stable ID, name, description, interaction hint, and both night/day palettes to [`WallpaperCatalog`](app/src/main/java/dev/sush/phosphor/model/WallpaperModels.kt). Palette order is ground, accent, accent; use a deep night ground and a pale day ground.
2. Implement or extend the appropriate scene under [`render/`](app/src/main/java/dev/sush/phosphor/render). Route new families through `WallpaperRenderer` if needed. The gallery, editor, system wallpaper, and exports share this renderer.
3. Respond to taps and drags using `Frame.wave` and `Frame.dragInfluence`; honor `Frame.reveal` and the existing motion inputs. Touch must settle and should work at zero ambient speed when enabled.
4. Pass `WallpaperRendererTest.launcherLabelsStayReadableInBothMoods`. Keep bright features away from icon rows and preserve the clock/dock veils. See the [readability rules](docs/DESIGN_SYSTEM.md#wallpaper-readability).
5. Inspect both treatments, portrait and landscape, multiple animation times, still mode, calm/dimming, touch, and launcher offsets. Generate full-resolution previews before updating [store assets](docs/TESTING.md#build-store-assets).

UI changes follow the [design system](docs/DESIGN_SYSTEM.md): use theme tokens and `ic_*` vector glyphs, follow its ember color rules for the live marker, brand horizon, and saved heart, and check large text and wide windows.

Platform changes follow the [device checklist](docs/ANDROID_COMPATIBILITY.md#device-validation-checklist). State which Android and One UI versions were actually tested. Use public APIs and describe device restrictions.

## Report a bug

Include:

- Device model, Android version, launcher, and One UI version if applicable.
- App version and build code shown in Settings.
- Preset, day/night treatment, motion settings, and battery/Battery Saver state.
- Reproduction steps, expected behavior, and actual behavior.
- Whether it occurs in the app preview, Android's system preview, or the applied wallpaper.
- A screenshot or short recording if helpful, with personal information removed.

## Versions and contributed files

Version names and Android version codes live in [`version.properties`](version.properties). For a release, follow [Releasing](docs/RELEASING.md) and record user-facing changes in [CHANGELOG.md](CHANGELOG.md).

Do not copy `ref-img/` into application or store assets. Third-party contributions need a source, author, redistribution license, and attribution. The MIT license covers source and original procedural artwork; it does not license the reference images or personal imports.

Never commit personal images, signing keys or passwords, local SDK paths, or device logs containing personal data.
