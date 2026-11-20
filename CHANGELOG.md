# Changelog

## Unreleased

- Let Echo trace respond to device music with an optional live frequency waterfall.

- Add scene-specific composition controls and curated wallpaper palettes. Expand ASCII, Glass, and Textures with Echo trace, Split spectrum, and Folded satin.
- Rotate selected saved looks daily or switch between a day and an evening look at chosen local times.

- Rework the wallpaper collections with ambient movement and layered launcher parallax. Retire wallpaper tap, drag, and icon-drop effects. Cache Long exposure’s orbital trail geometry.
- Keep a live preview in view while editing on phones, carry the gallery treatment into the editor, save tuned looks and frame imported photos.
- Animate the orbit mark on the native Android 12+ splash screen and fade into the app as soon as its first frame is ready. Older Android versions show the static mark; the exit respects disabled animations and Still mode.

## 0.1.1

- Remove creator branding from wallpapers, artwork previews, and exported still images.
- Keep the creator credit in Explore, the editor, and Settings, with a link to `https://sush.dev?utm_source=phosphor`.
- Display the app version and build code beside creator credits.
- Manage Android and displayed versions through `version.properties`; validate release tags and label CI artifacts with the version.

## 0.1.0

- Initial app with procedural live wallpapers, local image import, still export, favorites, adaptive editing, dynamic colors, and battery-aware motion.
