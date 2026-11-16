# Artemis Android (Gabrieli2806 fork)

[![Build APK](https://github.com/Gabrieli2806/moonlight-android/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Gabrieli2806/moonlight-android/actions/workflows/build-apk.yml)

An open source game streaming client for [Apollo](https://github.com/ClassicOldSong/Apollo) and [Sunshine](https://github.com/LizardByte/Sunshine), built on [Artemis](https://github.com/ClassicOldSong/moonlight-android) (previously Moonlight Noir), which is itself a fork of [Moonlight for Android](https://github.com/moonlight-stream/moonlight-android).

This fork adds better support for foldables, a PlayStation-style on-screen controller and the Steam Controller (2026). Everything from upstream Artemis is still here.

## Download

- **Latest build:** the [`nightly` pre-release](https://github.com/Gabrieli2806/moonlight-android/releases/tag/nightly). It is rebuilt on every push to `moonlight-noir`.
- **Tagged releases:** [Releases](https://github.com/Gabrieli2806/moonlight-android/releases).

Pick the APK for your device's CPU. Almost every current phone needs `arm64-v8a`. Use `x86_64` for emulators and Chromebooks.

Debug APKs install as a separate app (`com.limelight.noirdebug`, shown as "Diana"), so they can sit next to a release install.

## What this fork adds

### Steam Controller (2026)
The Steam Controller is used directly by the app and shows up on the PC as an **Xbox 360 controller**. Android on its own only sees the controller's keyboard/mouse emulation.

- **Bluetooth:** pair the controller in Android's Bluetooth settings, then open Artemis and allow the Bluetooth permission. The controller connects on its own when you start a stream. You can turn this off in *Settings → Gamepad settings → Steam Controller (2026) over Bluetooth*.
- **USB:** works with a cable or with the wireless puck plugged into the phone. The puck supports up to four controllers. This needs the USB driver setting (on by default).
- Buttons, sticks, analog triggers and rumble all work. The back buttons, the "…" button and the trackpad clicks are sent as extra buttons, but an emulated Xbox 360 pad has no slot for them.
- The host can't show it as a real Steam Controller, because the Moonlight protocol only knows Xbox, PlayStation and Nintendo controllers.

### On-screen controller
- New PlayStation layout: D-pad above the left stick, face buttons above the right stick, and both sticks toward the middle of the screen.
- *Gamepad glyphs* setting: Glyph 1 is the classic layout with A/B/X/Y, Glyph 2 is the PlayStation layout with △ ○ ✕ □ (default).
- A live preview in settings shows the controller on this screen and on a half-folded foldable.
- Saved button positions are kept separately for the classic, PlayStation and folded layouts.

### Foldables
- Flex mode: when the phone is half-folded, the stream goes on the top half and the bottom half becomes a trackpad, keyboard or controller area.
- Split screen with the keyboard, so the keyboard doesn't cover the stream.
- Black bars are cropped in split screen and picture-in-picture.
- The rear screen can be used as a touchpad on devices that allow it.

### Inherited from Artemis
Custom virtual buttons, custom resolutions and bitrates, several mouse modes (multi-touch, touchpad, local cursor), external monitor mode, portrait mode, clipboard sync, virtual display and server commands with Apollo, SBS 3D, non-QWERTY keyboard layouts, and more. See [upstream Artemis](https://github.com/ClassicOldSong/moonlight-android) for the full list.

## Building

You need:
- JDK 17 or newer (JDK 21 works)
- Android SDK with platform 36
- Android NDK `27.0.12077973`

```sh
git clone --recursive https://github.com/Gabrieli2806/moonlight-android.git
cd moonlight-android
# If you cloned without --recursive:
git submodule update --init --recursive

# Point Gradle at your SDK/NDK if Android Studio hasn't already
echo "sdk.dir=/path/to/Android/Sdk" >> local.properties

./gradlew assembleNonRoot_gameDebug
```

APKs are written to `app/build/outputs/apk/nonRoot_game/debug/`, one per ABI. Install one with `adb install -r app-nonRoot_game-arm64-v8a-debug.apk`.

Other useful tasks:

| Task | What it does |
|---|---|
| `./gradlew assembleNonRoot_gameRelease` | Release build (unsigned unless signing variables are set, see below) |
| `./gradlew testNonRoot_gameDebugUnitTest` | JVM/Robolectric unit tests, see [android_test_setup.md](android_test_setup.md) |
| `./gradlew compileNonRoot_gameDebugJavaWithJavac` | Quick Java-only compile check |

## Continuous integration

[`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml) builds APKs on every push, pull request and `v*` tag.
- Each run uploads its APKs as an artifact.
- A push to `moonlight-noir` replaces the `nightly` pre-release.
- A tag like `v20.3.0` creates a normal release.

To make CI builds install as updates of each other, the APKs must always be signed with the same key. Add these repository secrets:

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | The keystore file, base64-encoded |
| `SIGNING_KEYSTORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password |

Without them, CI only builds debug APKs, each signed with a new throwaway key. You then have to uninstall before installing a newer build. The same variables (with `SIGNING_KEYSTORE_FILE` pointing at the keystore) also sign local builds.

## Credits

Moonlight was started by [Cameron Gutman](https://github.com/cgutman), [Diego Waxemberg](https://github.com/dwaxemberg), [Aaron Neyer](https://github.com/Aaronneyer) and [Andrew Hennessy](https://github.com/yetanothername) at [Case Western](http://case.edu) and [MHacks](http://mhacks.org). Artemis is by [ClassicOldSong](https://github.com/ClassicOldSong). The Steam Controller protocol details come from [SDL](https://github.com/libsdl-org/SDL).

Licensed under the GPLv3, see [LICENSE.txt](LICENSE.txt).
