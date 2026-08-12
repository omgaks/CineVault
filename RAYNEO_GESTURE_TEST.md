# RayNeo Gesture Build — Device Test

This build preserves the atomic Media3 surface-handoff correction and adds the stable dark-tablet gesture map.

## Gesture map

- Single tap: play/pause. If the halo is over a visible control, the tap activates that control instead.
- Double tap: show/hide RayNeo controls.
- Horizontal drag starting in the center third: precision seek with timestamp and available cached frame preview in the glasses.
- Vertical drag in the left third: tablet/app brightness.
- Vertical drag in the right third: Android media volume.
- Two-finger pinch/pan: scale and pan the external RayNeo video viewport from 1× to 3×.
- Long press: open Quick Subtitles (on/off, text size, pin controls, close).
- Swipe inward from the left 10% edge: previous episode/video in an eligible TV or restricted-folder group.
- Swipe inward from the right 10% edge: next episode/video in an eligible TV or restricted-folder group.
- Halo pointer: visible only with controls or Quick Subtitles. Zone gestures remain active; use a horizontal drag begun in either outer third, or a non-horizontal center drag, to reposition it.
- Five-finger outward spread: end only the current Glasses Mode session and return the live player to the tablet.

## Learn gestures

- Open the RayNeo controls and select **GESTURES** to display the complete touchpad guide inside the glasses.
- Select **GOT IT** to return to the controls.

## Test order

1. Confirm video, audio and subtitles all render in RayNeo.
2. Test single and double tap with controls hidden.
3. Drag horizontally from the center and confirm timestamp/frame preview, then release and confirm the seek.
4. Test left brightness and right volume with controls both hidden and visible.
5. Pinch to 2×, pan, then pinch back to 1×.
6. Long-press, operate each Quick Subtitles button with the halo, then close it.
7. In a TV group, test left-edge previous and right-edge next.
8. Confirm Back and the existing external control dock still work.
9. Open **GESTURES**, confirm the guide is readable, and close it with **GOT IT**.
10. Use an ordinary two-finger pinch and confirm it never exits Glasses Mode.
11. Place five fingers together, spread them outward by roughly 35%, and confirm the RayNeo presentation closes while the same player, position and playback state return to the tablet.
12. Confirm Glasses Mode does not immediately restart while the same display remains connected.

RayNeo hardware optical brightness is not exposed by standard Android display APIs; the left-zone gesture controls the host activity brightness.
