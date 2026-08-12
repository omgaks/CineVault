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
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/** State shared by the full-window input surface and the non-intercepting halo. */
@Stable
class AppWidePointerState internal constructor() {
    var position by mutableStateOf(Offset.Unspecified)
    var pressed by mutableStateOf(false)
    var scrolling by mutableStateOf(false)
    internal var injecting = false
}

@Composable
fun rememberAppWidePointerState(sessionKey: Any?): AppWidePointerState =
    remember(sessionKey) { AppWidePointerState() }

/**
 * Full-window RayNeo touchpad input.
 *
 * One finger moves the halo, a tap dispatches a genuine touch at the halo,
 * and a two-finger drag dispatches a genuine drag at the halo so every
 * Compose LazyColumn/LazyRow/grid and detail page can scroll naturally.
 * Physical touch events are consumed; injected mouse-source events are not,
 * which lets them reach the real clickable/scrollable content below.
 */
fun Modifier.appWideGlassesInput(
    activity: Activity?,
    sessionKey: Any?,
    state: AppWidePointerState,
    onEmergencyReturnToTablet: () -> Unit
): Modifier = pointerInput(sessionKey, activity) {
    fun ready() = state.position.x.isFinite() && state.position.y.isFinite()

    fun dispatch(action: Int, x: Float, y: Float, downTime: Long, eventTime: Long = SystemClock.uptimeMillis()) {
        val host = activity ?: return
        // Pointer coordinates are local to the Compose content root. Activity
        // dispatch expects window coordinates, including any status-bar inset.
        val content = host.findViewById<View>(android.R.id.content)
        val origin = IntArray(2)
        content?.getLocationInWindow(origin)
        val event = MotionEvent.obtain(downTime, eventTime, action, x + origin[0], y + origin[1], 0).apply {
            source = InputDevice.SOURCE_MOUSE
        }
        state.injecting = true
        try {
            host.dispatchTouchEvent(event)
        } finally {
            state.injecting = false
            event.recycle()
        }
    }

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // Synthetic events must continue down the normal Compose hit path.
        if (state.injecting || down.type == androidx.compose.ui.input.pointer.PointerType.Mouse) {
            return@awaitEachGesture
        }
        down.consume()
        if (!ready()) state.position = Offset(size.width / 2f, size.height / 2f)

        var lastCentroid = down.position
        var maxPointers = 1
        var moved = false
        var totalPointerTravel = 0f
        var spread = 1f
        var scrollDownTime = 0L
        var scrollPoint = state.position
        var wasTwoFinger = false

        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            maxPointers = maxOf(maxPointers, pressed.size)

            if (pressed.size >= 5) {
                spread *= event.calculateZoom()
                event.changes.forEach { it.consume() }
                if (spread >= 1.35f) {
                    state.pressed = false
                    state.scrolling = false
                    onEmergencyReturnToTablet()
                }
                continue
            }

            if (pressed.size >= 2) {
                val centroid = pressed.fold(Offset.Zero) { total, change -> total + change.position } / pressed.size.toFloat()
                val delta = if (wasTwoFinger) centroid - lastCentroid else Offset.Zero
                wasTwoFinger = true
                if (abs(delta.x) > 0.5f || abs(delta.y) > 0.5f) {
                    moved = true
                    state.scrolling = true
                    if (scrollDownTime == 0L) {
                        scrollDownTime = SystemClock.uptimeMillis()
                        scrollPoint = state.position
                        dispatch(MotionEvent.ACTION_DOWN, scrollPoint.x, scrollPoint.y, scrollDownTime, scrollDownTime)
                    }
                    // Finger movement and content movement follow normal direct-touch behaviour.
                    scrollPoint = Offset(
                        (scrollPoint.x + delta.x).coerceIn(1f, size.width - 1f),
                        (scrollPoint.y + delta.y).coerceIn(1f, size.height - 1f)
                    )
                    dispatch(MotionEvent.ACTION_MOVE, scrollPoint.x, scrollPoint.y, scrollDownTime)
                }
                lastCentroid = centroid
                event.changes.forEach { it.consume() }
            } else if (pressed.size == 1 && maxPointers == 1) {
                wasTwoFinger = false
                val change = pressed.first()
                val delta = change.position - lastCentroid
                if (abs(delta.x) > 0.25f || abs(delta.y) > 0.25f) {
                    totalPointerTravel += delta.getDistance()
                    if (totalPointerTravel > 8f) moved = true
                    state.position = Offset(
                        (state.position.x + delta.x * 1.35f).coerceIn(12f, size.width - 12f),
                        (state.position.y + delta.y * 1.35f).coerceIn(12f, size.height - 12f)
                    )
                    lastCentroid = change.position
                }
                change.consume()
            }
        } while (event.changes.any { it.pressed })

        if (scrollDownTime != 0L) {
            dispatch(MotionEvent.ACTION_UP, scrollPoint.x, scrollPoint.y, scrollDownTime)
            state.scrolling = false
        } else if (maxPointers == 1 && !moved) {
            val now = SystemClock.uptimeMillis()
            state.pressed = true
            dispatch(MotionEvent.ACTION_DOWN, state.position.x, state.position.y, now, now)
            dispatch(MotionEvent.ACTION_UP, state.position.x, state.position.y, now)
            state.pressed = false
        }
    }
}

/** Draw-only overlay. Input is owned by the full-window parent, so this never blocks content. */
@Composable
fun BoxScope.AppWideGlassesPointer(state: AppWidePointerState) {
    Canvas(Modifier.fillMaxSize()) {
        val p = if (state.position.x.isFinite() && state.position.y.isFinite()) {
            state.position
        } else {
            center
        }
        val radius = when {
            state.scrolling -> 21f
            state.pressed -> 19f
            else -> 16f
        }
        drawCircle(Color(0x55FFC24D), radius = radius + 6f, center = p)
        drawCircle(Color(0xFFFFC24D), radius = radius, center = p, style = Stroke(width = 3f))
        drawCircle(Color.White, radius = 2.5f, center = p)
        if (state.scrolling) {
            drawLine(Color(0xFFFFC24D), p - Offset(0f, 30f), p + Offset(0f, 30f), strokeWidth = 3f)
        }
    }
}
