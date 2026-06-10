# Aurora Cinema Build Plan

## Product Goal

Build a production-grade Android VR cinema app for Samsung Galaxy S24 FE and similar phones. The app plays local/offline 4K+ videos on a giant IMAX-scale virtual screen in a phone-in-headset stereo view. Version 1 is a black void cinema with a perfectly tuned screen. A full 3D theatre environment comes later.

This is not a demo architecture. Treat the app as a small VR media engine with separate media, rendering, VR session, calibration, storage, and UI systems.

## Target Device

- Primary device: Samsung Galaxy S24 FE
- Display reality: 2340 x 1080 total screen pixels
- VR reality: each eye gets roughly half the display before distortion and crop
- Playback goal: hardware-decoded 4K files, rendered cleanly to the available phone display
- Runtime goal: stable 60fps VR rendering
- Experience goal: seated cinema, dark environment, headphones for audio

## Core Technology

- Language: Kotlin
- Native language: C++ through Android NDK for selected renderer/math hot paths only
- UI: Jetpack Compose
- Video: AndroidX Media3 / ExoPlayer
- Rendering: OpenGL ES 3.0
- Native build: CMake if and when C++ is introduced
- Persistence: Room + DataStore
- Async: Kotlin coroutines + Flow
- Dependency injection: explicit manual DI for the first production path
- Minimum Android: Android 10+

Do not start with Unity or Vulkan. Unity can be considered for rich theatre environments later. Vulkan can be considered only if OpenGL ES becomes a proven bottleneck.

Use manual dependency wiring until the app has enough modules and scopes to justify Hilt. Avoid introducing a DI framework before the app has real complexity.

Keep Android-facing app code in Kotlin. Use C++ only behind narrow JNI boundaries for math-heavy or allocation-sensitive renderer work. Do not move playback, storage, permissions, services, or UI into native code.

## System Architecture

```text
Aurora Cinema
├── App Shell
│   ├── Phone UI
│   ├── VR Entry Flow
│   └── Settings / Calibration
│
├── Media Platform
│   ├── Media Library
│   ├── Metadata Extraction
│   ├── Playback Engine
│   ├── Codec Capability Detection
│   └── Resume / Watch-State System
│
├── VR Runtime
│   ├── Session Manager
│   ├── Head Tracking
│   ├── Stereo Camera System
│   ├── Frame Scheduler
│   ├── Comfort / Safety System
│   └── Input Router
│
├── Rendering Engine
│   ├── Video Texture Pipeline
│   ├── Scene Graph
│   ├── Screen Geometry System
│   ├── Stereo Renderer
│   ├── Lens Distortion Pass
│   ├── Color / HDR Pipeline
│   ├── Native Math / Mesh Core
│   └── Performance Telemetry
│
├── Headset Calibration
│   ├── Lens Profiles
│   ├── IPD Profiles
│   ├── Distortion Profiles
│   ├── FOV Profiles
│   └── Per-Device Overrides
│
└── Persistence
    ├── Room Database
    ├── DataStore Preferences
    ├── MediaStore Access
    └── App-Private Cache
```

## Runtime State Machine

```text
Idle
  -> LibraryBrowsing
  -> PreparingMedia
  -> PhonePlayback
  -> PreparingVrSession
  -> VrPlayback
  -> VrPaused
  -> RecoveringFromInterruption
  -> ExitingVr
  -> Error
```

Every phase must respect Android lifecycle events: backgrounding, screen lock, permission revocation, audio focus loss, headphone disconnect, activity recreation, and thermal throttling.

## Build Command Contract

When the user says `build #<phase number>`, implement that phase end to end.

Each phase must include:

- Production code for the requested phase
- Local verification steps
- Focused tests where practical
- No unrelated refactors
- No fake placeholder success paths
- Clear final report listing files changed and verification result

Do not skip acceptance criteria. If an acceptance criterion is impossible because of platform or environment limits, document the blocker and implement the closest safe behavior.

## Phase 0: Repository And Android Foundation

### Goal

Create a clean Android application foundation that can support media playback, rendering, storage, and future VR systems.

### Scope

