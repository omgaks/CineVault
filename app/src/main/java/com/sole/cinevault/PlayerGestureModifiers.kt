package com.sole.cinevault

import android.graphics.Rect
import android.os.Build
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * PlayerGestureModifiers.kt
 *
 * First extraction out of VideoPlayerScreen.kt, per Review 1's recommended
 * split order ("GestureController"). This is the raw touch-gesture code
 * for the main video surface — single-tap, double-tap seek/zoom-toggle,
 * single-finger drag (brightness/volume + edge-swipe next-episode), and
 * two-finger pinch/pan — plus the separate opt-in subtitle gesture zone
 * (pinch = text size, drag = sync offset / position, double-tap = reset,
 * long-press = play/pause).
 *
 * Deliberately callback-driven rather than taking raw MutableState objects.
 * Every `by remember { mutableStateOf(...) }` property in VideoPlayerScreen
 * stays declared exactly as it already is — this file never touches how
 * that state is held, only pulls the gesture-DETECTION code out. All the
 * "what does this gesture actually mean for app state" decisions that are
 * genuinely positional (which half of the screen, which edge, drag
 * direction) still happen in here, since that's shape-of-the-gesture logic,
 * not business logic. Everything else is a lambda back into
 * VideoPlayerScreen, which still owns all the state it always did.
 *
 * Pure move, not a rewrite — no behavior change from the original inline
 * version.
 */

/**
 * Excludes the right-edge strip from the OS predictive-back gesture, then
 * chains the three touch detectors for the main video surface.
 *
 * @param edgeSwipeNextEnabled current value of `showPrevNextButtons` at the
 *   time a drag gesture ends — passed as a plain Boolean rather than a
 *   MutableState since this modifier never needs to observe it recompose,
 *   only read it when a gesture actually completes.
 * @param onDragSettled fired on both drag-end and drag-cancel — bumps
 *   whatever gesture keys VideoPlayerScreen uses to auto-hide the
 *   brightness/volume HUD circles.
 */
