# Changelog

## Unreleased

- Animate the orbit mark on the native Android 12+ splash screen and fade into the app as soon as its first frame is ready. Older Android versions show the static mark; the exit respects disabled animations and Still mode.

## 0.1.1

- Remove creator branding from wallpapers, artwork previews, and exported still images.
- Keep the creator credit in Explore, the editor, and Settings, with a link to `https://sush.dev?utm_source=phosphor`.
- Display the app version and build code beside creator credits.
- Manage Android and displayed versions through `version.properties`; validate release tags and label CI artifacts with the version.

## 0.1.0

- Initial app with procedural live wallpapers, local image import, still export, favorites, adaptive editing, dynamic colors, and battery-aware motion.