- Create or normalize Android Gradle project structure.
- Add Kotlin Android support.
- Add Jetpack Compose.
- Add AndroidX Media3.
- Add Room.
- Add DataStore.
- Add coroutine/Flow support.
- Add basic app theme.
- Add top-level navigation shell.
- Add dependency/version catalog if the repo does not already have one.
- Lock package name, min SDK, target SDK, compile SDK, and release/debug build types.
- Add explicit manual dependency container.
- Add NDK/CMake configuration only if the selected Android Gradle setup can keep native builds clean and reproducible.
- Ensure debug and release builds both compile from the beginning.

### Architecture Deliverables

```text
app/
  src/main/
    AndroidManifest.xml
    java/.../aurora/
      MainActivity.kt
      app/
      core/
      ui/
```

### Acceptance Criteria

- App builds successfully.
- Debug and release variants build successfully.
- App launches on Android emulator or device.
- Main screen renders without crashing.
- Project has clean package/module boundaries ready for later phases.
- If native scaffolding is added, a no-op C++ library builds in debug and release.

### Verification

- Run Gradle build.
- Run release build.
- Run unit tests if present.
- Launch app if an Android target is available.
- If native scaffolding exists, verify CMake/NDK build output.

## Native Code Policy

### Allowed C++ Responsibilities

- Matrix and quaternion math that is used every frame.
- Stereo camera transform helpers.
- Flat, curved, sphere, and distortion mesh generation.
- Lens distortion lookup tables or precomputed geometry.
- Allocation-sensitive renderer data preparation.
- Small renderer diagnostics helpers if profiling proves value.

### Forbidden C++ Responsibilities

- Android UI.
- Media3 / ExoPlayer ownership.
- PlaybackService or MediaSession logic.
- Storage Access Framework and MediaStore access.
- Room and DataStore persistence.
- App navigation.
- Permission handling.
- Business logic that does not need native performance.

### JNI Boundary Rules

- JNI APIs must be narrow, typed, and stable.
- Prefer bulk inputs/outputs over many tiny JNI calls per frame.
- Never call JNI repeatedly inside the hottest render loop unless profiling proves it is safe.
- Kotlin owns lifecycle; C++ owns pure computation buffers.
- Native code must not retain Android `Context`, `Activity`, or UI objects.
- Every native allocation must have an explicit owner and release path.
- Native crashes are app crashes, so native code must stay small and heavily tested.

### Native Build Rules

- Use CMake through the Android Gradle Plugin.
- Keep C++ standard and compiler flags explicit.
- Build all supported ABIs only when packaging requires it; prefer the target device ABI during development.
- Keep native warnings clean.
- Add unit-style tests for pure native math where practical.

## Phase 1: App Shell, Navigation, And Settings Skeleton

### Goal

Build the phone-side app shell users will use before entering VR.

### Scope

- Library screen placeholder.
- Player screen placeholder.
- Settings screen.
- Calibration screen placeholder.
- App navigation.
- Persistent basic settings using DataStore.
- First-run safety/comfort acknowledgement.

### Screens

```text
Library
Video Details
Settings
Headset Calibration
About / Diagnostics
```

### Acceptance Criteria

- User can navigate all top-level screens.
- Settings persist after app restart.
- First-run safety screen appears once.
- UI is usable in portrait and landscape.

### Verification

- Compose UI smoke tests where practical.
- Manual navigation test.

## Phase 2: SAF-First Offline Media Library

### Goal

Let the user import, browse, and manage local videos from the phone.

### Scope

- Storage Access Framework single-file picker as the primary import path.
- Storage Access Framework folder picker as the primary bulk import path.
- Persist URI permissions when Android allows it.
- Android MediaStore video scan only as an optional convenience path after permissions are explicit.
- Room database for video metadata.
- Metadata extraction for duration, width, height, mime type, and display name.
- Library list with sort and search.
- Missing-file handling.
- Permission and URI access recovery flow.

### Data Model

```text
VideoEntity
- id
- uri
- displayName
- durationMs
- width
- height
- mimeType
- dateAdded
- lastSeenAt
- sourceType
- persistedPermission
- lastAccessCheckAt
```

### Acceptance Criteria

- User can pick a local video.
- User can pick a local folder where supported.
- Imported videos appear in the library.
- App survives restart and still lists imported videos.
- App handles deleted/moved files gracefully.
- No broad storage hacks.
- If Android revokes access to a URI, the app asks the user to reconnect the file or folder.

### Verification

- Unit tests for repository behavior.
- Manual test with at least mp4 and mkv if available.
- Manual restart test confirming persisted URI access.

