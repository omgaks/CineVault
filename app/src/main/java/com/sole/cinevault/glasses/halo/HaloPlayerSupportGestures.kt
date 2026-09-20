package com.sole.cinevault.glasses.halo

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * D2-20 — temporary non-drag support surface used during the live Halo cutover.
 *
 * Single-finger drag ownership is NOT here; D2 live drag owns it.
 *
 * Tap contract:
 *  - controls hidden -> show controls
 *  - controls visible + Halo over actionable UI -> click that UI
 *  - controls visible + Halo over empty UI -> hide controls
 *  - double tap -> play/pause
 *
 * Long press is deliberately absent.
 *
 * Multi-finger behavior retained while it migrates:
 *  - two-finger pinch/pan -> external viewport transform
 *  - five-finger spread -> emergency return to tablet
 */
fun Modifier.haloPlayerSupportGestures(
    gestureKey: Any?,
    view: View,
    controlsVisible: () -> Boolean,
    clickHaloTarget: () -> Boolean,
    onShowControls: () -> Unit,
    onHideControls: () -> Unit,
    onDoubleTap: () -> Unit,
    onTouchPulse: () -> Unit,
    onPinchZoomPan: (Float, Offset) -> Unit,
    onEmergencyReturnToTablet: () -> Unit,
): Modifier =
    this
        .pointerInput(gestureKey) {
            detectTapGestures(
                onTap = {
                    onTouchPulse()
                    when (
                        HaloPlayerTapPolicy.route(
                            controlsVisible = controlsVisible(),
                            targetClicked =
                                if (controlsVisible()) clickHaloTarget()
                                else false,
                        )
                    ) {
                        HaloPlayerTapRoute.SHOW_CONTROLS -> onShowControls()
                        HaloPlayerTapRoute.HIDE_CONTROLS -> onHideControls()
                        HaloPlayerTapRoute.CLICK_TARGET -> Unit
                    }
                },
                onDoubleTap = {
                    onDoubleTap()
                },
            )
        }
        .pointerInput(gestureKey) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)

                var fiveFingerSpread = 1f
                var emergencyTriggered = false

                do {
                    val event = awaitPointerEvent()
                    val pressedCount = event.changes.count { change ->
                        change.pressed
                    }

                    if (pressedCount >= 5) {
                        fiveFingerSpread *= event.calculateZoom()

                        event.changes.forEach { change ->
                            if (change.positionChanged()) {
                                change.consume()
                            }
                        }

                        if (
                            !emergencyTriggered &&
                            fiveFingerSpread >=
                                HaloPlayerSupportPolicy.EMERGENCY_SPREAD_SCALE
                        ) {
                            emergencyTriggered = true
                            view.performHapticFeedback(
                                HapticFeedbackConstants.CONTEXT_CLICK
                            )
                            onEmergencyReturnToTablet()
                        }
                    } else if (
                        event.changes.size >= 2 &&
                        !emergencyTriggered
                    ) {
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()

                        if (zoom != 1f || pan != Offset.Zero) {
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                            onPinchZoomPan(zoom, pan)
                        }
                    }
                } while (
                    event.changes.any { change -> change.pressed }
                )
            }
        }

enum class HaloPlayerTapRoute {
    SHOW_CONTROLS,
    HIDE_CONTROLS,
    CLICK_TARGET,
}

object HaloPlayerTapPolicy {
    fun route(
        controlsVisible: Boolean,
        targetClicked: Boolean,
    ): HaloPlayerTapRoute =
        when {
            !controlsVisible -> HaloPlayerTapRoute.SHOW_CONTROLS
            targetClicked -> HaloPlayerTapRoute.CLICK_TARGET
            else -> HaloPlayerTapRoute.HIDE_CONTROLS
        }
}

object HaloPlayerSupportPolicy {
    const val EMERGENCY_SPREAD_SCALE = 1.35f
}
