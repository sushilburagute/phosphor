# Validation

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

## Not yet verified

- On-device behavior: the hardware canvas path, frame timing and thermal load at the heaviest scenes (Long exposure draws about 2,400 arcs per frame, Threshold 8,000–10,000 points, Event horizon about 10,000 line segments), `onCommand` taps and icon drops from real launchers, screen-on and unlock reveal timing, and battery broadcasts.
- How Pixel and One UI launchers react to the dark-text/dark-theme color hints, and lock-screen behavior.
- Shaded meshes (Chromatic loop, Rose silk) use `drawVertices`; Android 8.1–9 hardware canvases fall back to flat-colored cells, which is untested on a device.
- Energy use: the frame caps are policy, not a measured battery-life result.

Use the [device test matrix](ANDROID_COMPATIBILITY.md) before release.
