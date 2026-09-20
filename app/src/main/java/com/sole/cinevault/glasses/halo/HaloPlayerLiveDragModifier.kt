package com.sole.cinevault.glasses.halo

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * D2-18 — controlled live handover, part 1.
 *
 * This modifier owns the SINGLE-FINGER drag surface for glasses playback:
 *
 *   top 30%   -> seek
 *   left 20%  -> brightness
 *   right 20% -> volume
 *   centre    -> existing CineVault Halo pointer movement
 *
 * The important D2-18 change is that CANONICAL_UI is no longer a dead/no-op
 * branch. Centre drags are forwarded to the existing external pointer callback,
 * so handing drag ownership away from glassesTouchpadGestures does not regress
 * the usable Halo pointer.
 *
 * Tap/double-tap/long-press, pinch and five-finger emergency are intentionally
 * NOT implemented here. They stay on the legacy support path until their own
 * migration slices.
 */
fun Modifier.haloPlayerLiveDragGestures(
    gestureKey: Any?,
    playbackPositionMs: () -> Long,
    durationMs: () -> Long,
    liveSession: HaloPlayerLiveSession,
    onCanonicalPointerMove: (Offset) -> Unit,
    onGestureEnd: () -> Unit,
): Modifier =
    pointerInput(gestureKey, liveSession) {
        detectDragGestures(
            onDragStart = { start ->
                liveSession.onDragStart(
                    xPx = start.x,
                    yPx = start.y,
                    widthPx = size.width,
                    heightPx = size.height,
                    playbackPositionMs = playbackPositionMs(),
                    durationMs = durationMs(),
                )
            },
            onDrag = { change, dragAmount ->
                val intent =
                    liveSession.onDrag(
                        xPx = change.position.x,
                        yPx = change.position.y,
                        widthPx = size.width,
                        heightPx = size.height,
                    )

                when (HaloPlayerLiveDragPolicy.route(intent)) {
                    HaloPlayerLiveDragRoute.PLAYER_ACTION -> {
                        change.consume()
                    }

                    HaloPlayerLiveDragRoute.CANONICAL_POINTER -> {
                        onCanonicalPointerMove(dragAmount)
                        change.consume()
                    }

                    HaloPlayerLiveDragRoute.NONE -> Unit
                }
            },
            onDragEnd = {
                liveSession.onDragEnd()
                onGestureEnd()
            },
            onDragCancel = {
                liveSession.onDragCancel()
                onGestureEnd()
            },
        )
    }

enum class HaloPlayerLiveDragRoute {
    PLAYER_ACTION,
    CANONICAL_POINTER,
    NONE,
}

object HaloPlayerLiveDragPolicy {

    fun route(intent: HaloPlayerGestureIntent?): HaloPlayerLiveDragRoute =
        when (intent) {
            is HaloPlayerGestureIntent.Brightness,
            is HaloPlayerGestureIntent.Volume,
            is HaloPlayerGestureIntent.Seek ->
                HaloPlayerLiveDragRoute.PLAYER_ACTION

            HaloPlayerGestureIntent.CanonicalUi ->
                HaloPlayerLiveDragRoute.CANONICAL_POINTER

            null ->
                HaloPlayerLiveDragRoute.NONE
        }

    // Kept for compatibility with the D2-16 tests/callers while the handover
    // is in progress.
    fun shouldConsume(intent: HaloPlayerGestureIntent?): Boolean =
        route(intent) != HaloPlayerLiveDragRoute.NONE
}
