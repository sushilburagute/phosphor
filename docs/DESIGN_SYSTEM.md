# Phosphor design system

[Documentation index](README.md) · [Design decisions](DESIGN_PRINCIPLES.md) · [Contributing](../CONTRIBUTING.md)

Phosphor's wallpapers layer emitted light over dark grounds. The app uses deep sky in its dark theme and cool paper in its light theme.

Tokens live in `app/src/main/java/dev/sush/phosphor/ui/theme/PhosphorTheme.kt`; window and splash colors are mirrored in `res/values/colors.xml`.

## Color

| Token | Night | Day | Use |
| --- | --- | --- | --- |
| Ground | Sky `#060914` | Paper `#EDF0F5` | Backgrounds, window, splash |
| Raised | `#0E1324` / `#161C33` | `#F7F8FB` / `#E2E6EE` | Containers, navigation |
| Text | Starlight `#E9ECF6` | Ink `#12141C` | Primary text |
| Quiet text | Haze `#A5ACC6` | `#4A5068` | Secondary text |
| Primary | Phosphor `#A9C0FF` | Ultramarine `#2742B8` | Actions, selection, icons in headings |
| Ember | `#FF6A3A` | `#B83C24` | Active wallpaper status and the brand horizon |

Selection containers (chips, navigation indicator, toggles) stay in the blue family so ember remains limited to those accents. "Use your phone's colors" swaps in Material You on Android 12+; Phosphor's palette is the default.

## Type

One family, Bricolage Grotesque (variable; SIL OFL 1.1, bundled in `res/font`, license in `assets/licenses`). Three optical settings do the work of three typefaces:

| Role | Weight | Optical size | Width | Sizes |
| --- | --- | --- | --- | --- |
| Display | 640 | 96 | 82 (condensed) | 56 / 44 / 36 sp, tight tracking |
| Headline | 600 | 48 | 88 | 32 / 26 / 22 sp |
| Title | 560 | 20 | 96 | 20 / 16 / 14 sp |
| Text | 400 | 14 | 100 | 16 / 14 / 12.5 sp, 1.5 line height |

The scale steps by about 1.25 from a 16 sp body. Numbers in readouts use tabular figures. Copy is sentence case; there are no all-caps labels.

## Shape and space

Explore and Saved use compact Material 3 app bars and an adaptive grid with 16 dp gutters and 12 dp tile spacing. Gallery thumbnails use 16 dp corners and a 1:2 ratio to preserve the wallpaper framing. Each tile pairs the artwork with its title and a save action.

## Motion

| Token | Value | Use |
| --- | --- | --- |
| Arrive | cubic(0.05, 0.7, 0.1, 1) | Screens entering |
| Quick / Calm | 160 / 320 ms | Exits, toggles / entrances |

The wallpapers' ambient movement and launcher parallax are described below. The app respects Android's animator scale and the wallpaper's Still mode.

## Icons

The 24 dp vector glyphs in `res/drawable/ic_*.xml` use rounded caps and joins and take their tint from the control. Explore is an orbit around a grain of light, Auto is a disc split by its horizon, and Wake is a sun rising above a line.

The launcher icon is an adaptive icon: a grain of light inside a tilted orbit over an ember horizon, on a night-sky gradient with faint long-exposure arcs. A clip path cuts a gap in the grain where the orbit's front arc passes, so the themed (monochrome) icon reads as a ringed world rather than an eye. The native splash screen uses a dedicated copy of the mark on Sky, without an icon plate. On Android 12+ the orbit settles through 12 degrees and the horizon draws in over 520 ms; earlier versions show the complete static mark. The first app frame starts a 160 ms exit fade, without waiting for the icon animation. The exit is immediate when Android animations are disabled or either the active wallpaper or editor draft uses Still mode. The system controls the launch icon animation before app preferences are available.

## Wallpaper readability

The artwork is composed for white launcher labels. The service supplies `WallpaperColors` hints on Android 12+ and a measured frame on earlier versions so launchers can choose a matching label color. [Android compatibility](ANDROID_COMPATIBILITY.md)

The artwork keeps labels readable by composition, not by dimming everything:

- Dark grounds give white labels contrast.
- Bright features sit between typical icon rows. For example, Long exposure's horizon is at 55% height and Threshold's at about 82%, between the last row and the dock.
- A soft veil of ground color sits behind the status bar, clock and dock (`WallpaperRenderer.LEGIBILITY_TOP/BOTTOM`).
- "Calm the artwork" darkens the wallpaper.

`WallpaperRendererTest.launcherLabelsStayReadable` scores 16 label slots, the clock and the dock for every procedural preset at three moments. The score is the share of pixels where a label reaches 4.5:1 contrast. Clock and dock require 90% coverage. Label slots require an 85% average and a 45% minimum per slot. Northern veil and Low tide have a 60% minimum per slot.

## Ambient motion and parallax

Motion belongs to the subject. Solstice fans amber rays over a low horizon. Northern veil reflects three layers of jade curtains in a mountain lake. Signal bloom carries glyphs along a diagonal beam. Daybreak lights halftone clouds from below. Low tide carries fine silk fibres across broad turquoise swells. Basalt keeps its relief shallow so labels remain clear.

Near and distant layers shift by different amounts during the ambient camera drift and on launcher page changes. Direct wallpaper touches have no visual effect. Wake reveals the artwork over 1.5 seconds. Lock and unlock have their own transition setting.

## Store assets

[`docs/store/`](store) holds the Play icon (rendered from the adaptive icon's actual vector layers), a 1024×500 feature graphic and phone screenshots cropped to a maximum 2:1 long/short-side ratio. Regenerate them using the [preview and store asset workflow](TESTING.md#build-store-assets).
