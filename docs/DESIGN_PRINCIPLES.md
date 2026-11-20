# Design and motion decisions

[Documentation index](README.md) · [Design system](DESIGN_SYSTEM.md) · [Architecture](ARCHITECTURE.md)

This guide explains the layout, interaction, and power decisions behind Phosphor. The implementation uses Android's public APIs and the shared offline Canvas renderer. The linked guidance was consulted on 2026-11-13.

## Layout and Material You

- Use Material 3 semantic color roles for app surfaces, text, controls, and selection, filled from Phosphor’s own palette (see [the design system](DESIGN_SYSTEM.md)). Material You colors are an opt-in on Android 12+. Wallpaper palettes stay dark across all app themes.
- Adapt navigation and editor layout to the available window space. Keep controls readable with bounded widths, wrapping choices, scrollable content, and system insets. Keep the apply action easy to find.
- Use Material typography and consistent spacing to distinguish navigation, artwork, sections, and individual settings. Give interactive controls at least a 48 dp target and combine each setting's label, explanation, and switch into one accessible action.

Sources: [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3), [adaptive window size guidance](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes), and [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).

## Motion and interaction

- Use short transitions to explain navigation and state changes. Keep the application chrome quiet so the wallpaper remains the focus.
- Give each composition a clear silhouette and a slower background layer. Keep foreground motion away from the clock and dock, and vary depth as the launcher scrolls.
- Give each wallpaper family visible internal movement, rather than depending entirely on translating a flat canvas. Keep still gallery thumbnails and exported images deterministic.
- Respect both the app's still mode and Android's disabled-animation preference. Stop animation when hidden, detached, or paused; resume without advancing through time spent in the background.

See [Compose animation APIs](https://developer.android.com/develop/ui/compose/animation/choose-api), [Material motion](https://m3.material.io/styles/motion/overview), [ValueAnimator.areAnimatorsEnabled](https://developer.android.com/reference/android/animation/ValueAnimator#areAnimatorsEnabled()), and [WallpaperService.Engine visibility and launcher offsets](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine).

## Lock state and battery

- Treat screen interactivity, keyguard state, wallpaper visibility, and surface availability as distinct inputs. Animate the visible wallpaper's lock/wake/unlock appearance without attempting to control Android's keyguard or screen power animation.
- Android can hide the wallpaper or power down the display before a screen-off animation is visible. Lock-screen wallpaper availability and the events forwarded to a wallpaper depend on the launcher and manufacturer.
- Reduce work when the battery is low and unplugged, and honor system Battery Saver. Freeze content while non-interactive and never keep a hidden wallpaper's animation loop running. Battery-life improvements require physical-device profiling; a frame-rate cap alone is not a measured battery-life result.
- Cap visible lock-screen motion at 15 fps, including while charging. Restore the selected rate on unlock. Coalesce launcher scroll events with animation requests.
- Refresh power and keyguard state on broadcasts and visibility changes, rather than querying system services during each frame. Battery broadcasts only redraw when the effective power policy changes. Hidden editor previews unregister their observers and cancel frame callbacks using [aggregated View visibility](https://developer.android.com/reference/android/view/View#onVisibilityAggregated(boolean)).
- Reuse renderer scratch geometry and stable shaders. Orbit caches seeded trail coefficients and culls invisible arcs. ASCII traces reuse their crest buffer, and background gradients stay cached until their palette or size changes.
- Cache gallery artwork as one bitmap per visible tile. Invalidate it when content or size changes to avoid repeated procedural drawing while browsing. Release temporary renderer resources after drawing the still and leave bitmap lifetime to Android while display lists may still reference it.

Sources: [battery monitoring](https://developer.android.com/training/monitoring-device-state/battery-monitoring), [PowerManager](https://developer.android.com/reference/android/os/PowerManager), [KeyguardManager](https://developer.android.com/reference/android/app/KeyguardManager), and [WallpaperService.Engine](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine).

See [validation](VALIDATION.md) for checks actually performed and the [compatibility matrix](ANDROID_COMPATIBILITY.md) for physical-device checks still required.
