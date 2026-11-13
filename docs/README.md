# Phosphor documentation

Return to the [project overview](../README.md).

## Build and use

- [Getting started](GETTING_STARTED.md): prerequisites, SDK setup, building, installation, and troubleshooting.
- [User guide](USER_GUIDE.md): applying a wallpaper, appearance and motion controls, imports, exports, and common device limitations.

## Develop and maintain

- [Contributing](../CONTRIBUTING.md): change workflow, artwork requirements, bug reports, and asset licensing.
- [Architecture](ARCHITECTURE.md): source map, shared rendering, draft/active settings, engine lifecycle, and local data.
- [Testing](TESTING.md): automated checks, reports, opt-in screenshots, store asset generation, and manual validation.
- [Releasing](RELEASING.md): versions, signing, tags, CI artifacts, and distribution.
- [Changelog](../CHANGELOG.md): changes grouped by app version.

## Design and platform behavior

- [Design system](DESIGN_SYSTEM.md): color, typography, spacing, icons, motion, and wallpaper readability.
- [Design and motion decisions](DESIGN_PRINCIPLES.md): rationale for layout, interaction, scheduling, and battery policies.
- [Android compatibility](ANDROID_COMPATIBILITY.md): wallpaper colors, system picker behavior, manufacturer limits, and the physical-device checklist.
- [Validation history](VALIDATION.md): recorded results from earlier builds, including checks still outstanding. It is a historical record, not a statement that the current commit passed those checks.

## Update the docs with the implementation

Keep setup requirements aligned with the Gradle files and [Android workflow](../.github/workflows/android.yml), control names aligned with the UI, and validation claims tied to a date and commit. Put repeatable commands in the testing guide and observed results in the validation history. Link new guides here so readers can find them.
