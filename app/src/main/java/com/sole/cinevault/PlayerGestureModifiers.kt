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
 * Touch gestures for normal host-device video playback and the opt-in
 * subtitle gesture zone.
 *
 * External-display/Halo input does NOT live here. It is owned by the
 * dedicated Halo playback path, so the old parallel glasses touchpad
 * controller has been removed rather than kept as a second gesture system.
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
                dragStartX = offset.x
                dragTotalX = 0f
                dragTotalY = 0f
            },
            onDragEnd = {
                onDragSettled()
                val w = size.width.toFloat()
                val isHorizontal =
                    abs(dragTotalX) > abs(dragTotalY) * 1.5f &&
                        abs(dragTotalX) > 48.dp.toPx()

                if (isHorizontal && dragStartX > w * 0.88f && dragTotalX < 0f) {
                    if (edgeSwipeNextEnabled()) onEdgeSwipeNext()
                }
            },
            onDragCancel = { onDragSettled() },
            onDrag = { change, dragAmount ->
                dragTotalX += dragAmount.x
                dragTotalY += dragAmount.y
                val x = change.position.x
                val w = size.width
                val absX = abs(dragAmount.x)
                val absY = abs(dragAmount.y)
                val gestureIsVertical = abs(dragTotalY) >= abs(dragTotalX)

                if (gestureIsVertical && absY > absX) {
                    if (x < w * 0.35f) {
                        onBrightnessDrag(dragAmount.y)
                    } else if (x > w * 0.65f) {
                        onVolumeDrag(dragAmount.y)
                    }
                }
            }
        )
    }
    .pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                if (event.changes.size >= 2) {
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    if (zoom != 1f || pan != Offset.Zero) {
                        event.changes.forEach {
                            if (it.positionChanged()) it.consume()
                        }
                        onPinchZoomPan(zoom, pan)
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

/**
 * Opt-in subtitle gesture zone: pinch to resize subtitle text, horizontal
 * drag to adjust sync offset, vertical drag to reposition, double-tap to
 * reset sync, long-press to toggle play/pause.
 *
 * Subtitle editing remains independent from Halo/external-display navigation.
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
