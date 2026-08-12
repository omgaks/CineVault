package com.sole.cinevault.glasses

import android.app.Activity
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

/** Visual state for the app-wide RayNeo touch indicator. */
@Stable
class AppWidePointerState internal constructor() {
    var position by mutableStateOf(Offset.Unspecified)
    var pressed by mutableStateOf(false)
    var scrolling by mutableStateOf(false)
}

@Composable
fun rememberAppWidePointerState(sessionKey: Any?): AppWidePointerState =
    remember(sessionKey) { AppWidePointerState() }

/**
 * Observes normal tablet touch without consuming it.
 *
 * The earlier implementation consumed the real event and tried to inject a
 * second synthetic event through Activity.dispatchTouchEvent. Compose then
 * routed that event back through this same parent modifier, leaving ordinary
 * cards and lazy lists with no dependable click/scroll stream. Here the real
 * Android event remains authoritative: one-finger taps, drags and flings reach
 * CineVault unchanged while the halo simply mirrors the contact in RayNeo.
 */
fun Modifier.appWideGlassesInput(
    activity: Activity?,
    sessionKey: Any?,
    state: AppWidePointerState,
    onEmergencyReturnToTablet: () -> Unit
): Modifier = pointerInput(sessionKey, activity) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        state.position = down.position
        state.pressed = true
        state.scrolling = false
        var spread = 1f
        var emergencyTriggered = false

        do {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 5) {
                spread *= event.calculateZoom()
                if (!emergencyTriggered && spread >= 1.35f) {
                    emergencyTriggered = true
                    onEmergencyReturnToTablet()
                }
            }
            pressed.firstOrNull()?.let { change ->
                state.position = change.position
                state.scrolling = change.positionChanged()
            }
        } while (event.changes.any { it.pressed })

        state.pressed = false
        state.scrolling = false
    }
}

/** Draw-only overlay; it never intercepts taps, drags, flings or clicks. */
@Composable
fun BoxScope.AppWideGlassesPointer(state: AppWidePointerState) {
    Canvas(Modifier.fillMaxSize()) {
        val p = if (state.position.x.isFinite() && state.position.y.isFinite()) state.position else center
        val radius = when {
            state.scrolling -> 21f
            state.pressed -> 19f
            else -> 16f
        }
        drawCircle(Color(0x55FFC24D), radius = radius + 6f, center = p)
        drawCircle(Color(0xFFFFC24D), radius = radius, center = p, style = Stroke(width = 3f))
        drawCircle(Color.White, radius = 2.5f, center = p)
    }
}
