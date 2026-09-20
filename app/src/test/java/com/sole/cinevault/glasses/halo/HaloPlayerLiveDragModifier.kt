package com.sole.cinevault.glasses.halo

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * D2-16 — first live input cutover.
 *
 * Owns ONLY the three drag lanes that D2-11 defined:
 *   top 30%   -> seek
 *   left 20%  -> brightness
 *   right 20% -> volume
 *
 * The centre remains untouched for the canonical CineVault/Halo pointer UI.
 *
 * IMPORTANT:
 * This modifier is intended to REPLACE the old single-finger drag ownership
 * inside glassesTouchpadGestures, not to be stacked beside that old drag
 * detector. Tap/double-tap, pinch and five-finger emergency remain separate
 * until their later migration slices.
 */
fun Modifier.haloPlayerLiveDragGestures(
    gestureKey: Any?,
    playbackPositionMs: () -> Long,
    durationMs: () -> Long,
    liveSession: HaloPlayerLiveSession,
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
            onDrag = { change, _ ->
                val intent =
                    liveSession.onDrag(
                        xPx = change.position.x,
                        yPx = change.position.y,
                        widthPx = size.width,
                        heightPx = size.height,
                    )

                // Consume only when this gesture belongs to one of our actual
                // player-action lanes. Centre remains available to canonical UI.
                if (HaloPlayerLiveDragPolicy.shouldConsume(intent)) {
                    change.consume()
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

object HaloPlayerLiveDragPolicy {
    fun shouldConsume(intent: HaloPlayerGestureIntent?): Boolean =
        when (intent) {
            is HaloPlayerGestureIntent.Brightness,
            is HaloPlayerGestureIntent.Volume,
            is HaloPlayerGestureIntent.Seek -> true

            HaloPlayerGestureIntent.CanonicalUi,
            null -> false
        }
}
