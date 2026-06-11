# Device Test Plan

Use this checklist for S24 FE and other Android phones before calling a build
ready for VR playback testing.

## Setup

- Build with `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease bundleRelease`.
- Install with `scripts/install-debug-apk.sh`.
- Disable battery saver and keep the device on a stable surface before VR tests.
- Copy at least three local videos to the device: 1080p H.264, 4K HEVC, and one
  unsupported or software-decoded sample.

## App launch

- First launch shows the safety/onboarding flow.
- Safety acknowledgement enables the continue action.
- App returns to the library after process restart.
- Settings persist after changing comfort mode, projection, stereo layout, and
  theatre scene.

## Library

- Import a single video through the system file picker.
- Import a folder through the system folder picker.
- Verify thumbnails, duration, codec badges, and storage warnings.
- Rename a library item and verify the source file path is unchanged.
- Revoke folder permission and confirm the library warning appears.

## Playback

- Start playback from video details.
- Pause, resume, seek backward, and seek forward from the 2D player.
- Verify playback position is saved after leaving and reopening the app.
- Verify Bluetooth or hardware media keys trigger play/pause and seek.
- Confirm unsupported codecs show an actionable warning instead of crashing.

## VR rendering

- Enter VR from active playback.
- Verify split-eye output is visible and centered.
- Toggle mono, side-by-side, and over-under stereo layout.
- Toggle eye swap and confirm left/right content changes.
- Switch between cinema, 180, and 360 projection.
- Recenter view from VR controls and from the hardware input path.
- Confirm gaze controls are readable and do not overlap the video.

## Performance and comfort

- Run 10 minutes of 4K HEVC playback.
- Check renderer diagnostics for stable frame rate and low backlog.
- Trigger warm-device mode if possible and verify quality degradation is visible
  in diagnostics.
- Confirm subtitles and audio delay controls remain accessible.

## Failure handling

- Force-stop the app during playback and relaunch.
- Disconnect storage access and relaunch.
- Try entering VR without an active playable item.
- Confirm crash reporting writes a local crash marker after an uncaught crash in
  a debug-only manual test.
