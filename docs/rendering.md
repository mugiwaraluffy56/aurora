# Rendering Architecture

Aurora uses a custom `SurfaceView` and EGL14 pipeline rather than `GLSurfaceView`.

This requires more lifecycle code, but it gives Aurora explicit control over the render thread, EGL context, swap interval, surface recreation, and future per-eye framebuffer pipeline. `GLSurfaceView` would reduce initial setup but obscure ownership and make the later video-texture and stereo stages harder to control.

## Ownership

- `AuroraRenderView` forwards Android surface lifecycle events.
- `RenderEngine` owns a dedicated `HandlerThread`.
- `RenderLoop` and every EGL/OpenGL object live exclusively on that thread.
- `FrameClock` uses `Choreographer` on the render thread for display-synchronized frames.
- EGL context, surface, shaders, and buffers are explicitly destroyed when the Android surface is lost.

## Current Pipeline

The renderer clears to opaque black and can optionally draw a diagnostic overlay. The overlay confirms that EGL context creation, shader compilation, vertex upload, frame scheduling, and buffer swaps work.

## Video Texture Pipeline

`VideoFrameSampler` creates a `GL_TEXTURE_EXTERNAL_OES`, `SurfaceTexture`, and Android `Surface` on the render thread. `RenderEngine.videoSurface` publishes the surface to the UI, which attaches it to Media3 on the main thread. Media3 retains player ownership; the renderer retains texture and surface ownership.

Frame callbacks run on the render thread and only set an atomic pending flag. The display-synchronized render loop calls `updateTexImage()` when a new frame is available, applies the `SurfaceTexture` transform matrix, and samples the external texture. Player operations never block the render thread.

`ScreenGeometry.aspectFit` preserves the decoded video aspect ratio with letterboxing or pillarboxing. Render overlays run after the video pass, providing the extension point for subtitles, gaze controls, and VR UI.

## Native Boundary

C++ is limited to pure, deterministic matrix operations. Android lifecycle, EGL, OpenGL resource ownership, and UI remain in Kotlin. `NativeMath` falls back to the tested Kotlin implementation if the native library is unavailable.

## Future Stereo Rendering

The render loop keeps frame production separate from scene resources so later phases can render the same scene into left-eye and right-eye framebuffers before lens distortion and final composition.
