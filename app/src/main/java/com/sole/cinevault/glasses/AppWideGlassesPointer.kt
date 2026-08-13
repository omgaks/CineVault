package com.sole.cinevault.glasses

import android.app.Activity
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

private const val POINTER_SENSITIVITY = 1.15f
private const val POINTER_MARGIN_PX = 12f
private const val MOVE_SLOP_PX = 8f
private const val EMERGENCY_SPREAD = 1.35f

/** State shared by the full-window trackpad surface and its draw-only halo. */
@Stable
class AppWidePointerState internal constructor() {
    var position by mutableStateOf(Offset.Unspecified)
    var pressed by mutableStateOf(false)
    var scrolling by mutableStateOf(false)
}

/** The display id is the session key, so navigation never resets the halo. */
@Composable
fun rememberAppWidePointerState(sessionKey: Any?): AppWidePointerState =
    remember(sessionKey) { AppWidePointerState() }

/**
 * App-wide RayNeo trackpad input used outside playback.
 *
 * One finger moves a persistent relative pointer; a stationary one-finger tap
 * clicks at that pointer. Two-finger movement is replayed as a normal drag at
 * the pointer, allowing existing Compose lazy lists, rows and detail pages to
 * keep their native scrolling behaviour. Physical touch events are consumed.
 * Replayed events use the mouse source and therefore bypass this detector while
 * continuing through the real content hierarchy below it.
 */
fun Modifier.appWideGlassesInput(
    activity: Activity?,
    sessionKey: Any?,
    state: AppWidePointerState,
    onEmergencyReturnToTablet: () -> Unit
): Modifier = pointerInput(sessionKey, activity) {
    fun pointerReady(): Boolean =
        state.position.x.isFinite() && state.position.y.isFinite()

    fun clamp(point: Offset): Offset = Offset(
        point.x.coerceIn(POINTER_MARGIN_PX, (size.width - POINTER_MARGIN_PX).coerceAtLeast(POINTER_MARGIN_PX)),
        point.y.coerceIn(POINTER_MARGIN_PX, (size.height - POINTER_MARGIN_PX).coerceAtLeast(POINTER_MARGIN_PX))
    )

    fun dispatchSynthetic(
        action: Int,
        point: Offset,
        downTime: Long,
        eventTime: Long = SystemClock.uptimeMillis()
    ) {
        val host = activity ?: return
        val content = host.findViewById<View>(android.R.id.content)
        val origin = IntArray(2)
        content?.getLocationInWindow(origin)
        MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            point.x + origin[0],
            point.y + origin[1],
            0
        ).also { event ->
            event.source = InputDevice.SOURCE_MOUSE
            try {
                host.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
    }

    awaitEachGesture {
        val firstDown = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial
        )

        // Synthetic clicks and drags must reach the actual content unchanged.
        if (firstDown.type == PointerType.Mouse) return@awaitEachGesture

        if (!pointerReady()) {
            state.position = Offset(size.width / 2f, size.height / 2f)
        } else {
            state.position = clamp(state.position)
        }

        firstDown.consume()
        state.pressed = true
        state.scrolling = false

        var lastOneFingerPosition = firstDown.position
        var lastTwoFingerCentroid = Offset.Unspecified
        var oneFingerTravel = 0f
        var maximumPointerCount = 1
        var emergencySpread = 1f
        var emergencyTriggered = false
        var syntheticDragDownTime = 0L
        var syntheticDragPoint = state.position

        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressedChanges = event.changes.filter { it.pressed }
            maximumPointerCount = maxOf(maximumPointerCount, pressedChanges.size)

            if (pressedChanges.size >= 5) {
                emergencySpread *= event.calculateZoom()
                if (!emergencyTriggered && emergencySpread >= EMERGENCY_SPREAD) {
                    emergencyTriggered = true
                    onEmergencyReturnToTablet()
                }
                event.changes.forEach { it.consume() }
                continue
            }

            if (pressedChanges.size >= 2) {
                val centroid = pressedChanges
                    .fold(Offset.Zero) { total, change -> total + change.position } /
                    pressedChanges.size.toFloat()

                if (lastTwoFingerCentroid.x.isFinite() && lastTwoFingerCentroid.y.isFinite()) {
                    val delta = centroid - lastTwoFingerCentroid
                    if (abs(delta.x) > 0.5f || abs(delta.y) > 0.5f) {
                        if (syntheticDragDownTime == 0L) {
                            syntheticDragDownTime = SystemClock.uptimeMillis()
                            syntheticDragPoint = state.position
                            dispatchSynthetic(
                                MotionEvent.ACTION_DOWN,
                                syntheticDragPoint,
                                syntheticDragDownTime,
                                syntheticDragDownTime
                            )
                        }
                        syntheticDragPoint = clamp(syntheticDragPoint + delta)
                        dispatchSynthetic(
                            MotionEvent.ACTION_MOVE,
                            syntheticDragPoint,
                            syntheticDragDownTime
                        )
                        state.scrolling = true
                    }
                }
                lastTwoFingerCentroid = centroid
                event.changes.forEach { it.consume() }
            } else if (pressedChanges.size == 1 && maximumPointerCount == 1) {
                lastTwoFingerCentroid = Offset.Unspecified
                val change = pressedChanges.first()
                val delta = change.position - lastOneFingerPosition
                if (abs(delta.x) > 0.25f || abs(delta.y) > 0.25f) {
                    oneFingerTravel += delta.getDistance()
                    state.position = clamp(state.position + delta * POINTER_SENSITIVITY)
                    lastOneFingerPosition = change.position
                }
                change.consume()
            } else {
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })

        when {
            syntheticDragDownTime != 0L -> {
                dispatchSynthetic(
                    MotionEvent.ACTION_UP,
                    syntheticDragPoint,
                    syntheticDragDownTime
                )
            }

            maximumPointerCount == 1 && oneFingerTravel < MOVE_SLOP_PX -> {
                val now = SystemClock.uptimeMillis()
                dispatchSynthetic(MotionEvent.ACTION_DOWN, state.position, now, now)
                dispatchSynthetic(MotionEvent.ACTION_UP, state.position, now)
            }
        }

        state.pressed = false
        state.scrolling = false
    }
}

/** Draw-only overlay. Input belongs to the parent modifier, so this blocks nothing. */
@Composable
fun BoxScope.AppWideGlassesPointer(state: AppWidePointerState) {
    Canvas(Modifier.fillMaxSize()) {
        val point = if (state.position.x.isFinite() && state.position.y.isFinite()) {
            state.position
        } else {
            center
        }
        val radius = when {
            state.scrolling -> 21f
            state.pressed -> 19f
            else -> 16f
        }
        drawCircle(Color(0x55FFC24D), radius = radius + 6f, center = point)
        drawCircle(
            Color(0xFFFFC24D),
            radius = radius,
            center = point,
            style = Stroke(width = 3f)
        )
        drawCircle(Color.White, radius = 2.5f, center = point)
        if (state.scrolling) {
            drawLine(
                Color(0xFFFFC24D),
                point - Offset(0f, 30f),
                point + Offset(0f, 30f),
                strokeWidth = 3f
            )
        }
    }
}
