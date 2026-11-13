# Phosphor design system

**Night is light, day is ink.** Every wallpaper is one scene with two physical treatments. At night it is emitted light on a deep ground, drawn additively so overlapping strokes build up like a long exposure. By day it is the same scene overprinted in two inks on cool paper, with a slight misregistration, like a risograph print. The app chrome borrows the same two materials.

Tokens live in `app/src/main/java/dev/sush/phosphor/ui/theme/PhosphorTheme.kt`; window and splash colors are mirrored in `res/values/colors.xml`.

## Color

| Token | Night | Day | Use |
| --- | --- | --- | --- |
| Ground | Sky `#060914` | Paper `#EDF0F5` | Backgrounds, window, splash |
| Raised | `#0E1324` / `#161C33` | `#F7F8FB` / `#E2E6EE` | Containers, navigation |
| Text | Starlight `#E9ECF6` | Ink `#12141C` | Primary text |
| Quiet text | Haze `#A5ACC6` | `#4A5068` | Secondary text |
| Primary | Phosphor `#A9C0FF` | Ultramarine `#2742B8` | Actions, selection, icons in headings |
| Ember | `#FF6A3A` | `#D5482A` | **Only** "live on your phone", the brand horizon, and a saved heart |

Selection containers (chips, navigation indicator, toggles) stay in the blue family so ember keeps its single meaning. "Use your phone's colors" swaps in Material You on Android 12+; Phosphor's palette is the default.

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

Radius follows hierarchy: phone-shaped plates 30 dp (they mimic a device), large controls 22 dp, controls 16 dp, small marks 6–10 dp. Plates use a 1:2 ratio so wallpapers are judged at the proportions they're used at. Spacing is a 4 dp grid; screen gutters are 20 dp. Sections are separated by space and a heading with an icon, not boxed cards.

## Motion

| Token | Value | Use |
| --- | --- | --- |
| Arrive | cubic(0.05, 0.7, 0.1, 1) | Screens entering |
| Shift | cubic(0.2, 0, 0, 1) | State changes in place |
| Quick / Calm | 160 / 320 ms | Exits, toggles / entrances |
| Mood | 520 ms | The day/night crossfade, the app's one orchestrated moment |

The wallpapers' own motion is described under Interaction below. The app respects Android's animator scale and the wallpaper's Still mode.

## Icons

Twenty-four-dp vector glyphs in `res/drawable/ic_*.xml`: 1.75 stroke, round caps and joins, tinted at the call site. They are drawn from the brand's two motifs, the orbit and the horizon: Explore is an orbit around a grain of light; Auto is a disc split by its horizon; Wake is a sun rising over a horizon line; Touch is a tap sending out rings.

The launcher icon is an adaptive icon: a grain of light inside a tilted orbit over an ember horizon, on a night-sky gradient with faint long-exposure arcs. A clip path cuts a gap in the grain where the orbit's front arc passes, so the themed (monochrome) icon reads as a ringed world rather than an eye. The splash screen uses the same foreground.

## Wallpaper readability

Launcher labels are white at night and near-black by day. The service reports which, through `WallpaperColors` hints on Android 12+ and through the measured frame on earlier versions, so launchers switch label color with the treatment.

The artwork keeps labels readable by composition, not by dimming everything:

- Night grounds are deep and day grounds pale, so the base always contrasts with the label color.
- Bright features sit between typical icon rows. For example, Long exposure's horizon is at 55% height and Threshold's at 79%, between the last row and the dock.
- A soft veil of ground color sits behind the status bar, clock and dock (`WallpaperRenderer.LEGIBILITY_TOP/BOTTOM`).
- "Calm the artwork" fades toward the ground: darker at night, paler by day.

`WallpaperRendererTest.launcherLabelsStayReadableInBothMoods` enforces this. At full on-device detail, for every preset, in both treatments, at three moments, it scores each of 16 label slots, the clock zone and the dock zone by the share of pixels where a label would reach WCAG 4.5:1. Clock and dock need 90%; label slots average 85% with none below 45%. Add new presets only if this test passes.

## Interaction

Every scene responds to touch through a shared `TouchField`: taps send a decelerating wavefront, drags pull the scene, and both settle on their own. Touch runs on real time, so it answers even when motion speed is zero, and frames stop once it settles. Launchers' tap and icon-drop commands produce pulses too. Each scene answers in its own way: orbits bend and their center follows your finger; aurora curtains flare and fold; glyphs scramble; gradients stir; a lamp moves behind fluted glass; lenses can be dragged; filaments part; contours bow around a press.

Wake reveal: on screen-on the artwork rises from its ground over 1.5 seconds. Trails lengthen, rays grow, and glyphs decode into place. On unlock, a bloom rises from the bottom edge. Parallax uses depth layers: stars move least, foreground figures and floors move most.

## Store assets

`docs/store/` holds the Play icon (rendered from the adaptive icon's actual vector layers), a 1024×500 feature graphic and phone screenshots cropped to Play's 2:1 limit. Regenerate with the commands in `tools/store_assets.py`.
