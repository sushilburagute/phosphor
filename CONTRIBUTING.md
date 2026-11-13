# Contributing to Phosphor

Use JDK 17+, SDK 36, and the checked-in Gradle wrapper. Run `./gradlew testDebugUnitTest lintDebug assembleDebug` before submitting a change.

Keep the app offline and minimize permissions. Avoid per-frame disk access, bitmap decoding, and unbounded allocations. Every engine must release its resources and stop scheduling frames while hidden. Keep previews separate from active settings. Keep wallpapers, artwork previews, and exported stills free of branding. Preserve the clickable `made by sush.dev` credit and version in the app UI; the credit opens `https://sush.dev?utm_source=phosphor` in the browser.

Version names and Android version codes live in `version.properties`. Follow [the release process](docs/RELEASING.md) when preparing a release and record user-facing changes in [CHANGELOG.md](CHANGELOG.md).

To add artwork, create a stable preset ID in `WallpaperCatalog` and implement the corresponding renderer behavior. Test portrait and landscape, reduced motion, dimming, touch/scroll offsets, and at least two frame times. Existing preset IDs are persisted; do not rename them without a migration.

Do not copy the local `ref-img/` folder into application assets. Contributed external assets need a source, author, redistribution license, and attribution. Never include personal images, signing keys, device logs with personal data, or SDK paths.

For platform changes, follow the device matrix in `docs/ANDROID_COMPATIBILITY.md`. State which Android and One UI versions were actually tested. Report device-specific restrictions rather than bypassing them with hidden APIs.

Bug reports should include device model, Android version, One UI version when applicable, preset, reproduction steps, and whether the problem occurs in the app preview, system preview, or applied wallpaper.
