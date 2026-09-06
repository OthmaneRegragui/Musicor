This is a Kotlin Multiplatform project targeting Android, iOS, Desktop (JVM).

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

### Build artifacts

The GitHub Actions workflow (`.github/workflows/build.yml`) builds and publishes these artifacts on every push to `main`, pull request, or manual `workflow_dispatch` run. Download them from the run's artifact section.

| Artifact | Platform | Description | Build command | Output |
|---|---|---|---|---|
| **APK** | Android | Installable app package, ready to sideload. Release builds are unsigned; add a `signingConfig` to make them installable via Play Store. | `./gradlew :androidApp:assembleRelease` | `androidApp/build/outputs/apk/release/` |
| **AppImage** | Linux | Self-contained portable app - runs on any distro without installation. | `./gradlew :desktopApp:packageAppImage` | `desktopApp/build/compose/binaries/main/appImage/` |
| **deb** | Linux (Debian/Ubuntu) | System package, installable with `apt`/`dpkg`. | `./gradlew :desktopApp:packageDeb` | `desktopApp/build/compose/binaries/main/deb/` |
| **EXE** | Windows | Windows installer produced with jpackage. Built on the `windows` CI job (jpackage cannot cross-build). | `.\gradlew.bat :desktopApp:packageExe` | `desktopApp/build/compose/binaries/main/exe/` |

Notes:

- Desktop packaging picks formats per build OS in `desktopApp/build.gradle.kts` (Linux: AppImage + deb, Windows: EXE, macOS: dmg), so each CI job only builds what its runner supports.
- The Windows EXE runs fine even though the media controls (MPRIS) are Linux-only: `MprisService` degrades silently when the DBus session bus is unavailable.
- The app supports Android 6.0 (API 23) and up; cover art is decoded as small thumbnails and cached with an 8 MB LRU to keep RAM usage low on older devices.

### License

This project is licensed under the Apache License, Version 2.0. See the [LICENSE](./LICENSE) file for the full text.