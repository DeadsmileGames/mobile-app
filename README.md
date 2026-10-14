<p align="center">
  <img src="https://raw.githubusercontent.com/deadsmilegames/launcher/main/public/readme.png" width="120px" alt="Deadsmile Games">
</p>

<h3 align="center">

`Deadsmile Games for Android`

</h3>

<p align="center">
  <img src="https://img.shields.io/badge/STATUS-ANDROID%20RELEASE-gold" alt="Status: Android release">
  <img src="https://img.shields.io/badge/VERSION-1.1.0-gold" alt="Version 1.1.0">
  <img src="https://img.shields.io/badge/PLATFORM-ANDROID-green" alt="Platform: Android">
  <img src="https://img.shields.io/badge/TECH-Kotlin%20%7C%20Jetpack%20Compose-blue" alt="Kotlin and Jetpack Compose">
  <img src="https://img.shields.io/badge/DISTRIBUTION-APK%20%2B%20AAB-lightgrey" alt="APK and Android App Bundle">
</p>

<p align="center">
  A native Android app for discovering Deadsmile Games, reading news, viewing videos, and accessing account-linked game content.
</p>

<p align="center">
  <a href="https://deadsmilegames.vercel.app">Website</a>
  •
  <a href="https://deadsmilegames.vercel.app">Games</a>
  •
  <a href="https://github.com/DeadsmileGames/mobile-app/releases/latest">Releases</a>
  •
  <a href="https://github.com/DeadsmileGames/mobile-app/issues">Issues</a>
</p>

## About

Deadsmile Games for Android is built with **Kotlin, Jetpack Compose, and Material 3**. It connects to the Deadsmile Games platform and complements the Windows launcher without executing PC games on the phone.

- **Version:** 1.1.0
- **Package ID:** `com.deadsmilegames.companion`
- **Minimum Android version:** Android 8.0 (API 26)
- **Target Android version:** Android 16 (API 36)
- **UI:** Native Jetpack Compose
- **Distribution:** Signed APK for direct installation; signed Android App Bundle (AAB) for Google Play
- **Build validation:** GitHub Actions, UI contracts, and unit tests

## Features

- Global search across games, news, videos, and account-linked game lists.
- Game catalog with portrait poster artwork and age-rating details.
- News and video feeds with related game links when the platform provides them.
- Account sign-in, profile settings, synchronized wishlist, and read-only PC library.
- Portuguese (Brazilian), English, and Spanish app interfaces with explicit language selection.
- Dynamic colors, light/dark/OLED themes, reduced motion, and notifications.
- HTTPS-only API traffic and encrypted session-cookie storage.

## Installation

Open [GitHub Releases](https://github.com/DeadsmileGames/mobile-app/releases/latest) to get the latest signed direct-install APK. Android may ask you to allow installation for the app you use to open the APK.

The APK is intended for direct distribution outside Google Play. The `.aab` asset is the publishing file for Play Console and cannot be installed directly on a phone. Installations from the direct APK and Google Play are separate distribution channels; switching channels may require uninstalling the previous version because Google Play signs delivered APKs with the Play app-signing key.

## Google Play distribution

New Android apps and app updates submitted to Google Play from **August 31, 2026** must target Android 16 (API 36) or higher. This project targets API 36; see the [official target API requirement](https://developer.android.com/google/play/requirements/target-sdk).

The release workflow publishes both files to GitHub Releases:

- `Deadsmile-Games-Android-v1.1.0.apk` — signed APK for direct installation.
- `Deadsmile-Games-Play-Store-v1.1.0.aab` — signed Android App Bundle to upload in Google Play Console.
- `SHA256SUMS.txt` — SHA-256 hashes for both files.

The `direct` build retains user-confirmed APK updating for direct installations. The `play` build does not request `REQUEST_INSTALL_PACKAGES` and leaves app updates to Google Play. GitHub Actions does **not** submit an app to Play Console; upload the AAB and complete the Play listing, privacy policy, Data safety, content rating, app access/reviewer instructions, target-audience and ads declarations as applicable.

## Development

Requirements:

- JDK 17
- Gradle 8.13
- Android SDK Platform 36 and compatible Build Tools

Run from the repository root:

```sh
gradle --no-daemon testDirectDebugUnitTest testPlayDebugUnitTest assembleDirectDebug
```

The Android APK workflow verifies the shared UI/localization contracts, runs unit tests for both distribution variants, builds a direct-debug APK, and publishes it as a GitHub Actions artifact.

## Signed release process

A semantic version tag such as `v1.1.0` triggers [Release Android](https://github.com/DeadsmileGames/mobile-app/actions/workflows/release-android.yml). For the first `v1.1.0` release, the same workflow also runs after the dedicated release PR is merged and creates the tag at that tested merge commit. The workflow runs the static audit and unit tests, builds both distribution variants, checks the package ID and target API, verifies the signatures, confirms that only the direct APK contains the package-install permission, then uploads the APK, AAB, and checksum file to a GitHub Release.

For the first release, the PR has already been merged, but the release workflow stopped before building assets because the signing secrets were not configured. Add these GitHub Actions secrets, then open [the failed Release Android run](https://github.com/DeadsmileGames/mobile-app/actions/workflows/release-android.yml) and choose **Re-run jobs**. For later versions, configure the same secrets before pushing a new release tag:

- `ANDROID_RELEASE_KEYSTORE_BASE64` — release/upload keystore encoded as Base64.
- `ANDROID_RELEASE_STORE_PASSWORD` — keystore password.
- `ANDROID_RELEASE_KEY_ALIAS` — key alias.
- `ANDROID_RELEASE_KEY_PASSWORD` — key password.

Generate the upload key in a secure environment, store a backup outside the repository, and use the same key for future release assets:

```sh
keytool -genkeypair -v -keystore deadsmile-games-upload.jks -alias deadsmile-games-upload -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 deadsmile-games-upload.jks
```

Never commit the keystore, passwords, or Base64 contents. Google Play App Signing should be enabled when setting up the Play Console app; the upload key is used to submit the AAB, and Google Play manages the signing key for delivered installs.
