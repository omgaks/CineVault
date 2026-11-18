package com.sole.cinevault.glasses.halo

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reports the held speed (or null when released). The detector only observes; it never
 * consumes events, so pinch/pan, taps and the pointer keep working underneath.
 */
fun Modifier.haloSpeedHoldGestures(
    gestureKey: Any?,
    onHeldSpeedChanged: (Float?) -> Unit,
): Modifier = pointerInput(gestureKey) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var active: Float? = null
        try {
            var carry: androidx.compose.ui.input.pointer.PointerEvent? = null
            while (true) {
                // Re-use the event that interrupted the last hold check so a finger added
                // mid-wait is evaluated straight away instead of waiting for the next move.
                val event = carry ?: awaitPointerEvent(PointerEventPass.Initial)
                carry = null
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break

                val wanted = HaloSpeedHoldPolicy.speedFor(pressed.size)
                if (wanted == null) {
                    if (active != null) { active = null; onHeldSpeedChanged(null) }
                    continue
                }
                if (wanted == active) continue

                // Fingers changed: they must now stay still for HOLD_MS before the speed applies.
                val startCentroid = pressed.fold(Offset.Zero) { a, c -> a + c.position } / pressed.size.toFloat()
                val startSize = event.calculateCentroidSize(useCurrent = true).coerceAtLeast(1f)
                val stayedStill = withTimeoutOrNull(HaloSpeedHoldPolicy.HOLD_MS) {
                    var still = true
                    while (still) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val p = e.changes.filter { it.pressed }
                        if (p.size != pressed.size) { carry = e; return@withTimeoutOrNull false }
                        val centroid = p.fold(Offset.Zero) { a, c -> a + c.position } / p.size.toFloat()
                        val size = e.calculateCentroidSize(useCurrent = true).coerceAtLeast(1f)
                        still = HaloSpeedHoldPolicy.isStill((centroid - startCentroid).getDistance(), size / startSize)
                    }
                    false
                }
                // withTimeoutOrNull returns null when the whole hold time passed without movement.
                if (stayedStill == null) {
                    active = wanted
                    onHeldSpeedChanged(wanted)
                }
            }
        } finally {
            if (active != null) onHeldSpeedChanged(null)
        }
    }
}
