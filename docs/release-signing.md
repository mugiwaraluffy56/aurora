# Release Signing

Aurora release builds should be signed with a private keystore that is not committed to the repository.

## Local APK

1. Create or provide a release keystore outside the repo.
2. Build the unsigned release APK:

   ```bash
   ./gradlew assembleRelease
   ```

3. Sign with `apksigner` or configure local Gradle signing properties outside version control.

## Play Distribution

Use Play App Signing for store releases. Keep upload keys private and rotate them through the Play Console if compromised.

## Verification

Before distributing a build:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Install on a target device and verify import, playback, VR mode, calibration persistence, and resume position.
