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

Phase 5 clears to opaque black and can optionally draw a diagnostic screen mesh. The mesh confirms that EGL context creation, shader compilation, vertex upload, frame scheduling, and buffer swaps work.

The render loop is deliberately independent from Media3. Phase 6 will provide decoded video frames through an external texture without moving playback ownership onto the GL thread.

## Native Boundary

C++ is limited to pure, deterministic matrix operations. Android lifecycle, EGL, OpenGL resource ownership, and UI remain in Kotlin. `NativeMath` falls back to the tested Kotlin implementation if the native library is unavailable.

## Future Stereo Rendering

The render loop keeps frame production separate from scene resources so later phases can render the same scene into left-eye and right-eye framebuffers before lens distortion and final composition.
