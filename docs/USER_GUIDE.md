# Using Phosphor

[Documentation index](README.md) · [Build and install](GETTING_STARTED.md) · [Android compatibility](ANDROID_COMPATIBILITY.md)

## Apply a wallpaper

1. Choose a wallpaper in the gallery. The sun/moon button at the top switches the previews between day and night.
2. In the editor, tap or drag the preview to try its interaction. Use the home-screen preview button to overlay sample icons and labels.
3. Choose the appearance and motion settings you want, then tap **Set wallpaper**.
4. Finish in Android's wallpaper picker and select one of the destinations your phone offers.

Editing changes a draft. The active wallpaper keeps its existing settings until the draft is applied. Canceling the system preview leaves the active configuration unchanged.

Some system pickers do not report whether you applied the wallpaper. If you chose Phosphor and the app asks, tap **Save wallpaper settings** to commit the draft. If you canceled the picker, leave that confirmation alone. Home and lock screens share one applied Phosphor configuration; separate designs are not implemented.

The download icon in the editor's top bar saves a still image. Settings open from the sliders icon in the gallery's top bar.

## Appearance

| Control | Effect |
| --- | --- |
| Auto | Resolves day/night from the phone's system dark theme. |
| Day | Uses the preset's daylight treatment: luminous color on pale paper. |
| Night | Uses its emitted-light treatment on a dark ground. |
| Calm | Fades toward the background: paler by day, darker at night, making icons easier to read. |

The gallery's day/night switch only changes the gallery preview. Choose **Day**, **Night**, or **Auto** in the editor for the applied wallpaper.

**Settings → App appearance** controls the app's System/Light/Dark theme separately. **Use your phone's colors** applies Material You colors to the app on Android 12+; it does not recolor the wallpaper. Phosphor's own app palette is the default.

## Motion and battery

| Control | Effect |
| --- | --- |
| Speed | Sets ambient animation speed. Zero speed can still allow finite touch responses. |
| Intensity | Changes movement range and artwork detail. |
| Respond to touch | Enables tap, drag, and launcher-swipe responses where the launcher forwards them. |
| Still | Stops artwork animation, including touch and scroll animation. |
| Frame rate | Selects 15, 30, or 60 fps, subject to power and visibility limits. |
| Light up on wake | Reveals the artwork on screen-on and adds an unlock bloom. The replay button previews the wake effect. |
| Lock and unlock | Enables the separate settling/reveal treatment around lock state changes. |
| Save battery when low | At 20% or less while unplugged, reduces motion and caps at 15 fps; at 5% or less, holds still. |

System Battery Saver reduces motion and caps animation at 15 fps even if **Save battery when low** is off. The visible lock screen is also capped at 15 fps. Hidden or screen-off wallpapers stop their animation loop, and Android's disabled-animation preference makes the artwork still. Wake effects can be suppressed while saving energy.

These are rendering policies; a battery-life percentage has not been measured. Screen-off/always-on-display visibility and the events delivered on wake depend on your phone.

## Import your own image

Use the import action in the gallery and select a JPEG, PNG, or WebP image through Android's document picker. Phosphor stores a bounded, orientation-corrected PNG copy in private app storage, so later losing access to the original document does not break the import.

The image gets gentle drift, ripples, and floating light. It is a flat-image effect; Phosphor does not segment subjects or generate a depth map. A new import opens as a draft and does not immediately replace the applied wallpaper.

Imports stay on the device and are not included in cloud backup. Clearing app storage or uninstalling deletes private copies and settings. Keep your original images elsewhere if you want to retain them.

## Export a still

In the editor, choose the desired treatment and tap **Save a still image**. Select a destination in Android's document picker. Phosphor writes a watermark-free **1080 × 2400 PNG** using the shared renderer at a fixed animation time. It is a rendered still, rather than a capture of the exact current live frame.

With **Auto**, export resolves against the system dark theme. Choose **Day** or **Night** explicitly to export either treatment. A still export does not apply a wallpaper.

## Phone colors and common limitations

Phosphor supplies representative wallpaper colors and light/dark hints to Android. Your phone and launcher decide how to use them for labels, palettes, and themed icons.

- **Pixel:** look in **Wallpaper & style** for palette options.
- **Samsung:** look in **Wallpaper and style → Color palette**. A Galaxy Themes package is a separate format and is not included.
- **No live wallpaper palette offered:** try exporting a still and choosing a palette from it, then reapply Phosphor. This is a manual workaround and may not persist on every firmware.
- **No touch response or swipe motion:** enable **Respond to touch**, turn off **Still**, and check battery/system animation settings. Some launchers do not forward touch or scrolling offsets.
- **No independent lock-screen option:** use the destinations the system picker provides.

See [Android compatibility](ANDROID_COMPATIBILITY.md) for detailed behavior. For a reproducible problem, use the [bug report checklist](../CONTRIBUTING.md#report-a-bug).