## Phase 3: Media Playback Engine

### Goal

Build a robust media playback layer independent of VR.

### Scope

- Media3 ExoPlayer wrapper.
- Implement `MediaSession` for system media controls and audio focus integration.
- Add a lightweight `PlaybackService` for resilient long-form playback state ownership; VR presentation remains activity-bound.
- Play, pause, seek, stop.
- Playback state exposed as Flow.
- Audio focus handling.
- Noisy-audio handling for headphones/Bluetooth disconnect.
- Resume position tracking.
- Error model for unsupported files.
- Basic fullscreen phone playback.
- Early timed-text/overlay contract so subtitles can integrate later without changing playback state shape.

### Architecture Deliverables

```text
playback/
  PlaybackEngine.kt
  PlayerController.kt
  PlaybackState.kt
  PlaybackError.kt
  PlaybackProgressStore.kt
  MediaSessionController.kt
  TimedTextTrack.kt
```

### Acceptance Criteria

- User can play selected local videos in normal phone mode.
- Playback state updates correctly.
- Resume position is saved.
- Unsupported media shows a useful error.
- App handles pause/resume lifecycle.
- Audio focus loss and headphone disconnect are handled predictably.
- MediaSession works for system play/pause where available.
- PlaybackService owns player lifetime cleanly across normal app lifecycle transitions.

### Verification

- Unit tests for playback state reducer/progress persistence.
- Manual playback test.

## Phase 4: Codec And Device Capability System

### Goal

Detect what the device can play before the VR renderer depends on it.

### Scope

- Query MediaCodec capabilities.
- Detect likely support for H.264, HEVC, AV1, VP9.
- Probe media container, codec profile, codec level, bit depth, color format, HDR format, frame rate, and estimated bitrate where available.
- Detect HDR metadata where Android exposes it.
- Surface decoder name and risk warnings.
- Store per-video probe results for diagnostics and user-facing warnings.
- Add diagnostics screen.

### Acceptance Criteria

- Diagnostics screen shows display, decoder, and media capability info.
- Video details screen warns for likely unsupported or risky codecs.
- Playback failures include actionable details.
- Video details include probe results, not just file extension or mime type.

### Architecture Deliverables

```text
media/
  MediaProbe.kt
  MediaProbeResult.kt
  CodecCapabilityService.kt
  DeviceDisplayInfo.kt
```

### Verification

- Unit tests for capability classification where possible.
- Manual test with known media samples.

## Phase 5: OpenGL Rendering Foundation

### Goal

Create the standalone rendering engine without video first.

### Scope

- Custom EGL-backed render surface.
- Dedicated GL thread with explicit lifecycle ownership.
- GLSurfaceView may be used only as a temporary spike if custom EGL setup is blocked, not as the default production path.
- Renderer lifecycle.
- Frame clock using Choreographer.
- Clear black background.
- Basic mesh rendering.
- Matrix/math utilities.
- Kotlin-native math facade with optional C++ implementation.
- Render telemetry.
- Reserve render architecture for future offscreen per-eye framebuffers.

### Architecture Deliverables

```text
render/
  RenderEngine.kt
  RenderLoop.kt
  FrameClock.kt
  EglRenderSurface.kt
  Mesh.kt
  ShaderProgram.kt
  Telemetry.kt
  native/
    NativeMath.kt
    NativeMesh.kt
```

```text
cpp/
  native_math.cpp
  native_mesh.cpp
  include/
    native_math.h
    native_mesh.h
```

### Acceptance Criteria

- A GL screen launches and renders a stable black scene.
- A test mesh can render for diagnostics.
- GL lifecycle survives pause/resume.
- Frame timing telemetry is captured.
- Render surface choice is documented with tradeoffs.
- C++ is used only for pure math/mesh helpers if it is introduced in this phase.
- Kotlin fallback or test coverage exists for correctness-sensitive math.

### Verification

- Manual render smoke test.
- Unit tests for math utilities.

## Phase 6: Video Texture Pipeline

### Goal

Connect ExoPlayer decoding to OpenGL presentation.

### Scope

- Create `SurfaceTexture`.
- Create `GL_TEXTURE_EXTERNAL_OES`.
- Provide a Surface to ExoPlayer.
- Sample video texture in GL shader.
- Render video onto a flat screen mesh.
- Preserve video aspect ratio.
- Handle frame availability without blocking render thread.
- Include an overlay extension point for later subtitles and VR controls.
- Keep texture ownership and player ownership separate.

