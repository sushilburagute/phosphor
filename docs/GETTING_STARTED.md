# Getting started

[Documentation index](README.md) · [User guide](USER_GUIDE.md) · [Testing](TESTING.md)

Run commands from the repository root. No global Gradle installation is needed.

## Prerequisites

| Component | Repository configuration |
| --- | --- |
| Java | JDK 17 (the CI version); a newer compatible JDK can also work |
| Android SDK | Platform 36 and Build Tools 35.0.0 |
| Gradle | 8.13, supplied by the checksum-pinned wrapper |
| Android Gradle plugin / Kotlin | 8.12.0 / 2.2.0, declared in [build.gradle.kts](../build.gradle.kts) |
| Device or emulator | Android 8.1 / API 27 or newer, with live wallpaper support |

The app compiles and targets API 36. See [app/build.gradle.kts](../app/build.gradle.kts) for Android configuration and [the wrapper properties](../gradle/wrapper/gradle-wrapper.properties) for the Gradle distribution.

## Configure Java and the SDK

Install JDK 17 and set `JAVA_HOME` to its directory. Check `java -version` in your terminal. In Android Studio, also choose a compatible Gradle JDK in the project's Gradle settings; the IDE and terminal may otherwise use different JDKs.

In Android Studio's SDK Manager, install:

- Android SDK Platform 36.
- Android SDK Build-Tools 35.0.0 (enable **Show Package Details** to select it).
- Android SDK Platform-Tools, for `adb` and device installation.
- An emulator and system image if you do not have a test phone.

If Android command-line tools are already installed, the equivalent SDK package commands are:

```sh
sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"
sdkmanager --licenses
```

Use `sdkmanager.bat` on Windows. Add its directory to `PATH` or invoke it by its full SDK path.

Set `ANDROID_HOME` to the SDK directory. Alternatively, create a root `local.properties` with your own absolute path:

```properties
# macOS example; replace with your SDK location.
sdk.dir=/Users/your-name/Library/Android/sdk
```

On Windows, forward slashes avoid Java properties backslash escaping:

```properties
sdk.dir=C:/Users/your-name/AppData/Local/Android/Sdk
```

`local.properties` is ignored by Git. Do not commit a machine-specific SDK path.

## Build and check

macOS/Linux:

```sh
./gradlew --version
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat --version
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The first build downloads Gradle, plugins, and dependencies. An internet connection is needed for these downloads and may be needed by Robolectric to resolve its Android runtime. The app itself has no network permission.

| Output | Location |
| --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Unit test report | `app/build/reports/tests/testDebugUnitTest/index.html` |
| Lint report | `app/build/reports/lint-results-debug.html` |

For the full CI command, preview generation, and report details, see [Testing](TESTING.md). For release builds and signing, see [Releasing](RELEASING.md).

## Run on a device or emulator

In Android Studio, open the repository, let Gradle sync, select the `app` run configuration and a device, then run it. On a physical phone, enable developer options and USB debugging and accept the debugging authorization prompt.

From a terminal with SDK Platform-Tools on `PATH`:

```sh
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.sush.phosphor/.MainActivity
```

If multiple devices are connected, use `adb -s <serial>` with the serial from `adb devices`. Launching the app does not apply a wallpaper; follow the [user guide](USER_GUIDE.md#apply-a-wallpaper).

## Troubleshooting

| Symptom | Check |
| --- | --- |
| `JAVA_HOME` is unset, Java cannot be found, or the Gradle JVM is incompatible | Install JDK 17, set `JAVA_HOME`, and check the JVM shown by the wrapper's `--version`. Check Android Studio's Gradle JDK separately. |
| SDK location not found | Set `ANDROID_HOME` or add `sdk.dir` to root `local.properties`. |
| Missing SDK platform/build tools, or licenses not accepted | Install the exact packages above and run `sdkmanager --licenses`. |
| `./gradlew: Permission denied` on macOS/Linux | Run `chmod +x gradlew`, then retry. |
| PowerShell cannot find `gradlew.bat` | Invoke it as `.\gradlew.bat` from the repository root. |
| Gradle, Maven, or Robolectric download fails | Check the network/proxy and retry after connectivity is restored. Offline mode requires previously cached dependencies. |
| APK installation reports a signature mismatch | Use the signing key for the installed app, or uninstall it first. Uninstalling deletes Phosphor's settings and private imported images. |
| Build succeeds but the wallpaper picker or palette behaves differently | Use the [Android compatibility guide](ANDROID_COMPATIBILITY.md) and record the exact OS and launcher. |
