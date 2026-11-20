# Releasing Phosphor

[Documentation index](README.md) · [Testing](TESTING.md) · [Changelog](../CHANGELOG.md)

## Version source

The manual release workflow updates the root `version.properties` file before building the APK. Gradle reads this file for the APK's `versionName` and `versionCode`, generates `BuildConfig` for the version shown beside app credits, and exposes `:app:printVersion` for tooling.

- `versionName` uses `MAJOR.MINOR.PATCH`, without prefixes or leading zeroes. Increase PATCH for fixes, MINOR for compatible features, and MAJOR for breaking changes. During 0.x development, MINOR may include breaking changes.
- The workflow increases Android's `versionCode` by one for each release, up to 2100000000.
- [CHANGELOG.md](../CHANGELOG.md) records user-facing changes under the matching version.

Read the current version from the repository root:

```sh
./gradlew -q :app:printVersion
```

On Windows PowerShell, use `.\gradlew.bat -q :app:printVersion`. This task prints the version name; read the Android version code from [version.properties](../version.properties).

## Debug APKs from main

Pushes and pull requests run the Android checks and upload the debug APK as an Actions artifact. Builds from pushes to `main` include the 12-digit Git revision in the artifact name and installed app version. For a numbered release, use the [manual release workflow](#publish-a-github-release).

For a local copy after every commit on `main`, configure the [build prerequisites](GETTING_STARTED.md#prerequisites) and enable the tracked hook once per clone:

```sh
git config --local core.hooksPath .githooks
```

The hook builds the committed snapshot, including for partially staged commits. A local build failure leaves the commit in place. Retry with `sh tools/build-debug-apk.sh` (Git Bash on Windows). Git ignores local APKs. Local and hosted builds can use different debug signing keys.

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

1. Optionally add user-facing notes under `## Unreleased` in the changelog.
2. Run `./gradlew -q :app:printVersion` and `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`. Use `.\gradlew.bat` on Windows PowerShell. Inspect [test and lint reports](TESTING.md#reports); a successful build alone does not establish device compatibility.
3. Install and check the app. Confirm the credit opens `https://sush.dev?utm_source=phosphor` from Explore, the editor, and Settings, and that the adjacent version matches the build. Check wallpaper previews, the applied wallpaper, and a still export for absence of branding. Complete the relevant [device checks](ANDROID_COMPATIBILITY.md).
4. Review and merge the changes you want to release into `main`.

## Publish a GitHub Release

Open **Actions → Android → Run workflow**, select **main**, choose **patch**, **minor**, or **major**, and click **Run workflow**. From `0.1.1`, the choices produce `0.1.2`, `0.2.0`, or `1.0.0`, respectively. Each choice advances Android's version code from `2` to `3`.

After tests, lint, and both APK builds pass, the workflow commits the new version, changelog, and verified debug APK to `main`. It tags that commit as `vMAJOR.MINOR.PATCH` and attaches the APK to the matching GitHub Release. The workflow uses an existing changelog entry for the new version, then falls back to **Unreleased** notes or commit subjects since the previous version.

The publish job needs permission to push to `main` and create release tags under the repository rules. If `main` advances before the release commit lands, start a new run against the updated branch. If publication is interrupted after tagging, rerun the original workflow run to finish publishing the same APK.

Settings checks GitHub’s latest published release when opened. Commit suffixes do not affect version precedence. Android may require a reinstall when CI debug signing keys differ. Uninstalling clears saved looks and imported photos.
