# Aurora

Aurora is an Android VR cinema app for watching local videos on a large virtual cinema screen. It is designed for phone-in-headset viewing, where the phone renders a stereo split-screen image suitable for mobile VR headsets.

The first experience is intentionally minimal: a dark cinema environment with a large configurable screen. The focus is smooth offline playback, comfortable head-tracked viewing, and precise screen geometry before adding decorative theatre environments.

## Features

- Local/offline video playback
- Android-native media pipeline
- Large cinema-style screen target
- Dark viewing environment
- Foundation for stereo VR rendering
- Foundation for headset calibration
- Kotlin Android app architecture
- C++ native layer for future renderer and math hot paths

## Requirements

- Android Studio
- Android SDK
- JDK 21
- Network access for the first Gradle dependency download

## Build

```sh
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

APK outputs:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

## Architecture

Aurora keeps Android application behavior in Kotlin: UI, lifecycle, playback, storage, permissions, and settings.

C++ is reserved for narrow native workloads such as mesh generation, camera transforms, and lens distortion helpers.
