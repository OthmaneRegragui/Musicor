<p align="center">
  <img src="media/logo.png" alt="Musicor logo" width="128">
</p>

<h1 align="center">Musicor</h1>

<p align="center">
  A dark, minimalist local music player for Android, Linux, Windows, and macOS.
  <br>
  No accounts. No streaming. No telemetry. Just your music.
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue" alt="License"></a>
  <img src="https://img.shields.io/badge/Kotlin-2.4-blueviolet" alt="Kotlin">
  <img src="https://img.shields.io/badge/Compose%20Multiplatform-1.11.1-informational" alt="Compose Multiplatform">
  <img src="https://img.shields.io/badge/Android-6.0%2B-brightgreen" alt="Android 6.0+">
  <img src="https://img.shields.io/badge/Desktop-Windows%20%7C%20Linux%20%7C%20macOS-lightgrey" alt="Desktop">
</p>

Musicor is a local-first music player built with Kotlin Multiplatform and Compose Multiplatform.
Point it at a folder of MP3s or WAVs and it builds a searchable, playable library - nothing is uploaded anywhere.

## Features

- **Your files, your library** - add any music folder; MP3 and WAV are scanned and indexed locally.
- **Categories** - organize songs into playlists you can name, rename, rescan, and remove.
- **Full playback controls** - play/pause, previous/next, click-to-seek, and loop mode.
- **Cover art** - extracted automatically from tags and shown in lists, the player, and notifications.
- **Edit track info** - fix a title or artist directly in the app.
- **Background playback (Android)** - audio keeps playing with the screen off, backed by a foreground service, wake lock, and proper audio-focus handling.
- **Media controls everywhere** - notification and lock-screen controls on Android, MPRIS2 integration for GNOME/KDE on desktop.
- **Picks up where you stopped** - the current song and position survive app restarts.
- **Kind to old hardware** - Android 6.0 (API 23) and up, small memory footprint with an 8 MB bounded cover-art cache.

## Platforms

| Platform | Minimum | Status |
|---|---|---|
| Android | 6.0 (API 23) | Full support |
| Linux | Any desktop with a GUI | Full support (AppImage / deb) |
| Windows | 10 | Full support (EXE installer) |
| macOS | 11 | Best effort (dmg, not regularly tested) |
| iOS | 15 | In progress (interface only, playback not yet implemented) |

## Installation

Ready-to-use packages are produced by the GitHub Actions workflow on every push, pull request, or manual
`workflow_dispatch` run - grab them from the run's artifact section on GitHub. You can also build everything yourself
(see [Building from source](#building-from-source)).

| Package | Platform | Install |
|---|---|---|
| **APK** | Android | Enable "Install unknown apps", then open the APK file. Release builds are unsigned - add a `signingConfig` for Play Store distribution. |
| **AppImage** | Linux | `chmod +x Musicor-1.0.0.AppImage && ./Musicor-1.0.0.AppImage` - runs on any distro, no installation. |
| **deb** | Linux (Debian/Ubuntu) | `sudo apt install ./your-musicor.deb` |
| **EXE** | Windows | Run the installer produced by jpackage (built on a Windows runner; jpackage cannot cross-build). |

Notes:

- Desktop packaging picks formats per build OS in `desktopApp/build.gradle.kts`
  (Linux: AppImage + deb, Windows: EXE, macOS: dmg), so each CI job builds only what its runner supports.
- The Windows build runs fine even though MPRIS is Linux-only: `MprisService` degrades silently when no DBus
  session bus is available.

## Building from source

Requirements:

- JDK 21 (Temurin recommended) - includes `jpackage` for desktop packaging.
- Android SDK (platform 36) for the Android app; set `sdk.dir` in `local.properties` or `ANDROID_HOME` in your shell.
- Xcode only if you work on the iOS app.

| Task | Command |
|---|---|
| Run the desktop app | `./gradlew :desktopApp:run` |
| Desktop with hot reload | `./gradlew :desktopApp:hotRun --auto` |
| Android debug APK | `./gradlew :androidApp:assembleDebug` |
| Android release APK | `./gradlew :androidApp:assembleRelease` |
| Linux packages | `./gradlew :desktopApp:packageDeb :desktopApp:packageAppImage` |
| Windows package | `.\gradlew.bat :desktopApp:packageExe` |
| macOS package | `./gradlew :desktopApp:packageDmg` |
| iOS app | Open `iosApp/` in Xcode and run from there |

Artifacts are written under `androidApp/build/outputs/` and `desktopApp/build/compose/binaries/`.

## Testing

| Suite | Command |
|---|---|
| Desktop / shared logic | `./gradlew :shared:jvmTest` |
| Android host tests | `./gradlew :shared:testAndroidHostTest` |
| iOS simulator tests | `./gradlew :shared:iosSimulatorArm64Test` (macOS only) |

## Tech stack

- **Kotlin Multiplatform** - shared logic and UI across Android, desktop, and iOS.
- **Compose Multiplatform + Material 3** - one UI codebase, dark theme throughout.
- **AndroidX Media** - MediaSession, audio focus, and the playback foreground service.
- **mp3agic / mp3spi** - tag extraction and MP3 decoding on the JVM.
- **dbus-java** - MPRIS2 media controls on Linux.
- **Gradle with version catalogs + GitHub Actions** - build automation for APK, AppImage, deb, and EXE.

## Project structure

```
musicor/
├── androidApp/          # Android application (entry point + manifest)
├── desktopApp/          # Desktop JVM application (window, MPRIS)
├── iosApp/              # iOS application (Xcode project)
├── shared/              # Shared Kotlin Multiplatform module
│   ├── commonMain/      #   Platform-independent UI and logic
│   ├── androidMain/     #   Playback service, SAF folder scanning
│   ├── jvmMain/         #   Desktop playback, MPRIS helpers
│   ├── iosMain/         #   iOS entry point
│   ├── androidHostTest/ #   Android host tests
│   ├── commonTest/      #   Shared tests
│   ├── jvmTest/         #   Desktop tests
│   └── iosTest/         #   iOS tests
├── gradle/              # Version catalog and wrapper
└── media/               # Brand assets (logo)
```

## License

This project is licensed under the Apache License, Version 2.0. See the [LICENSE](./LICENSE) file for the full text.