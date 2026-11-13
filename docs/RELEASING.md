# Releasing Phosphor

[Documentation index](README.md) · [Testing](TESTING.md) · [Changelog](../CHANGELOG.md)

## Version source

Edit the root `version.properties` file. Gradle reads this file for the APK's `versionName` and `versionCode`, generates `BuildConfig` for the version shown beside app credits, and exposes `:app:printVersion` for tooling. Do not hardcode a second version in the app or workflow.

- `versionName` uses `MAJOR.MINOR.PATCH`, without prefixes or leading zeroes. Increase PATCH for fixes, MINOR for compatible features, and MAJOR for breaking changes. During 0.x development, MINOR may include breaking changes.
- `versionCode` is a positive integer, at most 2100000000. Increase it for every distributed build, including fixes to the same release. It must exceed all previously distributed version codes so Android accepts the update. It is independent of the semantic version.
- [CHANGELOG.md](../CHANGELOG.md) records user-facing changes under the matching version.

Read the current version from the repository root:

```sh
./gradlew -q :app:printVersion
```

On Windows PowerShell, use `.\gradlew.bat -q :app:printVersion`. This task prints the version name; read the Android version code from [version.properties](../version.properties).

## Configure signing

The release build reads these names from Gradle properties first, then environment variables:

| Name | Value |
| --- | --- |
| `PHOSPHOR_KEYSTORE` | Keystore file path; use an absolute path to avoid resolution relative to `app/`. |
| `PHOSPHOR_KEYSTORE_PASSWORD` | Keystore password. |
| `PHOSPHOR_KEY_ALIAS` | Signing key alias. |
| `PHOSPHOR_KEY_PASSWORD` | Signing key password. |

Store them outside the repository, for example in your user-level `~/.gradle/gradle.properties` (`%USERPROFILE%\.gradle\gradle.properties` on Windows):

```properties
PHOSPHOR_KEYSTORE=/absolute/path/to/phosphor-release.jks
PHOSPHOR_KEYSTORE_PASSWORD=replace-with-your-keystore-password
PHOSPHOR_KEY_ALIAS=replace-with-your-key-alias
PHOSPHOR_KEY_PASSWORD=replace-with-your-key-password
```

Use forward slashes for Windows paths in a properties file. Never commit keys or real passwords, and retain the signing key needed for future updates. If `PHOSPHOR_KEYSTORE` is absent, `assembleRelease` produces an unsigned APK. If it is set, supply all four values and a valid keystore; partial configuration is not an unsigned-build fallback.

Build with `./gradlew assembleRelease` (PowerShell: `.\gradlew.bat assembleRelease`). Outputs are under `app/build/outputs/apk/release/`; inspect `output-metadata.json` for the actual filename and version. Without signing, the expected APK is `app-release-unsigned.apk`.

Before distributing a signed APK, verify its certificate and version using the SDK Build Tools, with that directory on `PATH`:

```sh
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
aapt dump badging app/build/outputs/apk/release/app-release.apk
```

Use `apksigner.bat` on Windows and substitute the actual signed filename if different. Confirm package `dev.sush.phosphor`, version name, and version code match the intended release.

## Prepare and validate

1. Update both version fields and the changelog on a branch.
2. Run `./gradlew -q :app:printVersion` and `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`. Use `.\gradlew.bat` on Windows PowerShell. Inspect [test and lint reports](TESTING.md#reports); a successful build alone does not establish device compatibility.
3. Install and check the app. Confirm the credit opens `https://sush.dev?utm_source=phosphor` from the gallery, the editor, and Settings, and that the adjacent version matches the build. Check wallpaper previews, the applied wallpaper, and a still export for absence of branding. Complete the relevant [device checks](ANDROID_COMPATIBILITY.md).
4. Review and merge the version change before tagging that commit.

## Tag and distribute

Create an annotated tag `vMAJOR.MINOR.PATCH` matching `versionName` exactly on the reviewed release commit and push that tag. For example, only when releasing a commit with `versionName=0.1.1`:

```sh
git tag -a v0.1.1 -m "Phosphor 0.1.1"
git push origin v0.1.1
```

Replace the example version with the version being released. Do not recreate an existing release tag.

The [Android workflow](../.github/workflows/android.yml) runs on pushes and pull requests, validates version fields through Gradle, and rejects a tag that does not match the version file. It builds/tests/lints the app and uploads a debug APK artifact named `phosphor-<versionName>-debug`. It also attempts to upload test and renderer reports as `phosphor-reports`, even after a failure. Release APKs are built but are not uploaded by this workflow. Signing secrets and store publishing are not configured in CI.

Tagging does not create a GitHub Release or publish to a store. Distribute the verified signed artifact separately and publish release notes from the matching changelog entry. Regenerate and inspect [store assets](TESTING.md#build-store-assets) when artwork or UI changes require them.
