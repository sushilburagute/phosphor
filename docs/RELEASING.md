# Releasing Phosphor

## Version source

Edit the root `version.properties` file. Gradle reads this file for the APK's `versionName` and `versionCode`, generates `BuildConfig` for the version shown beside app credits, and exposes `:app:printVersion` for tooling. Do not hardcode a second version in the app or workflow.

- `versionName` uses `MAJOR.MINOR.PATCH`, without prefixes or leading zeroes. Increase PATCH for fixes, MINOR for compatible features, and MAJOR for breaking changes. During 0.x development, MINOR may include breaking changes.
- `versionCode` is a positive integer, at most 2100000000. Increase it for every distributed build, including fixes to the same release. It must exceed all previously distributed version codes so Android accepts the update. It is independent of the semantic version.
- `CHANGELOG.md` records user-facing changes under the matching version.

## Prepare and validate

1. Update both version fields and the changelog on a branch.
2. Run `./gradlew -q :app:printVersion` and `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`. Use `gradlew.bat` on Windows.
3. Install and check the app. Confirm the credit opens `https://sush.dev?utm_source=phosphor` from Explore, the editor, and Settings, and that the adjacent version matches the build. Check wallpaper previews, the applied wallpaper, and a still export for absence of branding. Complete the relevant [device checks](ANDROID_COMPATIBILITY.md).
4. Review and merge the version change before tagging that commit.

## Tag and distribute

Create an annotated tag `vMAJOR.MINOR.PATCH` matching `versionName` exactly and push that tag. For example, a release with `versionName=0.1.1` uses `git tag -a v0.1.1 -m "Phosphor 0.1.1"` followed by `git push origin v0.1.1`.

CI runs on pushes and pull requests, validates version fields through Gradle, and rejects a tag that does not match the version file. It builds/tests/lints the app and uploads a debug APK artifact named `phosphor-<versionName>-debug`. This is a development artifact, not a signed release or an automatic publication.

Release builds are unsigned without your own signing configuration. Configure signing securely outside version control, build and sign the release artifact, and verify its package and version metadata before distribution. Never commit signing keys or passwords. Publish release notes from the matching changelog entry when distributing a signed release.
