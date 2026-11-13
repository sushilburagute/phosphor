# Testing and generated assets

[Documentation index](README.md) · [Local setup](GETTING_STARTED.md) · [Validation history](VALIDATION.md)

Run commands from the repository root using the [configured JDK and SDK](GETTING_STARTED.md#prerequisites).

## Standard checks

The [Android workflow](../.github/workflows/android.yml) runs these tasks on pushes and pull requests:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Windows PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Tests run on the JVM through JUnit and Robolectric, including native Skia graphics for renderer checks. No emulator is needed for this suite. The four opt-in artwork/screenshot tests are skipped unless `PHOSPHOR_PREVIEWS=1` is set. An unsigned release APK is expected without [signing configuration](RELEASING.md#configure-signing).

| Check | Main coverage |
| --- | --- |
| `render/WallpaperRendererTest` | Scene output, both treatments, time changes, touch at zero speed, still mode, imported images, reveal effects, resource reuse, and label contrast. |
| Motion and scheduler tests | Touch settling, frame deadlines, coalescing, cancellation, resume, and frame caps. |
| Platform tests | Battery and power policies, lock/reveal behavior, settings round trips, and draft/active separation. |
| UI/file tests | Shared attribution and version, private image handling, PNG export, preview lifecycle, and gallery cache invalidation. |

Test source is under [`app/src/test/java/dev/sush/phosphor/`](../app/src/test/java/dev/sush/phosphor). For a focused renderer run:

```sh
./gradlew testDebugUnitTest --tests '*WallpaperRendererTest*'
```

On Windows, replace `./gradlew` with `.\gradlew.bat`. Use the unfiltered standard command before submitting code changes.

## Reports

| File or directory | Produced by |
| --- | --- |
| `app/build/reports/tests/testDebugUnitTest/index.html` | Unit test task; inspect failures and skips here. |
| `app/build/test-results/testDebugUnitTest/` | JUnit XML results. |
| `app/build/reports/lint-results-debug.html` | `lintDebug`. |
| `app/build/reports/phosphor-artwork.png` | Renderer contact-sheet test. |
| `app/build/reports/phosphor-legibility.txt` | Launcher-label readability test. |
| `app/build/reports/previews/` | Opt-in full-resolution artwork PNGs. |
| `app/build/reports/screens/` | Opt-in Compose screenshots and icon renders. |

Readability checks score 16 label slots, the clock, and the dock for every preset in both treatments at three times, using a 4.5:1 contrast threshold. See [the design system](DESIGN_SYSTEM.md#wallpaper-readability) for required coverage. This is a composition check using representative label positions, not proof for every launcher layout.

## Generate artwork and app previews

macOS/Linux:

```sh
PHOSPHOR_PREVIEWS=1 ./gradlew testDebugUnitTest --tests '*ArtworkPreviewTest*' --tests '*ScreenshotTest*' --rerun-tasks
```

Windows PowerShell:

```powershell
$env:PHOSPHOR_PREVIEWS = '1'
try {
    .\gradlew.bat testDebugUnitTest --tests '*ArtworkPreviewTest*' --tests '*ScreenshotTest*' --rerun-tasks
} finally {
    Remove-Item Env:PHOSPHOR_PREVIEWS
}
```

The environment variable opts in to expensive rendering. `--rerun-tasks` prevents Gradle's up-to-date checks from skipping a run after changing only the environment. Artwork previews are 1080 × 2340 in both treatments. App screenshots include gallery, editor (with and without the home-screen overlay), and settings in light and dark, plus launcher icon renders. They are JVM renders, not device captures.

For a smaller artwork review, set `PHOSPHOR_PREVIEW_ONLY` to a preset ID prefix. `PHOSPHOR_PREVIEW_TAP=1` adds a sample touch interaction. Clear both variables before generating a complete set of distribution assets.

Inspect these outputs for readability, cropping, motion composition, and branding placement when changing artwork or UI. Run the standard unfiltered checks afterward if you previously ran a filtered test selection.

## Build store assets

Generate both artwork and app previews first. Install Python 3 with Pillow; Python is only required for this asset step, not for building the APK. An optional isolated environment on macOS/Linux:

```sh
python3 -m venv .tools/store-venv
.tools/store-venv/bin/python -m pip install Pillow
.tools/store-venv/bin/python tools/store_assets.py
```

Windows PowerShell, with the Python launcher installed:

```powershell
py -3 -m venv .tools/store-venv
.\.tools\store-venv\Scripts\python.exe -m pip install Pillow
.\.tools\store-venv\Scripts\python.exe tools/store_assets.py
```

`.tools/` is ignored by Git. The [asset builder](../tools/store_assets.py) uses generated artwork and the bundled variable font to write:

- `docs/store/play-icon-512.png` — a 512 × 512 icon from the adaptive icon layers.
- `docs/store/feature-graphic-1024x500.jpg` — a feature graphic with the Phosphor wordmark.
- `docs/store/screenshots/` — nine selected JPEGs, cropped to a maximum 2:1 long/short-side ratio.

The builder does not clear stale files and can skip missing screenshot inputs. Check its output and inspect every intended asset before committing. Generated reports stay under ignored build directories; `docs/store/` contains tracked distribution assets. No `ref-img/` files are used.

## Manual checks and recording results

JVM tests do not establish manufacturer picker behavior, actual launcher colors, hardware frame timing, energy use, or lock/always-on-display behavior. Use the [physical-device checklist](ANDROID_COMPATIBILITY.md#device-validation-checklist) for platform changes and releases.

Record the date, exact commit, toolchain, commands, pass/fail/skip counts, lint findings, and device/firmware/launcher when reporting validation. Keep observed results in [VALIDATION.md](VALIDATION.md) separate from repeatable instructions here. Existing historical results do not verify a new commit. Frame caps alone are not a measured battery-life improvement.