### Pipeline

```text
ExoPlayer
  -> Surface
  -> SurfaceTexture
  -> GL_TEXTURE_EXTERNAL_OES
  -> VideoFrameSampler
  -> Screen Material
  -> Rendered Screen
```

### Acceptance Criteria

- Local video plays on an OpenGL-rendered screen.
- Aspect ratio is correct.
- Pause, resume, seek, and stop work.
- No render-thread blocking from player operations.
- The renderer can draw non-video overlay primitives on top of the video plane.

### Verification

- Manual playback test through GL path.
- Check logs for dropped frame and lifecycle issues.

## Phase 7: Cinema Screen Geometry System

### Goal

Make the screen feel physically huge and configurable.

### Scope

- Physical screen model using virtual meters.
- Flat screen mesh.
- Curved screen mesh.
- Native C++ mesh generation for curved screen geometry if profiling or complexity justifies it.
- Aspect ratio presets.
- Fit/fill/crop modes.
- Screen size, distance, vertical offset, and curvature controls.
- Save cinema presets.

### Config Model

```text
CinemaScreenConfig
- aspectRatioMode
- distanceMeters
- widthMeters
- verticalOffsetMeters
- curvatureRadius
- tiltDegrees
- brightness
- contrast
- cropMode
```

### Aspect Ratio Modes

- Source
- 1.43:1
- 1.90:1
- 16:9
- 2.39:1

### Acceptance Criteria

- User can switch aspect ratio modes.
- Screen geometry updates without restarting playback.
- Curved screen mode works.
- Presets persist across app restart.
- Mesh generation produces stable vertex/index buffers without per-frame allocation churn.

### Verification

- Unit tests for screen geometry generation.
- Native/Kotlin parity tests for generated screen geometry if C++ is used.
- Manual visual test with different aspect videos.

## Phase 8: Stereo VR Renderer

### Goal

Render the cinema screen as a phone-in-headset split stereo view.

### Scope

- Left-eye and right-eye viewports.
- Stereo camera rig.
- IPD setting.
- Per-eye projection matrix.
- Native C++ stereo transform helpers if Kotlin math becomes noisy or allocation-heavy.
- Offscreen per-eye framebuffer path prepared for lens distortion.
- Headset-safe full-screen immersive mode.
- Recenter transform.

### Camera Model

```text
CameraRig
- headPose
- leftEyePose
- rightEyePose
- ipdMeters
- fovDegrees
- nearPlane
- farPlane
- recenterTransform
```

### Acceptance Criteria

- VR mode renders left and right eye views.
- Screen appears at stable virtual depth.
- IPD changes affect stereo separation.
- User can recenter view.
- Android system UI is hidden in VR mode.
- The renderer can switch between direct-to-viewport stereo and offscreen eye buffers without changing playback code.

### Verification

- Manual headset test.
- Math tests for eye transform generation.
- Native/Kotlin parity tests for eye transform generation if C++ is used.

## Phase 9: Head Tracking System

### Goal

Make the virtual cinema respond correctly to phone orientation.

### Scope

- Android rotation vector sensor integration.
- Pose smoothing.
- Sensor lifecycle.
- Recenter.
- Drift-aware seated mode.
- Orientation lock for headset usage.

### Acceptance Criteria

- Looking left/right/up/down moves the view naturally.
- Recenter resets forward direction.
- Tracking starts and stops cleanly with VR session lifecycle.
- No artificial camera movement.

### Verification

- Manual headset test.
- Unit tests for pose/recenter math.

## Phase 10: Lens Distortion And Headset Calibration

### Goal

Correct the stereo image for phone VR headset lenses.

### Scope

- Per-eye framebuffer render.
- Barrel distortion shader.
- Distortion coefficients.
- Optional C++ generation for distortion meshes or lookup tables.
- FOV calibration.
- Lens center offset.
- IPD calibration.
- Headset profile persistence.
- Manual calibration UI.

### Profile Model

```text
HeadsetProfile
- id
- name
- ipdMeters
- fovDegrees
- screenToLensDistance
- interLensDistance
- verticalLensOffset
- distortionK1
- distortionK2
- distortionK3
- chromaticAberrationRed
- chromaticAberrationBlue
```

