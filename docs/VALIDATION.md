# Validation

Verified on 2026-11-13 using the Android SDK 36 toolchain and a headless Android 16 / API 36 x86_64 emulator with the Pixel launcher.

## Build and automated checks

Command: `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`

- Debug APK: built and installed successfully.
- Optimized release APK: R8 and resource shrinking completed successfully. This APK is unsigned; distribution requires a release signing key.
- 37 unit tests passed, with zero failures or errors.
- Android lint: zero errors, 25 warnings. Remaining warnings cover dependency updates, optional XML/version attributes, backup-rule guidance, and Kotlin style suggestions. They are not suppressed by a baseline.

The tests exercise separate draft/active persistence, cancellation protection, cross-instance notifications, corrupt or nonfinite settings, actual native Skia rendering of every family, animation over time, still mode, image fallback and overscan, renderer reuse, dimming, bounded private image import, invalid-file handling, and readable PNG export.

The open-items update adds regression coverage for legacy defaults and persistence of battery/unlock preferences, battery parsing and charging state, automatic saving at the 20% and 5% boundaries, system saver precedence, lock/unlock interruption and repeated callbacks, frame-rate-independent touch response, settling to idle, zero-speed touch feedback, reduced-motion suppression of interaction/reveal, and combined imported-image motion coverage. A zero-delta spring update initially failed because of floating-point cancellation; zero elapsed time now returns the current state without integrating it.

## Open-items update emulator checks

- Installed and opened the updated debug APK on Android 16 / API 36.
- Inspected the phone gallery/editor and a 1280 x 800 dp window. Confirmed the wide gallery uses a navigation rail and the wide editor places artwork beside scrollable settings.
- Checked a 320 dp wide phone window at 2x font scale. The title wraps, the editor remains scrollable, sliders and control labels remain reachable, and the apply action stays visible.
- Compared artwork-region screenshots: normal motion changes between frames; setting Android animator duration scale to zero produces matching still frames; restoring it resumes motion.
- Simulated an unplugged, discharging battery at 5%; artwork-region screenshots match after receiver delivery. Restored normal battery reporting after the check. This verifies the critical-battery hold, not a measured energy saving.
- Set motion speed to 0% in the editor. Idle artwork-region screenshots match; a drag changes the artwork; screenshots match again after the interaction settles.
- Applied Northern veil to home and lock screens through Android's picker; confirmed Phosphor's active service using `dumpsys wallpaper`.
- Enabled the emulator's swipe lock screen, slept, woke, and dismissed keyguard. The wallpaper remained available and no app runtime errors were reported. Exact visual lock/unlock timing and manufacturer-specific behavior still need physical-device validation.

Changing emulator display overrides during the large-text check caused the emulator process to exit. This is recorded separately from app runtime failures; the layout check was retried after restarting the emulator.

## Earlier emulator checks

- Launched the gallery, filtered to ASCII, and opened live previews.
- Opened Android's wallpaper preview and applied Signal bloom to the home screen. Confirmed the selected service with `dumpsys wallpaper` and the committed configuration in application preferences.
- Observed the app's dynamic colors change after wallpaper application.
- Opened Afterimage in the system preview and canceled. The draft remained Afterimage while the active wallpaper remained Signal bloom.
- Imported a supplied JPEG reference through the system document picker. Confirmed its private PNG copy and image wallpaper preview with the watermark. The import did not change the active wallpaper.
- Scrolled to editor controls and saved a still through Android's document picker. Retrieved the resulting nonempty PNG from Downloads.
- Inspected generated artwork for all twelve procedural presets and the imported-image preview.
- Checked the runtime error log: no app crashes recorded during these checks.

## Not yet verified

No physical Pixel or Samsung device has been tested. One UI palette behavior, older Android picker variations, three-button navigation across OEMs, physical foldables, independent lock-screen options, and sustained power/thermal/frame-time measurements still need the [device test matrix](ANDROID_COMPATIBILITY.md).

The source implements public wallpaper color reporting and dynamic app colors; this is not certification of every manufacturer's theming behavior. Reference images are local test inputs and are not included in the APK or source distribution.
