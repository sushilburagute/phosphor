# Design and motion decisions

[Documentation index](README.md) · [Design system](DESIGN_SYSTEM.md) · [Architecture](ARCHITECTURE.md)

This guide explains the layout, interaction, and power decisions behind Phosphor. The implementation uses Android's public APIs and the shared offline Canvas renderer. The linked guidance was consulted on 2026-11-13.

## Layout and Material You

- Use Material 3 semantic color roles for app surfaces, text, controls, and selection, filled from Phosphor’s own palette (see [the design system](DESIGN_SYSTEM.md)). Material You colors are an opt-in on Android 12+. Artwork keeps its own colors, with night and day treatments; Auto follows the system theme.
- Adapt navigation and editor layout to the available window space. Keep controls readable with bounded widths, wrapping choices, scrollable content, and system insets. Keep the apply action easy to find.
- Use Material typography and consistent spacing to distinguish navigation, artwork, sections, and individual settings. Give interactive controls at least a 48 dp target and combine each setting's label, explanation, and switch into one accessible action.

Sources: [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3), [adaptive window size guidance](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes), and [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).

## Motion and interaction

- Use short transitions to explain navigation and state changes. Keep the application chrome quiet so the wallpaper remains the focus.
- Let touch affect the artwork with eased movement and a settling response. Continue a finite interaction even when ambient motion speed is zero, then stop requesting frames.
- Every family answers the same four gestures in its own material: a tap sends a pulse; a held finger is a presence the art leans toward; a swipe has a velocity and leaves a fading wake (a light streak captured into orbit, a fold in the aurora, ink stirred through a gradient, a fissure etched in stone); a finger resting still gathers a visible charge, and lifting it releases a stronger pulse. Each preset's interaction hint names its signature gesture. The renderer tests require a visible wake and charge in every family, in both moods.
- Give each wallpaper family visible internal movement, rather than depending entirely on translating a flat canvas. Keep still gallery thumbnails and exported images deterministic.
- Respect both the app's still mode and Android's disabled-animation preference. Stop animation when hidden, detached, or paused; resume without advancing through time spent in the background.

Sources: [choosing Compose animation APIs](https://developer.android.com/develop/ui/compose/animation/choose-api), [Material motion](https://m3.material.io/styles/motion/overview), [ValueAnimator.areAnimatorsEnabled](https://developer.android.com/reference/android/animation/ValueAnimator#areAnimatorsEnabled()), and [WallpaperService.Engine lifecycle and touch](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine).

## Lock state and battery

- Treat screen interactivity, keyguard state, wallpaper visibility, and surface availability as distinct inputs. Animate the visible wallpaper's lock/wake/unlock appearance without attempting to control Android's keyguard or screen power animation.
- Android can hide the wallpaper or power down the display before a screen-off animation is visible. Lock-screen wallpaper availability and the events forwarded to a wallpaper depend on the launcher and manufacturer.
- Reduce work when the battery is low and unplugged, and honor system Battery Saver. Freeze content while non-interactive and never keep a hidden wallpaper's animation loop running. Battery-life improvements require physical-device profiling; a frame-rate cap alone is not a measured battery-life result.
- Cap visible lock-screen motion at 15 fps, including while charging. Restore the selected rate on unlock. Coalesce touch and scroll events with animation requests rather than redrawing for every input event.
- Refresh power and keyguard state on broadcasts and visibility changes, rather than querying system services during each frame. Battery broadcasts only redraw when the effective power policy changes. Hidden editor previews unregister their observers and cancel frame callbacks using [aggregated View visibility](https://developer.android.com/reference/android/view/View#onVisibilityAggregated(boolean)).
- Reuse renderer scratch geometry and stable shaders. Abstract loops share angle and ripple calculations across bands and passes, ASCII traces reuse their crest buffer, and background gradients remain cached until their palette or size changes. Preserve the same artwork and full-resolution output.
- Cache gallery artwork as one bitmap per visible tile, invalidating on content or size changes. This avoids repeated procedural drawing and GPU path-mask caching while browsing. Release temporary renderer resources after creating the still; leave bitmap lifetime to Android while recorded display lists may still reference it.

Sources: [battery monitoring](https://developer.android.com/training/monitoring-device-state/battery-monitoring), [PowerManager](https://developer.android.com/reference/android/os/PowerManager), [KeyguardManager](https://developer.android.com/reference/android/app/KeyguardManager), and [WallpaperService.Engine](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine).

See [validation](VALIDATION.md) for checks actually performed and the [compatibility matrix](ANDROID_COMPATIBILITY.md) for physical-device checks still required.
