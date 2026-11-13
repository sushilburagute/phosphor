# Validation

Verified on 2026-11-13 using the Android SDK 36 toolchain and a headless Android 16 / API 36 x86_64 emulator with the Pixel launcher.

## Build and automated checks

Command: `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`

- Debug APK: built and installed successfully.
- Optimized release APK: R8 and resource shrinking completed successfully. This APK is unsigned; distribution requires a release signing key.
- 17 unit tests passed, with zero failures or errors.
- Android lint: zero errors, 25 warnings. Remaining warnings cover dependency updates, optional XML/version attributes, backup-rule guidance, and Kotlin style suggestions. They are not suppressed by a baseline.

The tests exercise separate draft/active persistence, cancellation protection, cross-instance notifications, corrupt or nonfinite settings, actual native Skia rendering of every family, animation over time, still mode, image fallback and overscan, renderer reuse, dimming, bounded private image import, invalid-file handling, and readable PNG export.

## Emulator checks

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