### Acceptance Criteria

- User can tune lens distortion.
- Calibration persists.
- Presets can be created and selected.
- VR image is comfortable enough for seated viewing.
- Distortion pass uses the same per-eye framebuffer path prepared by earlier renderer phases.
- Distortion data is precomputed when possible and not rebuilt every frame.

### Verification

- Shader smoke test.
- Native/Kotlin parity tests for distortion mesh or lookup-table generation if C++ is used.
- Manual test inside actual headset.

## Phase 11: VR Playback Controls And Gaze Input

### Goal

Allow playback control while the phone is inside the headset.

### Scope

- Gaze cursor.
- Dwell selection.
- VR overlay.
- Play/pause.
- Seek forward/back.
- Timeline.
- Recenter.
- Screen size/distance quick controls.
- Exit VR.
- Control lock mode.

### Acceptance Criteria

- User can control playback without touching the screen.
- Gaze dwell does not accidentally trigger constantly.
- Overlay can be hidden.
- User can exit VR reliably.

### Verification

- Manual headset test.
- Unit tests for dwell state machine.

## Phase 12: Comfort, Safety, And Session Recovery

### Goal

Make the app safe and robust during real viewing sessions.

### Scope

- Seated-use warning.
- Brightness limiter.
- Comfort mode.
- Thermal status monitoring.
- Battery status awareness.
- Audio focus recovery.
- Incoming interruption handling.
- Screen lock/background recovery.
- Crash-safe resume position.

### Acceptance Criteria

- App recovers from pause/resume.
- App saves progress frequently enough.
- Thermal warning appears when needed.
- User can always exit VR.
- No camera motion that causes artificial movement sickness.

### Verification

- Manual lifecycle test matrix.
- Unit tests for session state transitions.

## Phase 13: Performance And Telemetry

### Goal

Optimize for stable long-form 4K viewing.

### Scope

- Render frame timing.
- Dropped video frame tracking.
- Decoder info tracking.
- Thermal/battery telemetry.
- Performance diagnostics screen.
- Quality degradation policy.
- Perfetto/Android Studio profiler capture checklist for render, decode, thermal, and memory behavior.
- Release-build performance verification, not only debug-build verification.

### Degradation Policy

```text
Normal:
- full quality

Warm:
- force 60fps
- reduce overlay effects
- keep black void

Hot:
- warn user
- reduce brightness
- disable optional scene work
```

### Acceptance Criteria

- Diagnostics identify render and decode issues.
- App remains stable during long playback.
- User receives useful warnings instead of silent failure.
- Release builds meet the same basic playback and render stability expectations as debug builds.

### Verification

- Long playback test.
- Stress test with high-bitrate sample if available.
- Capture at least one profiler trace during sustained VR playback when a device is available.

## Phase 14: Subtitle And Audio Timing Support

### Goal

Support serious movie watching features.

### Scope

- Embedded subtitle detection where Media3 supports it.
- External subtitle picker for SRT/WebVTT if practical.
- Subtitle rendering in VR overlay space.
- Subtitle size/depth controls.
- Audio delay adjustment.

### Acceptance Criteria

- Subtitles are readable in VR.
- User can adjust subtitle size/position.
- User can adjust audio delay.
- Settings persist per video or globally.

### Verification

- Manual test with subtitle samples.
- Unit tests for subtitle settings persistence.

## Phase 15: Library Polish And Watch Experience

### Goal

Make the app feel like a real offline cinema library.

### Scope

- Thumbnails.
- Recent videos.
- Continue watching row.
- Search.
- Sort.
- Storage warnings.
- Video details page.
- Rename display title locally.
- Delete library entry without deleting source file.

### Acceptance Criteria

- Library is pleasant and fast with many videos.
- Continue watching works.
- Missing files and permission issues are clearly handled.

### Verification

- Manual library test with many fake or real entries.
- Repository tests.

## Phase 16: Bluetooth Controller And Hardware Input

### Goal

Support optional external controls.

### Scope

- Bluetooth keyboard/controller key mapping.
- Media button support.
- Volume key shortcuts.
- Configurable input actions.

### Acceptance Criteria

- User can play/pause/seek/recenter with external controls.
- Controls do not conflict with system volume behavior unexpectedly.
- Input mapping is visible in settings.

### Verification

