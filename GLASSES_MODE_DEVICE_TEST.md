# CineVault Glasses Mode — RayNeo device test

This build is based on the exact repository used for the 11 August 2026 RayNeo test.

## Changes in this build

- Uses a TextureView-backed external PlayerView to address the observed black video while audio and subtitles continued.
- Keeps the external subtitle renderer attached to the same PlayerView.
- Adds a visible Back control with the current title and available ratings.
- Adds SUB, SPEED, and TIMER quick controls on the external dock.
- Adds pointer press/move/release handling for seek-bar dragging.
- Tapping outside an actionable control hides the external controls.
- Initializes the volume HUD from the device's real current media volume; Glasses Mode does not set volume on startup.
- Expands brightness and volume gesture regions to 35% on each side, leaving the central 30% free.
- Removes the obsolete root-level Externaldisplayhelper.kt implementation.

## Test order

1. Connect RayNeo and start a local H.264/AVC video.
2. Confirm picture, audio, and subtitles all render externally.
3. Tap once to show controls and move the halo pointer.
4. Select Back, play/pause, ±10 seconds, SUB, SPEED, and TIMER.
5. Place the pointer over the seek bar, touch-hold on the host, drag, then release.
6. Tap an empty area and confirm controls hide.
7. Confirm connecting and starting playback does not change media volume.
8. Test right-side volume gestures farther inward from the physical edge.
9. Disconnect and reconnect without stopping playback.

## Deliberately still pending

The complete normal Compose player overlay, Subtitle Studio sheets, waveform seek bar, and app-wide pointer navigation are not falsely marked complete in this build. They require extracting the existing Compose UI into a shared external-display host; duplicating them in this Presentation would create two player implementations that drift apart.

Dual-subtitle-aware deletion also remains pending.
