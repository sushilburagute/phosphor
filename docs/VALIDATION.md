# Validation history

[Documentation index](README.md) · [Repeatable test commands](TESTING.md) · [Device checklist](ANDROID_COMPATIBILITY.md#device-validation-checklist)

This file preserves results reported for earlier commits and branches. Counts, warnings, APK sizes, and screenshots describe those builds, not necessarily the current checkout. Re-run the [standard checks](TESTING.md#standard-checks) to validate a new change; do not infer physical-device coverage from JVM tests or earlier emulator checks.

## Echo trace playback visualization

On 2026-11-20, an Android 16 emulator showed the audio permission prompt when **React to music** was enabled in the editor. The preview reported waiting during silence and following playback when a separate Android player played a PCM file. With Echo trace applied to the home screen, a 130 Hz tone raised the left side of the waterfall and a 3.2 kHz tone raised the right side. AudioFlinger showed one output-mix visualizer while the wallpaper was visible and zero after Android Settings covered it. Physical phones and Bluetooth routes still need verification.

## Launcher and tilt parallax

On 2026-11-20, the rendered-frame tests verified that launcher swipes and optional tilt change all 15 presets, and that Still mode holds each one steady. A landscape test checked all four edges of an imported portrait photo with drift, page offset, and tilt combined.

With Ringfall applied on an Android 16 emulator, SurfaceFlinger measured 30.4 fps during five simulated tilts and launcher swipes at the default 30 fps setting. Android registered the accelerometer at 15 Hz, released it when Settings covered the wallpaper, and registered it again on returning home. At 20% simulated unplugged battery, the sensor stopped and the wallpaper averaged 14.8 fps over eight seconds. At 5%, SurfaceFlinger recorded no new wallpaper frames over eight seconds.

Debug and release builds passed. The unit test run passed 84 tests and skipped four opt-in previews. Debug lint reported 55 warnings and zero errors. Physical-device tilt response and battery drain remain unmeasured.

## Catalog frame timing and Ringfall

On 2026-11-20, checked all 14 alternatives to Long exposure with Phosphor applied to the home screen. The baseline included the Long exposure and frame scheduling fixes on top of `2931fbf`. Both debug builds used the same API 36 Pixel Launcher emulator at 1080 × 2400 with host NVIDIA RTX 4060 Laptop graphics. A temporary test receiver switched the applied engine through its normal settings listener. The custom photo case used a local JPEG.

Each catalog sample used the default 30 fps setting, eight seconds of warm-up and eight seconds of SurfaceFlinger wallpaper-layer statistics. Ringfall was the sustained outlier at 10.3 fps. The other 13 alternatives averaged 30.3–30.4 fps, with 95th-percentile presentation intervals in the 36–50 ms histogram buckets.

| Preset | Before (fps) | After (fps) | Before p95 (ms) | After p95 (ms) |
| --- | ---: | ---: | ---: | ---: |
| Threshold | 30.4 | 30.3 | 36 | 38 |
| Ringfall | 10.3 | 30.3 | 150 | 38 |
| First light | 30.3 | 30.3 | 36 | 38 |
| Veil nebula | 30.4 | 30.4 | 36 | 36 |
| Northern veil | 30.3 | 30.3 | 50 | 50 |
| Solstice | 30.3 | 30.2 | 38 | 38 |
| Signal bloom | 30.3 | 30.4 | 50 | 36 |
| Daybreak | 30.3 | 30.5 | 50 | 38 |
| Low tide | 30.4 | 30.4 | 48 | 36 |
| Fluted glass | 30.4 | 30.4 | 36 | 36 |
| Event horizon | 30.4 | 30.2 | 48 | 48 |
| Chromatic loop | 30.3 | 30.3 | 50 | 50 |
| Basalt | 30.4 | 30.3 | 36 | 50 |
| Your own frame (photo) | 30.4 | 30.4 | 38 | 38 |

For the Ringfall comparison below, both builds had 30 seconds of warm-up, 20 seconds on a stationary home screen, then ten horizontal launcher swipes. CPU time comes from the wallpaper process's user and system ticks summed across its threads during the stationary capture.

| Ringfall measure | Before | After |
| --- | ---: | ---: |
| Stationary wallpaper frame rate | 14.2 fps | 30.3 fps |
| Stationary 95th-percentile frame interval | 90 ms | 50 ms |
| Wallpaper frame rate during swipes | 11.7 fps | 30.3 fps |
| 95th-percentile frame interval during swipes | 106 ms | 54 ms |
| App CPU time in 20 seconds | 33.87 s | 5.91 s |

With the updated Ringfall engine and a simulated unplugged battery at 20%, the wallpaper layer averaged 14.8 fps. A separate 10-second sample at 5% recorded no wallpaper frames. Physical-device energy use remains unmeasured.

The final debug APK was reinstalled without the test receiver and the applied Ringfall service resumed at 30.3 fps. The standard Gradle checks passed 78 tests with four opt-in previews skipped. Debug lint reported 55 warnings and zero errors. The debug and unsigned release APKs both built successfully.

## Applied live wallpaper frame timing and battery behavior

On 2026-11-20, compared debug builds of `2931fbf` and the revised renderer on the same API 36 Pixel Launcher emulator at 1080 × 2400 with host NVIDIA RTX 4060 Laptop graphics. Long exposure was applied to the home screen with its default 30 fps setting. Each capture used a stationary launcher, a 30-second warm-up and 20 seconds of SurfaceFlinger wallpaper-layer time statistics.

| Wallpaper layer | Before | After |
| --- | ---: | ---: |
| Average presented frames per second | 8.86 | 29.99 |
| 95th-percentile presentation interval, histogram bucket | 200 ms | 66 ms |

With the revised build, simulated unplugged operation at 20% battery averaged 14.48 fps. Separate 10-second captures recorded no wallpaper frames at 5% battery, behind Android Settings, or with the screen off. Motion resumed on returning to the home screen at normal power. These captures measure frame submissions. Physical-device battery drain and thermal behavior remain unmeasured.

On Windows with JDK 21.0.9 and Gradle 8.13, the standard unit test, lint, debug APK and unsigned release APK tasks passed. The suite passed 78 tests and skipped four opt-in preview tests. Debug lint reported 55 warnings and zero errors.

## Main integration verification

On 2026-11-20, the passive-motion and editor changes were integrated with the wallpaper renderers from `main` at `2d4299d`. With `PHOSPHOR_PREVIEWS=1`, all 79 tests passed, including the native Skia checks for label contrast, launcher offsets, still mode and recovery from minimum live detail across all 15 procedural presets. Debug and unsigned release APKs built successfully. Android lint reported zero errors and 50 warnings.

The merged renderer produced 30 full-resolution day and night previews. The feature graphic and nine store screenshots were regenerated from this run. The frame timings below were measured on the earlier branch. Device frame timing and power use for this integration remain unmeasured.

## Ambient artwork and editor verification

Tested the working tree based on `1281fbe` on 2026-11-20 using Windows, JDK 21.0.9, Android SDK Platform 36, and Gradle 8.13.

- With `PHOSPHOR_PREVIEWS=1`, `gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease` passed all 78 tests. Lint reported zero errors and 38 warnings. The release APK was unsigned.
- Rendered all 15 procedural presets at 1080 × 2340 in both treatments and inspected the refreshed store screenshots. In the weakest sampled icon-label slot, the share of pixels meeting 4.5:1 contrast went from 47% to 66% for Northern veil and from 48% to 100% for Low tide (rounded). These are the same 16 representative slots and three animation times used in the previous renderer report.
- On the API 36 Pixel-launcher emulator, saved a named Signal bloom look, reopened it from Saved, applied it through the native wallpaper picker, and confirmed the home-screen service with `dumpsys wallpaper`.

### Long exposure frame timing

Measured the old and revised release APKs, both locally signed for testing, in the Android live wallpaper picker. Both runs used the same API 36 x86-64 emulator, host NVIDIA RTX 4060 Laptop GPU, 1080 × 2400 display, Night treatment, 30 fps selection, default speed and intensity. Each run used a 30-second warm-up followed by a 20-second Perfetto trace. The revised service could reduce scene density during warm-up and capture.

| Measure | Previous renderer | Revised renderer |
| --- | ---: | ---: |
| Median interval between RenderThread draw starts | 554 ms | 48 ms |
| 95th-percentile interval between RenderThread draw starts | 730 ms | 68 ms |
| Wallpaper layer average FPS reported by SurfaceFlinger | 1.6 | 20.0 |

The revised renderer delivered smoother motion in this workload and still fell short of the selected 30 fps. The captures cover stationary ambient motion in one emulator. Physical Pixel and Samsung frame timing, power use, thermal behavior, and launcher-specific parallax remain unmeasured. A separate SwiftShader attempt ended in a host emulator crash and is excluded from this comparison.

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
- each preset mid-gesture (swipe, hold and charged release) in both treatments, via `PHOSPHOR_PREVIEW_GESTURES=swipe,hold,burst`.

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

- On-device behavior: the hardware canvas path, frame timing and thermal load at the heaviest scenes (Long exposure draws about 2,400 arcs per frame, Threshold 8,000–10,000 points, Event horizon about 10,000 line segments), `onCommand` taps and icon drops from real launchers, screen-on and unlock reveal timing, and battery broadcasts.
- How Pixel and One UI launchers react to the dark-text/dark-theme color hints, and lock-screen behavior.
- Shaded meshes (Chromatic loop, Rose silk) use `drawVertices`; Android 8.1–9 hardware canvases fall back to flat-colored cells, which is untested on a device.
- Energy use: the frame caps are policy, not a measured battery-life result.

Use the [device test matrix](ANDROID_COMPATIBILITY.md) before release.
