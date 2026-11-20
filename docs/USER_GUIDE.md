# Using Phosphor

[Documentation index](README.md) · [Build and install](GETTING_STARTED.md) · [Android compatibility](ANDROID_COMPATIBILITY.md)

## Apply a wallpaper

1. Open **Explore** and tap a wallpaper. Use the collection chips to narrow the grid.
2. Watch the live preview as you adjust the controls. On a phone, use **Expand preview** for a larger view. The home-screen preview button overlays sample icons and labels.
3. Choose the appearance and motion settings you want, then tap **Set live wallpaper**.
4. Finish in Android's wallpaper picker and select one of the destinations your phone offers.

Editing changes a draft. The active wallpaper keeps its existing settings until the draft is applied. Canceling the system preview leaves the active configuration unchanged.

Some system pickers do not report whether you applied the wallpaper. If you chose Phosphor and the app asks, tap **Save wallpaper settings** to commit the draft. If you canceled the picker, leave that confirmation alone. Home and lock screens share one applied Phosphor configuration; separate designs are not implemented.

The heart beside a wallpaper's title saves a look immediately. In the editor, **Save look** lets you name a version with its dimming, motion, and photo framing settings. Open a look from **Saved** to adjust it, update it, or save another variation. Saved and Explore remember their own collection filters.

## Rotate saved looks

Save at least two looks, then open **Saved → Rotation**. Choose **Daily** to cycle through selected looks in the numbered order at local midnight. Deselect and reselect a look to move it to the end. Choose **Day and evening** to assign a look and local start time to each period. Tap **Save**, and set Phosphor as your live wallpaper if needed.

Rotation switches while the wallpaper is visible. If the screen is off or another app covers it, the scheduled look appears when you return. Editing a draft leaves the schedule alone. Manually applying a look keeps it until the next scheduled change. Removing a selected look skips it, and turning rotation off keeps the current wallpaper.

## Appearance

| Control | Effect |
| --- | --- |
| Scene control | Changes the composition, such as ring angle in Ringfall, curtain width in Northern veil, or rib spacing in Fluted glass. For photos, Floating lights adjusts the number of motes. |
| Color palette | Use the original procedural colors or choose Lagoon or Ember. Save a look to keep its palette and composition together. |
| Calm the artwork | Darkens the wallpaper so icons and widgets stand out. |

Built-in wallpapers use dark palettes in the gallery, editor, and Android wallpaper picker.

**Settings → App appearance** controls the app's System/Light/Dark theme separately. **Use your phone's colors** applies Material You colors to the app on Android 12+; it does not recolor the wallpaper. Phosphor's own app palette is the default.

## Echo trace and music

Open **Explore → ASCII → Echo trace**, turn on **Motion → React to music**, and allow Android’s microphone permission. Play music on the device. Bass peaks rise on the left, treble peaks rise on the right, and older peaks trail up the screen. **Trace height** adjusts their size. Tap **Set live wallpaper** to use it on your phone, or **Save look** to keep the setting for rotation.

Phosphor reads frequency levels from Android’s playback visualizer. The levels stay briefly in memory. The preview shows when it receives a signal. During silence or on an unsupported output, Echo trace returns to its ambient animation. Capture pauses when hidden, in Still mode, at zero speed, and while saving battery. Gallery tiles and PNG exports use the ambient artwork.

## Motion and battery

| Control | Effect |
| --- | --- |
| Speed | Sets ambient animation speed. Zero holds the scene at its current phase. |
| Intensity | Changes movement range and artwork detail. |
| Home-screen parallax | Nearby layers travel farther than distant scenery as the camera drifts. On supported launchers they ease into place after a page swipe. Increase Intensity for a wider sweep. |
| Tilt parallax | Enable this under Motion to move the viewpoint by tilting your phone from its resting angle. Requires an accelerometer, Home-screen parallax, and Speed above zero. Pauses in battery-saving mode. |
| Still mode | Holds the artwork and its parallax layers still. |
| Frame rate | Selects 15, 30, or 60 fps, subject to power and visibility limits. |
| Light up on wake | Gradually reveals the artwork on screen-on. The replay button previews the wake effect. |
| Lock and unlock animation | Enables the separate settling/reveal treatment around lock state changes. |
| Save battery when low | At 20% or less while unplugged, reduces motion and caps at 15 fps; at 5% or less, holds still. |

System Battery Saver reduces motion and caps animation at 15 fps even if **Save battery when low** is off. The visible lock screen is also capped at 15 fps. Hidden or screen-off wallpapers stop their animation loop, and Android's disabled-animation preference makes the artwork still. Wake effects can be suppressed while saving energy.

These are rendering policies; a battery-life percentage has not been measured. Screen-off/always-on-display visibility and the events delivered on wake depend on your phone.

## Import your own image

Tap **Import photo** in Explore and select a JPEG, PNG, or WebP image through Android's document picker. Phosphor stores a bounded, orientation-corrected PNG copy in private app storage, so later losing access to the original document does not break the import.

In **Photo framing**, move the target on the full photo to your subject, or use the horizontal and vertical focus sliders. The live preview shows the crop with room around its edges for drift. The wallpaper layers floating light over the photo. Save a look to keep this image and framing when you import another photo.

Imports stay on the device and are not included in cloud backup. Clearing app storage or uninstalling deletes private copies and settings. Keep your original images elsewhere if you want to retain them.

## Export a still

In the editor, tap **Save a still image** and select a destination in Android's document picker. Phosphor writes a watermark-free **1080 × 2400 PNG** at a fixed animation time using your current settings and photo framing.

## Phone colors and common limitations

Phosphor supplies representative wallpaper colors and light/dark hints to Android. Your phone and launcher decide how to use them for labels, palettes, and themed icons.

- **Pixel:** look in **Wallpaper & style** for palette options.
- **Samsung:** look in **Wallpaper and style → Color palette**. A Galaxy Themes package is a separate format and is not included.
- **No live wallpaper palette offered:** try exporting a still and choosing a palette from it, then reapply Phosphor. This is a manual workaround and may not persist on every firmware.
- **Checking depth on a single-page launcher.** Enable **Home-screen parallax**, turn off Still mode, and set Speed above zero to see the ambient drift. Page-to-page movement also requires a launcher that supplies scrolling offsets.
- **No independent lock-screen option:** use the destinations the system picker provides.

See [Android compatibility](ANDROID_COMPATIBILITY.md) for detailed behavior. For a reproducible problem, use the [bug report checklist](../CONTRIBUTING.md#report-a-bug).
