# Validation history

[Documentation index](README.md) · [Repeatable test commands](TESTING.md) · [Device checklist](ANDROID_COMPATIBILITY.md#device-validation-checklist)

This file preserves results reported for earlier commits and branches. Counts, warnings, APK sizes, and screenshots describe those builds, not necessarily the current checkout. Re-run the [standard checks](TESTING.md#standard-checks) to validate a new change; do not infer physical-device coverage from JVM tests or earlier emulator checks.

The most recent recorded integrated build below passed 65 tests and reported 33 lint warnings on 2026-11-14. Physical Pixel/One UI compatibility and energy profiling remain outstanding. No device results were added by the documentation reorganization.

## Branding branch merge verification

Verified on 2026-11-14 after merging `origin/main` at `18634aa` into `feat/app-branding-versioning`, using Windows, JDK 21.0.9, Android SDK Platform 36 and Build Tools 35.0.0.

- `gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease`: successful; 65 tests passed with zero failures or errors, and the 4 opt-in preview/screenshot tests were skipped.
- Android lint: zero errors and 33 warnings.
- Main's redesigned UI, day/night artwork, rendering and battery optimizations, release signing support, and CI SDK setup are retained. The gallery, editor, and Settings use the shared clickable credit and installed version; the shared renderer keeps wallpapers, previews, and exports free of branding.
- `gradlew.bat -q :app:printVersion` returned `0.1.1`. Generated BuildConfig and both debug/release APK metadata use version name `0.1.1` and code `2` from `version.properties`.
- Debug and optimized unsigned release APKs built successfully. No emulator or physical-device checks were performed for this merge.

## App branding and versioning (0.1.1)

Verified on 2026-11-13 in the `feat/app-branding-versioning` worktree:

- `gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease`: successful; 38 unit tests passed and lint reported zero errors and 24 warnings.
- The Compose credit test clicks the shared attribution, checks the exact `https://sush.dev?utm_source=phosphor` URI, and verifies the displayed version name and code.
- `gradlew.bat -q :app:printVersion` returned `0.1.1`; generated BuildConfig and debug/release APK metadata use version name `0.1.1` and code `2`.
- Native renderer and PNG export tests passed after removing the shared watermark drawing path. Explore, the editor, and Settings use the same app-only credit component.
- The optimized release APK is unsigned. No new emulator or physical-device interaction checks were performed for this change; browser dispatch and device appearance remain part of the device checklist.

The results below describe earlier implementation checks.

## Performance and battery PR

Integrated on 2026-11-14 with `origin/main` at `e3dbb12`, preserving its redesigned artwork, day/night treatments, hardware Canvas path, touch effects, and independent wake/unlock reveals.

Command: `./gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease`

- 64 tests passed with zero failures or errors; the 4 opt-in preview/screenshot tests were skipped.
- Android lint: zero errors, 33 warnings. These include dependency/style suggestions and two allocation warnings for lazily generating a gallery bitmap once per content or size change; no lint baseline was added.
- CI explicitly installs Platform 36 and Build Tools 35.0.0. This avoids the setup action's legacy default `tools` package, which failed SDK setup before Gradle could run.
- Added 14 tests for frame-request coalescing and deadlines, surface FPS limits, cancellation/resume, lock-screen power policy, preview observer cleanup, screen-off/wake behavior, battery-policy changes, and gallery bitmap reuse/invalidation (including the day/night switch).
- Native Skia artwork contact-sheet PNG and label-legibility report hashes match a baseline run of the current renderer before the calculation/shader changes. Existing motion, reveal, touch, still-mode, import, persistence, and readability tests remain passing.
- Gallery tiles cache a bitmap instead of replaying procedural paths. Cached frames are invalidated after content, treatment, or size changes. Temporary renderer resources are released; Android retains bitmap references until recorded drawing commands are finished with them.
- Power and keyguard state refresh on broadcasts/visibility changes. Input requests share the animation frame budget, screen-off rendering stops even if Android keeps the surface marked visible, and the visible lock screen uses at most 15 fps.

The initial implementation was smoke-tested on a headless Android 16 / API 36 emulator: gallery rendering, native wallpaper preview, and sleep/wake, including a dozing display with a still-visible preview engine. The emulator had an initial process-start timeout and a later delayed first activity draw before the gallery cache was added; the final cached gallery rendered successfully and its procedural GPU path-mask cache was absent. The integrated version described above was checked through the automated suite and build, without repeating those emulator checks against the redesign.

Physical-device energy, thermal, and sustained frame-time profiling remains outstanding. Compare signed release builds at fixed brightness, refresh rate, preset, motion settings, and battery state. Measure the gallery, editor, home screen, lock screen, screen-off/always-on display, and an opaque foreground app separately, including input bursts and the 20%/5% boundaries. Use Android Studio System Trace/Power Profiler or Perfetto, repeat after cooling, and check for recurring rendering work while hidden or screen-off. Frame caps and reduced work are not a measured battery-life percentage.

## PR #1 merge verification

Verified the merge of `main` at `dcd301d` on 2026-11-13 on Windows with JDK 21.0.9, Android SDK Platform 36 and Build Tools 35.0.0.

Command: `./gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease`

- Debug and unsigned release APKs built, including R8 and resource shrinking.
- 50 tests passed with zero failures; the 4 opt-in preview and screenshot tests were skipped.
- Android lint: zero errors, 29 warnings.
- Both branches' motion, battery, settings and renderer tests are retained. Added checks for independent wake/unlock treatments in both moods and disabled reveal preferences migrating from either version.
- No emulator or physical device was used for this merge verification. The visual review below describes the original production asset validation.

## Production asset validation

Verified on 2026-11-13 on Windows with JDK 17.0.21, Android SDK Platform 36 and Build Tools 35.0.0. **No emulator or physical device was used for this round.** Everything below ran on the JVM through Robolectric with native (Skia) graphics.

## Build and automated checks

Command: `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`

- Debug APK built.
- Release APK: R8 and resource shrinking completed; 1.47 MB, unsigned. Distribution requires a release signing key (see README).
- 28 unit tests passed, with zero failures; 4 opt-in preview and screenshot tests are skipped by default.
- Android lint: zero errors. Remaining warnings are dependency-update notices, KTX style suggestions, and attributes that older API levels ignore. No baseline is used.

What the tests cover:

- **Renderer:** opaque, detailed output for every family in both treatments; animation over time; still mode freezing time and parallax; image fallback and landscape overscan; renderer reuse after release; calm/dimming; the wake reveal starting from the ground and ending on the full artwork.
- **Readability:** for every preset, both treatments and three moments, at full on-device detail. Each of 16 label slots, the clock zone and the dock zone is scored by the share of pixels where a launcher label would reach WCAG 4.5:1. The report is written to `app/build/reports/phosphor-legibility.txt`.
- **Touch:** a tap visibly changes every family even at zero motion speed; pulses expire; duplicate launcher taps merge; a released drag settles to rest.
- **Power policy:** plugged-in, 20% and 5% boundaries, opting out, Battery Saver precedence, still mode; day/night resolution against the system theme.
- **Persistence and files:** draft and active separation, preview commit, the new appearance/wake/battery settings round-trip, bounded private image import, invalid files, PNG export.

## Visual review

Rendered and inspected:

- full-resolution (1080×2340) artwork for all 15 presets in both treatments, with a mock home screen overlaid;
- the gallery, editor (with and without the home-screen overlay) and settings, in light and dark;
- the adaptive launcher icon at 192, 96 and 48 px, plus the themed monochrome version.

Images are produced by the opt-in preview tests (see README).

## Earlier emulator checks

Verified on 2026-11-13 using the Android SDK 36 toolchain and a headless Android 16 / API 36 x86_64 emulator with the Pixel launcher.

- Launched the gallery, filtered to ASCII, and opened live previews.
- Opened Android's wallpaper preview and applied Signal bloom to the home screen. Confirmed the selected service with `dumpsys wallpaper` and the committed configuration in application preferences.
- Observed the app's dynamic colors change after wallpaper application.
- Opened Afterimage in the system preview and canceled. The draft remained Afterimage while the active wallpaper remained Signal bloom.
- Imported a supplied JPEG reference through the system document picker. Confirmed its private PNG copy and image wallpaper preview (this earlier build included a watermark; version 0.1.1 removes it). The import did not change the active wallpaper.
- Scrolled to editor controls and saved a still through Android's document picker. Retrieved the resulting nonempty PNG from Downloads.
- Inspected generated artwork for all twelve procedural presets and the imported-image preview.
- Checked the runtime error log: no app crashes recorded during these checks.

## Not yet verified

- On-device behavior: the hardware canvas path, frame timing and thermal load with 3,000+ trail strokes per frame, `onCommand` taps and icon drops from real launchers, screen-on and unlock reveal timing, and battery broadcasts.
- How Pixel and One UI launchers react to the dark-text/dark-theme color hints, and lock-screen behavior.
- Energy use: the frame caps are policy, not a measured battery-life result.

Use the [device test matrix](ANDROID_COMPATIBILITY.md) before release.
