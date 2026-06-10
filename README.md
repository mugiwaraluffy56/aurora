# Aurora

Aurora is an Android VR cinema app for phone-in-headset viewing. The goal is offline local video playback on a large configurable cinema screen, starting with a black void experience and adding richer VR features phase by phase.

## Current Status

Phase 0 is complete:

- Android app scaffold
- Kotlin + Jetpack Compose
- Media3, Room, DataStore dependencies
- Manual dependency container
- C++/NDK no-op native library through CMake
- Debug and release APK builds

## Requirements

- Android Studio
- Android SDK
- JDK 21, using Android Studio's bundled JBR by default
- Network access for the first Gradle dependency download

The local SDK path is intentionally kept out of git in `local.properties`.

## Build

```sh
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

APK outputs:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

## Project Plan

The full build plan is in `plan.md`.

Work is organized by numbered phases. Ask for a phase like:

```text
build #1
```

Each completed phase should leave the app buildable, committed, and pushed.

## Native Code Rule

Kotlin owns Android app behavior: UI, lifecycle, playback, storage, permissions, and settings.

C++ is reserved for narrow renderer/math hot paths such as mesh generation, camera transforms, and lens distortion helpers.
