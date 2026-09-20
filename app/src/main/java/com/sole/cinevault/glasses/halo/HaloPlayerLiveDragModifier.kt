package com.sole.cinevault.glasses.halo

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * D2-22 — live-drag stabilization checkpoint.
 *
 * Single-finger external-player ownership:
 *  - top 50% horizontal -> seek
 *  - left 20% vertical -> brightness
 *  - right 20% vertical -> volume
 *  - otherwise -> canonical CineVault Halo pointer
 *
 * Gesture classification is direction-aware in HaloPlayerGestureRouter.
 * Tap/double-tap, pinch and emergency return remain in
 * HaloPlayerSupportGestures. Long press is intentionally absent.
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

    fun shouldConsume(intent: HaloPlayerGestureIntent?): Boolean =
        route(intent) != HaloPlayerLiveDragRoute.NONE
}
