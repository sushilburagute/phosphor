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

## Debug APKs from main

Each push to `main` runs the Android checks. After they pass, GitHub Actions commits the debug APK to `releases/` on `main`. For example, a push of `15b8062abcde` with `versionName=0.1.1` produces `releases/phosphor-0.1.1+g15b8062abcde-debug.apk`. The installed app shows the same version. The 12-digit Git revision is SemVer build metadata. To increase version precedence or the Android version code, edit `version.properties` as described above.

For a local copy after every commit on `main`, configure the [build prerequisites](GETTING_STARTED.md#prerequisites) and enable the tracked hook once per clone:

```sh
git config --local core.hooksPath .githooks
```

The hook builds the committed snapshot, including for partially staged commits. A local build failure leaves the commit in place. Retry with `sh tools/build-debug-apk.sh` (Git Bash on Windows). Local APKs are ignored by Git until CI publishes its copy. Local and hosted builds can use different debug signing keys.

The publish job needs permission to push to `main` under the repository's branch rules. It adds a follow-up commit, so run `git pull --rebase` to pick up published APKs before your next push. A multi-commit push publishes the APK for the pushed tip. Rerunning the same push workflow preserves an already published APK.

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

1. Increase both version fields on a branch. Optionally add user-facing notes under `## Unreleased` in the changelog.
2. Run `./gradlew -q :app:printVersion` and `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`. Use `.\gradlew.bat` on Windows PowerShell. Inspect [test and lint reports](TESTING.md#reports); a successful build alone does not establish device compatibility.
3. Install and check the app. Confirm the credit opens `https://sush.dev?utm_source=phosphor` from Explore, the editor, and Settings, and that the adjacent version matches the build. Check wallpaper previews, the applied wallpaper, and a still export for absence of branding. Complete the relevant [device checks](ANDROID_COMPATIBILITY.md).
4. Review and merge the version change into `main`.

## Automated GitHub Releases

When a push to `main` increases both `versionName` and `versionCode` relative to the previous tip, the Android workflow runs its normal checks and tags the tested commit as `vMAJOR.MINOR.PATCH`. It records the notes in `CHANGELOG.md` and publishes the verified CI debug APK on the matching GitHub Release. Ordinary pushes continue to produce commit-specific debug APKs.

The workflow uses an existing entry for the new version or promotes the pushed commit’s **Unreleased** notes. If both are empty, it generates notes from commit subjects since the previous version. If publication is interrupted, rerun the original push workflow. The job can resume an existing draft and verifies the asset on an already published release.

Settings checks GitHub’s latest published release when opened. Commit suffixes do not affect version precedence. Android may require a reinstall when CI debug signing keys differ. Uninstalling clears saved looks and imported photos.
