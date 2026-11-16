# AGENTS.md

Notes for AI coding agents (and humans) working on this repo.

## What this is

Artemis Android is a game streaming client for Apollo/Sunshine and a fork of Moonlight Android. This copy (`Gabrieli2806/moonlight-android`, default branch `moonlight-noir`) is a fork of [ClassicOldSong/moonlight-android](https://github.com/ClassicOldSong/moonlight-android). It adds:
- support for foldables and the rear screen
- a PlayStation-style on-screen controller with a glyph setting and a live preview
- drivers for the Steam Controller (2026) over USB and Bluetooth LE

Status: in active personal use. The Steam Controller drivers were written from SDL's protocol sources and still need checking on real hardware.

## Commands

The JDK must be 17 or newer. Set `JAVA_HOME` first: the machine default can be a JDK that Gradle rejects. On Windows, use e.g. `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot`.

```sh
git submodule update --init --recursive        # required once: native code lives in moonlight-common-c
./gradlew compileNonRoot_gameDebugJavaWithJavac # fast Java-only check
./gradlew assembleNonRoot_gameDebug             # APKs in app/build/outputs/apk/nonRoot_game/debug/
./gradlew testNonRoot_gameDebugUnitTest         # Robolectric unit tests (see android_test_setup.md)
```

- Use the `nonRoot_game` flavor. `root` is a legacy flavor for Android 7 and older.
- `lint` currently fails because of existing errors (NewApi in AppView/PcView/ControllerHandler, MissingSuperCall in StreamSettings, a vi translation). Don't add new ones, and don't make CI depend on lint until those are fixed.
- The debug build has the package `com.limelight.noirdebug` and the label "Diana", so it installs next to the release app (`com.limelight.noir`).

## Testing on a device or emulator

- An x86_64 emulator works for UI and on-screen controller checks. Launch a fake stream with:
  `adb shell am start -n com.limelight.noirdebug/com.limelight.Game --es Host 10.255.255.1 --ei AppId 1 --es AppName Test --es PcName PC`
  The OSC draws while the connection dialog spins.
- To simulate a foldable, use a resizable/foldable AVD and `adb emu posture <n>` (2 = half-open, 3 = open).
- USB and Bluetooth controllers can only be tested on a real phone. Filter logcat by `SteamTriton`, `SteamTritonBle` or `ProCon`.

## Layout

| Path | What's there |
|---|---|
| `app/src/main/java/com/limelight/Game.java` | Streaming activity: input routing, fold handling, binds `UsbDriverService` |
| `.../binding/input/ControllerHandler.java` | All gamepad input. Maps Android `InputDevice`s and our own drivers to Moonlight controller packets |
| `.../binding/input/driver/` | Our own controller drivers (`AbstractController` subclasses), hosted by `UsbDriverService` |
| `.../binding/input/driver/SteamTriton*.java` | Steam Controller (2026): shared protocol, USB wired, USB puck, BLE controller, BLE manager |
| `.../binding/input/virtual_controller/` | On-screen controller (layouts, glyph styles, saved positions) |
| `.../preferences/` | Settings screen (`StreamSettings`), `PreferenceConfiguration` (all pref keys and defaults), OSC preview |
| `.../utils/FoldSplitHelper.java`, `RearTouchpad.java` | Foldable posture handling and the rear-screen touchpad |
| `app/src/main/jni/moonlight-core/moonlight-common-c` | Git submodule with the streaming protocol (C) |
| `.github/workflows/build-apk.yml` | CI: builds APKs, keeps the `nightly` pre-release up to date, releases `v*` tags |

## Conventions

- **Strings:** every user-visible string goes in `res/values/strings.xml`, with a Spanish translation in `res/values-es/strings.xml`. Other languages fall back to English (lint `MissingTranslation` is disabled). In strings, use "the Android Bluetooth settings" rather than an unescaped apostrophe, which breaks aapt.
- **Preferences:** add the key and default in `PreferenceConfiguration`, then the UI in `res/xml/preferences.xml`.
- **Controller drivers:**
  - A driver extends `AbstractController`, fills in `buttonFlags`, the sticks and the triggers, then calls `reportInput()`.
  - Stick Y uses Android's convention: positive is down.
  - Call `notifyDeviceAdded()` / `notifyDeviceRemoved()` when the controller appears or disappears.
  - Controller IDs come from `UsbDriverService.allocateDeviceId()`.
- **What the host sees:** the controller type sent to the host (`LI_CTYPE_*`) decides what Sunshine emulates. Xbox gives an Xbox 360 pad; PS gives a DualShock/DualSense. `UsbDeviceContext.sendControllerArrival` changes the type to UNKNOWN when the phone's own gyro gets attached; the Steam Controller drivers are exempt so they stay Xbox.
- **minSdk is 21:** guard newer APIs with `Build.VERSION.SDK_INT` checks and don't use `java.util.function` (no desugaring).
- **Line endings:** files use CRLF or LF depending on the file. Keep whatever each file already uses.
- **Commits:** branch `moonlight-noir`. Keep upstream merges possible: put new features in new files when you can, and keep edits to upstream files small.
