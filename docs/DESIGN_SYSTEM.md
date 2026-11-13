# Phosphor design system

**Night is light, day is color.** Every wallpaper is one scene with two treatments. At night it is emitted light on a deep, tinted ground, drawn additively so overlapping strokes build up like a long exposure. By day it is the same scene in daylight: luminous pastel color fields (the accents lifted in lightness, same hue) on two-tone paper, with crisp ink lines on top. Day never multiplies translucent ink over paper, which only ever reads as grey.

The app chrome is a gallery wall: near-neutral graphite or paper, so the wallpapers carry all the color.

Tokens live in `app/src/main/java/dev/sush/phosphor/ui/theme/PhosphorTheme.kt`; window and splash colors are mirrored in `res/values/colors.xml`.

## Color

| Token | Night | Day | Use |
| --- | --- | --- | --- |
| Ground | Graphite `#0D0E12` | Paper `#F4F4F7` | Backgrounds |
| Tiles | `#1A1B21` | White | Grouped list items, pills |
| High | `#22242B` | `#EAEAEF` | Button-group tracks |
| Text | Starlight `#EEEEF2` | Ink `#121318` | Primary text |
| Quiet text | Haze `#A4A6B2` | `#5B5D69` | Secondary text |
| Primary | Phosphor `#B3C3FF` | Ultramarine `#2E47C7` | The main action, selection, section labels |
| Ember | `#FF6A3A` | `#D5482A` | **Only** "live on your phone" and the brand horizon |

Selection (button groups, switches) stays in the blue family so ember keeps its single meaning. Category pills invert (text color on its own background) rather than adding a hue. "Use your phone's colors" swaps in Material You on Android 12+; Phosphor's palette is the default.

## Wallpaper color

Palettes are built, not picked. Each preset declares a `Scheme` (`model/ColorTheory.kt`): a key hue in OKLCH and a harmony that places the accent (analogous, triadic, split-complementary or complementary). Night and day are two lightness treatments of the same hues, so a scene keeps its identity when it switches.

- **60/30/10.** The ground fills most of the frame at low chroma; the key is the main light or ink; the harmony accent is the small, hot spark.
- **Tinted darks.** Night grounds sit at OKLCH lightness 0.17 with a little of the key's hue, never neutral black, so shadows feel colored.
- **Temperature contrast by day.** Paper leans toward the accent and the key ink sits on it, so a blue scene prints on warm paper.
- **Pigments, not thin ink.** Day color fields use `ColorTheory.pigment`: the accent at lightness about 0.8 with as much chroma as sRGB allows. Lines stay ink at lightness 0.47–0.63.
- Out-of-gamut colors lose chroma, never hue or lightness.

`ColorTheoryTest` checks the conversions, harmony placement and that every preset's grounds and accents keep their lightness separation.

## Type

One family, Bricolage Grotesque (variable; SIL OFL 1.1, bundled in `res/font`, license in `assets/licenses`). Three optical settings do the work of three typefaces:

| Role | Weight | Optical size | Width | Sizes |
| --- | --- | --- | --- | --- |
| Display | 600 | 96 | 88 | 56 / 44 / 36 / 34 sp (large top bar), tight tracking |
| Headline | 580 | 48 | 92 | 22 / 20 sp |
| Title | 540 | 20 | 98 | 16 / 14 sp |
| Text | 400 | 14 | 100 | 16 / 14 / 12.5 sp, 1.5 line height |

The scale steps by about 1.25 from a 16 sp body. Numbers in readouts use tabular figures. Copy is sentence case; there are no all-caps labels.

## Shape and space

Plates take this phone's real screen ratio (read from the window metrics, clamped for foldables and tablets), so a wallpaper is judged at exactly the proportions it will be used at. Their corners are 11% of their width, like a phone's own. Pills and the main action are fully round; grouped lists use 24 dp outer and 6 dp inner corners with 2 dp gaps, as in Android's own settings.

Spacing is a 4 dp grid. Layout follows Material's width classes: compact (under 600 dp) has 16 dp gutters and two columns; medium 24 dp and three; expanded 32 dp and four; large 40 dp and five. Large text drops a column rather than squeezing names. Sections are separated by space and a small primary-colored label, never by dividers or boxes.

There is one level of navigation. The gallery is home, with a collapsing large title; settings and the editor push over it. There is no bottom bar and no saved list.

## Motion

| Token | Value | Use |
| --- | --- | --- |
| Arrive | cubic(0.05, 0.7, 0.1, 1) | Screens entering |
| Shift | cubic(0.2, 0, 0, 1) | State changes in place |
| Quick / Calm | 160 / 320 ms | Exits, toggles / entrances |
| Mood | 520 ms | The day/night crossfade, the app's one orchestrated moment |

The wallpapers' own motion is described under Interaction below. The app respects Android's animator scale and the wallpaper's Still mode.

## Icons

Twenty-four-dp vector glyphs in `res/drawable/ic_*.xml`: 1.75 stroke, round caps and joins, tinted at the call site. They are drawn from the brand's two motifs, the orbit and the horizon: Wake is a sun rising over a horizon line. Icons appear only where a word would crowd: top-bar actions and the preview's two toggles.

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

Every scene responds to touch through a shared `TouchField`: taps send a decelerating wavefront, drags pull the scene, and both settle on their own. Touch runs on real time, so it answers even when motion speed is zero, and frames stop once it settles. Launchers' tap and icon-drop commands produce pulses too. Each scene answers in its own way: orbits bend and their center follows your finger; aurora curtains flare and fold; glyphs scramble; digital rain parts around your finger and taps send a ring of decoded characters; gradients stir; a lamp moves behind fluted glass; lenses can be dragged; filaments part; contours bow around a press.

Wake reveal: on screen-on the artwork rises from its ground over 1.5 seconds. Trails lengthen, rays grow, and glyphs decode into place. On unlock, a bloom rises from the bottom edge. Parallax uses depth layers: stars move least, foreground figures and floors move most.

## Store assets

`docs/store/` holds the Play icon (rendered from the adaptive icon's actual vector layers), a 1024×500 feature graphic and phone screenshots cropped to Play's 2:1 limit. Regenerate with the commands in `tools/store_assets.py`.
