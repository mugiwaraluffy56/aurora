# Contributing

## Branches

Use short, descriptive branch names:

- `feature/library-search`
- `fix/playback-resume`
- `engine/sphere-projection`

## Commits

Keep commits focused and buildable. Use concise, human-written subjects.

## Architecture

Aurora is split by runtime responsibility:

- `app`: Android entry point and wiring only.
- `core`: shared models, math, geometry, telemetry, and pure logic.
- `domain`: business contracts and use cases.
- `data`: Room, DataStore, MediaStore, and repository implementations.
- `engine`: playback, rendering, tracking, input, native runtime systems.
- `feature`: user-facing Compose screens.
- `design-system`: shared theme and components.
- `testing`: shared fixtures and test utilities.

Domain code should not depend on Android UI, Room, ExoPlayer, EGL, or Compose.

## Testing

Before opening a pull request, run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Renderer, tracking, playback, and storage changes should include focused tests where practical and manual notes for device behavior.
