package com.sole.cinevault.glasses

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastAny
import com.sole.cinevault.glasses.halo.HaloPointerMapper
import com.sole.cinevault.glasses.halo.HaloRemoteInputSession
import com.sole.cinevault.glasses.halo.PAD_HOLD_SLOP_FRACTION
import com.sole.cinevault.glasses.halo.PAD_HOLD_TO_DRAG_MS
import com.sole.cinevault.glasses.halo.PAD_SECOND_FINGER_WAIT_MS
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot

/**
 * Touch rules for the tablet pad while the glasses show the APP (not a film):
 *
 *  - one finger: moves the glasses pointer only; a quick tap clicks. The page never scrolls.
 *  - two fingers: scroll the page. The pointer stays exactly where it was left.
 *  - one finger held still ~0.5 s, then dragged: a real drag (for sliders).
 *
 * Observes in the Final pass and never consumes, like the player pad.
 */
internal fun Modifier.appPadTouch(
    allowScroll: Boolean = true,
    onActivity: () -> Unit = {},
): Modifier = pointerInput(allowScroll) {
    awaitEachGesture {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@awaitEachGesture

        fun send(pos: Offset, pressed: Boolean, moveOnly: Boolean) {
            HaloRemoteInputSession.publish(
                sample = HaloPointerMapper.sample(pos.x, pos.y, w, h, pressed)
                    .copy(moveOnly = moveOnly),
                sourceWidthPx = w,
                sourceHeightPx = h,
            )
        }

        val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        onActivity()
        var pos = first.position

        // Give a second finger a moment to land before treating this as a pointer press.
        var early: Int? = null
        withTimeoutOrNull(PAD_SECOND_FINGER_WAIT_MS) {
            while (early == null) {
                val e = awaitPointerEvent(PointerEventPass.Final)
                e.changes.firstOrNull { it.id == first.id }?.let { pos = it.position }
                val down = e.changes.count { it.pressed }
                early = when {
                    down >= 2 -> 1
                    down == 0 -> 2
                    else -> null
                }
            }
        }

        when (early) {
            1 -> {
                twoFingerScroll(w, h, allowScroll)
                return@awaitEachGesture
            }
            2 -> { // quick tap
                send(first.position, pressed = true, moveOnly = true)
                send(pos, pressed = false, moveOnly = true)
                return@awaitEachGesture
            }
        }

        // One finger is staying down: it drives the pointer.
        send(pos, pressed = true, moveOnly = true)
        var held = false
        var anchor = pos
        var anchorTime = System.currentTimeMillis()
        while (true) {
            val event = if (held) {
                awaitPointerEvent(PointerEventPass.Final)
            } else {
                val left = PAD_HOLD_TO_DRAG_MS - (System.currentTimeMillis() - anchorTime)
                withTimeoutOrNull(left.coerceAtLeast(1L)) { awaitPointerEvent(PointerEventPass.Final) }
            }

            if (event == null) {
                // Held still long enough: from here the finger really drags.
                held = true
                send(pos, pressed = true, moveOnly = false)
                continue
            }

            onActivity()
            val down = event.changes.count { it.pressed }
            if (down >= 2) {
                HaloRemoteInputSession.publishCancelPress()
                twoFingerScroll(w, h, allowScroll)
                return@awaitEachGesture
            }
            val mine = event.changes.firstOrNull { it.id == first.id } ?: break
            pos = mine.position
            if (down == 0 || !mine.pressed) {
                send(pos, pressed = false, moveOnly = !held)
                break
            }
            if (!held) {
                val travel = hypot((pos.x - anchor.x) / w, (pos.y - anchor.y) / h)
                if (travel > PAD_HOLD_SLOP_FRACTION) {
                    anchor = pos
                    anchorTime = System.currentTimeMillis()
                }
            }
            send(pos, pressed = true, moveOnly = !held)
        }
    }
}

/** Runs until every finger is up; the first lifted finger ends the scroll without a jump. */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.twoFingerScroll(
    w: Int,
    h: Int,
    allowScroll: Boolean,
) {
    if (!allowScroll) {
        // Inside a film the two-finger scroll must never nudge a slider under the pointer.
        do {
            val event = awaitPointerEvent(PointerEventPass.Final)
        } while (event.changes.fastAny { it.pressed })
        return
    }
    HaloRemoteInputSession.publishScroll(HaloRemoteInputSession.ScrollPhase.START, 0f, 0f)
    var previous: Offset? = null
    var scrolling = true
    do {
        val event = awaitPointerEvent(PointerEventPass.Final)
        val pressed = event.changes.filter { it.pressed }
        if (scrolling && pressed.size >= 2) {
            val centre = Offset(
                pressed.map { it.position.x }.average().toFloat(),
                pressed.map { it.position.y }.average().toFloat(),
            )
            previous?.let {
                HaloRemoteInputSession.publishScroll(
                    HaloRemoteInputSession.ScrollPhase.MOVE,
                    (centre.x - it.x) / w,
                    (centre.y - it.y) / h,
                )
            }
            previous = centre
        } else if (scrolling) {
            scrolling = false
            HaloRemoteInputSession.publishScroll(HaloRemoteInputSession.ScrollPhase.END, 0f, 0f)
        }
    } while (event.changes.fastAny { it.pressed })
    if (scrolling) {
        HaloRemoteInputSession.publishScroll(HaloRemoteInputSession.ScrollPhase.END, 0f, 0f)
    }
}