- Manual test with available hardware.
- Unit tests for input action mapping.

## Phase 17: 3D Video Modes

### Goal

Support actual stereoscopic video files.

### Scope

- Side-by-side 3D video mode.
- Over-under 3D video mode.
- Mono mode.
- Per-eye UV mapping.
- Swap eyes option.

### Acceptance Criteria

- SBS and OU videos render correct per-eye images.
- User can swap eyes.
- Mono files still work normally.

### Verification

- Manual test with known SBS/OU samples.
- Unit tests for UV mapping.

## Phase 18: 180 And 360 Video Foundation

### Goal

Add immersive video formats beyond a cinema screen.

### Scope

- Equirectangular sphere mesh.
- Native C++ sphere mesh generation if vertex density becomes high.
- 180-degree mode.
- 360-degree mode.
- Mono and stereo variants if practical.
- Format selection UI.

### Acceptance Criteria

- 180/360 videos can be viewed with head tracking.
- Cinema screen mode remains unaffected.
- User can select projection mode per video.

### Verification

- Manual test with known 180/360 samples.
- Geometry tests for projection mesh.
- Native/Kotlin parity tests for sphere mesh generation if C++ is used.

## Phase 19: Theatre Scene System

### Goal

Add optional 3D theatre environments without hurting pure cinema mode.

### Scope

- Scene graph expansion.
- Theatre environment asset loading.
- Seats/walls/floor.
- Subtle lighting.
- Multiple seat positions.
- Performance mode fallback to black void.

### Acceptance Criteria

- User can switch between black void and theatre.
- Theatre mode does not break playback.
- Black void remains the highest-performance path.

### Verification

- Manual headset test.
- Performance comparison against black void.

## Phase 20: Production Hardening

### Goal

Make the app reliable enough for regular personal use.

### Scope

- Crash reporting hooks if desired.
- Strict error boundaries.
- Database migration tests.
- Permission edge cases.
- Lifecycle torture testing.
- Accessibility pass for phone UI.
- Release build configuration.
- R8/minification rules for Media3, Room, and any renderer/native code if enabled.

### Acceptance Criteria

- Release build works.
- Minified release build works if minification is enabled.
- App handles common Android failure modes.
- No debug-only code is required for normal usage.
- Core flows are tested or manually verified.

### Verification

- Release build.
- Unit tests.
- Manual lifecycle matrix.

## Phase 21: Packaging And Device Install

### Goal

Produce an installable APK/AAB for the target phone.

### Scope

- Release signing setup guidance.
- APK generation.
- AAB generation if needed.
- Install instructions.
- Device-specific test checklist for S24 FE.

### Acceptance Criteria

- APK installs on Samsung S24 FE.
- User can import a video and enter VR mode.
- Playback works offline.
- Calibration and settings persist.

### Verification

- Install on device.
- End-to-end offline playback test.

## Non-Negotiable Engineering Rules

- Keep media decoding independent from VR rendering.
- Keep rendering independent from Android UI.
- Keep headset calibration profile-driven, never hardcoded to one headset.
- Do not block the GL thread with IO, database, or player commands.
- Preserve Android lifecycle correctness from the beginning.
- Prefer black void performance mode over visual decoration.
- Treat 4K playback as a decode problem, not a display-resolution promise.
- Every phase must leave the app buildable.
- C++ is allowed only for narrow renderer/math hot paths.
- Native code must not own Android lifecycle, playback, storage, permissions, or UI.

## Known Hard Problems

- Stable video frame timing through `SurfaceTexture`.
- Correct stereo camera geometry.
- Comfortable lens distortion calibration.
- 4K codec/device compatibility.
- Heat during long sessions.
- Android storage permission behavior.
- Lifecycle recovery during VR playback.
- Avoiding nausea from bad optics or artificial camera motion.

## Definition Of Done For The Full App

- User installs the app on Samsung S24 FE.
- User imports or selects an offline local video.
- User starts VR cinema mode.
- App displays the video on a giant configurable screen in a black void.
- Stereo split-screen rendering works inside a phone VR headset.
- Lens distortion and IPD are adjustable.
- Head tracking and recenter work.
- Playback controls work through gaze or external input.
- Resume position is saved.
- App survives normal lifecycle interruptions.
- 4K files play when supported by hardware decoder.
- Later theatre mode can be added without rewriting the core.
