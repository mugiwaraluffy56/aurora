# Packaging and Device Install

This is the local packaging path for Aurora builds that need to be installed on
a handset or attached to a GitHub release.

## Build artifacts

Run the complete packaging check before handing a build to testers:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease bundleRelease
```

Expected outputs:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
app/build/outputs/bundle/release/app-release.aab
```

Use the debug APK for local device testing. Use the release APK for sideload
smoke tests after signing. Use the AAB for store-style distribution once
release signing is configured.

## Local install

Install the debug build:

```sh
scripts/install-debug-apk.sh
```

Install the unsigned release APK only on a device profile that allows debug
signing or after replacing it with a signed artifact:

```sh
scripts/install-release-apk.sh
```

Both scripts use `adb install -r` and fail if the expected artifact is missing.

## GitHub release

Pushing a tag that starts with `v` runs `.github/workflows/release.yml`:

```sh
git tag v0.1.0
git push origin v0.1.0
```

The release workflow runs release unit tests, builds the release APK and AAB,
creates a GitHub Release, and attaches both artifacts.

## Signing

Release signing is documented in `docs/release-signing.md`. Do not distribute
an unsigned release APK outside internal smoke testing.