fun Modifier.videoPlaybackGestures(
    view: View,
    videoPathKey: Any?,
    episodeListKey: Any?,
    edgeSwipeNextEnabled: () -> Boolean,
    onTap: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onToggleZoomMode: () -> Unit,
    onDragSettled: () -> Unit,
    onEdgeSwipeNext: () -> Unit,
    onBrightnessDrag: (deltaY: Float) -> Unit,
    onVolumeDrag: (deltaY: Float) -> Unit,
    onPinchZoomPan: (zoom: Float, pan: Offset) -> Unit,
): Modifier = this
    // Reserves ONLY the right-edge strip from Android's system back
    // gesture — the zone the edge-swipe-to-next drag detector below
    // needs, since without this a right-edge swipe gets intercepted by
    // the OS's predictive-back gesture first. Left edge is deliberately
    // NOT excluded — see original VideoPlayerScreen.kt history for why
    // (some OEM overlays don't respect exclusion rects, so native
    // BackHandler is left to own the left edge instead of double-firing).
    .onGloballyPositioned { coordinates ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bounds = coordinates.boundsInWindow()
            val rightEdgeStart = bounds.left + (bounds.width * 0.70f)
            view.systemGestureExclusionRects = listOf(
                Rect(
                    rightEdgeStart.roundToInt(),
                    bounds.top.roundToInt(),
                    bounds.right.roundToInt(),
                    bounds.bottom.roundToInt()
                )
            )
        }
    }

    .pointerInput(videoPathKey) {
        detectTapGestures(
            onTap = { onTap() },
            onDoubleTap = { offset ->
                val w = size.width
                when {
                    offset.x < w * 0.45f -> onSeekBack()
                    offset.x > w * 0.55f -> onSeekForward()
                    else -> onToggleZoomMode()
                }
            }
        )
    }
    .pointerInput(videoPathKey, episodeListKey) {
        var dragStartX = 0f
        var dragTotalX = 0f
        var dragTotalY = 0f
        detectDragGestures(
            onDragStart = { offset ->
                dragStartX = offset.x; dragTotalX = 0f; dragTotalY = 0f
            },
            onDragEnd = {
                onDragSettled()
                val w = size.width.toFloat()
                val isHorizontal = abs(dragTotalX) > abs(dragTotalY) * 1.5f &&
                        abs(dragTotalX) > 48.dp.toPx()
                // Left edge intentionally NOT handled here — see the
                // exclusion-rect comment above.
                if (isHorizontal && dragStartX > w * 0.88f && dragTotalX < 0f) {
                    if (edgeSwipeNextEnabled()) onEdgeSwipeNext()
                }
            },
            onDragCancel = { onDragSettled() },
            onDrag = { change, dragAmount ->
                dragTotalX += dragAmount.x; dragTotalY += dragAmount.y
                val x = change.position.x; val w = size.width
                val absX = abs(dragAmount.x); val absY = abs(dragAmount.y)
                val gestureIsVertical = abs(dragTotalY) >= abs(dragTotalX)
                if (gestureIsVertical && absY > absX) {
                    // Wider, easier-to-reach vertical zones for a dark host
                    // used as a glasses controller. The centre 30% remains
                    // free for seeking/pointer movement.
                    if (x < w * 0.35f) onBrightnessDrag(dragAmount.y)
                    else if (x > w * 0.65f) onVolumeDrag(dragAmount.y)
                }
            }
        )
    }
    // FIX (E2, corrected): detectTransformGestures ALSO processes a single
    // finger as a valid one-pointer pan, silently starving the separate
    // single-finger drag detector above that brightness/volume/seek relies
    // on. This explicitly requires 2+ simultaneous pointers before it ever
    // touches (or consumes) anything.
    .pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                if (event.changes.size >= 2) {
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    if (zoom != 1f || pan != Offset.Zero) {
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                        onPinchZoomPan(zoom, pan)
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

/** Stable, zone-based controller used only while a secondary RayNeo display is active. */
fun Modifier.rayNeoTouchpadGestures(
    view: View,
    gestureKey: Any?,
    controlsVisible: () -> Boolean,
    canChangeEpisode: () -> Boolean,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekDelta: (fractionDelta: Float) -> Unit,
    onSeekEnd: () -> Unit,
    onBrightnessDrag: (Float) -> Unit,
    onVolumeDrag: (Float) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPointerMove: (Offset) -> Unit,
    onPointerClick: () -> Boolean,
    onPinchZoomPan: (Float, Offset) -> Unit,
    onEmergencyReturnToTablet: () -> Unit,
    onGestureEnd: () -> Unit,
): Modifier = this
    .onGloballyPositioned { coordinates ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val b = coordinates.boundsInWindow()
            view.systemGestureExclusionRects = listOf(Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt()))
        }
    }
    .pointerInput(gestureKey) {
        detectTapGestures(
            onTap = {
                if (!controlsVisible() || !onPointerClick()) onSingleTap()
            },
            onDoubleTap = { onDoubleTap() },
            onLongPress = { onLongPress() }
        )
    }
    .pointerInput(gestureKey) {
        var startX = 0f
        var totalX = 0f
        var totalY = 0f
        var seeking = false
        detectDragGestures(
            onDragStart = { startX = it.x; totalX = 0f; totalY = 0f; seeking = false },
            onDragEnd = {
                val w = size.width.toFloat()
                val horizontal = abs(totalX) > abs(totalY) * 1.35f && abs(totalX) > 48.dp.toPx()
                when {
                    startX < w * 0.10f && horizontal && totalX > 0f && canChangeEpisode() -> onPrevious()
                    startX > w * 0.90f && horizontal && totalX < 0f && canChangeEpisode() -> onNext()
                    seeking -> onSeekEnd()
                }
                onGestureEnd()
            },
            onDragCancel = { if (seeking) onSeekEnd(); onGestureEnd() },
            onDrag = { change, drag ->
                totalX += drag.x; totalY += drag.y
                val w = size.width.toFloat()
                val vertical = abs(totalY) > abs(totalX) * 1.15f
                val horizontal = abs(totalX) > abs(totalY) * 1.15f
                when {
                    startX < w * 0.10f || startX > w * 0.90f -> Unit
                    startX < w / 3f && vertical -> { change.consume(); onBrightnessDrag(drag.y) }
                    startX > w * 2f / 3f && vertical -> { change.consume(); onVolumeDrag(drag.y) }
                    // Visible controls turn the centre into a true pointer
                    // surface. This check must precede direct seeking or a
                    // normal attempt to reach a button scrubs the movie.
                    controlsVisible() -> { change.consume(); onPointerMove(drag) }
                    startX in (w / 3f)..(w * 2f / 3f) && horizontal -> {
                        change.consume()
                        if (!seeking) { seeking = true; onSeekStart() }
                        onSeekDelta(drag.x / w)
                    }
                }
            }
        )
    }
    .pointerInput(gestureKey) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var fiveFingerSpread = 1f
            var emergencyTriggered = false
            do {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 5) {
                    fiveFingerSpread *= event.calculateZoom()
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                    // A deliberate 35% five-finger spread is the emergency
                    // escape hatch. Two-finger viewport zoom can never enter
                    // this branch, and the one-shot guard prevents repeats.
                    if (!emergencyTriggered && fiveFingerSpread >= 1.35f) {
                        emergencyTriggered = true
                        onEmergencyReturnToTablet()
                    }
                } else if (event.changes.size >= 2 && !emergencyTriggered) {
                    val zoom = event.calculateZoom(); val pan = event.calculatePan()
                    if (zoom != 1f || pan != Offset.Zero) {
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                        onPinchZoomPan(zoom, pan)
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

/**
 * Opt-in subtitle gesture zone: pinch to resize subtitle text, horizontal
 * drag to adjust sync offset, vertical drag to reposition, double-tap to
 * reset sync, long-press to toggle play/pause. Caller (VideoPlayerScreen)
 * still owns sizing/placement of the Box this attaches to, and still
 * renders the feedback pill.
 *
 * @param onVerticalPositionDrag receives the already-computed fraction
 *   delta (`-pan.y / zoneHeight * 0.6f`), matching the original sign
 *   convention (dragging up raises the subtitle).
 */
fun Modifier.subtitleGestureZone(
    enabledKey: Any?,
    onPinchTextSize: (zoom: Float) -> Unit,
    onHorizontalSyncDrag: (deltaX: Float) -> Unit,
    onVerticalPositionDrag: (deltaFraction: Float) -> Unit,
    onDoubleTapResetSync: () -> Unit,
    onLongPressTogglePlayback: () -> Unit,
): Modifier = this
    .pointerInput(enabledKey) {
        detectTransformGestures { _, pan, zoom, _ ->
            if (zoom != 1f) {
                onPinchTextSize(zoom)
            } else if (abs(pan.x) > abs(pan.y)) {
                onHorizontalSyncDrag(pan.x)
            } else {
                val deltaFraction = -pan.y / size.height.toFloat() * 0.6f
                onVerticalPositionDrag(deltaFraction)
            }
        }
    }
    .pointerInput(enabledKey) {
        detectTapGestures(
            onDoubleTap = { onDoubleTapResetSync() },
            onLongPress = { onLongPressTogglePlayback() }
        )
    }
