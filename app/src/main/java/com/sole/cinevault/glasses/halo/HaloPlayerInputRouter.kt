package com.sole.cinevault.glasses.halo

/**
 * D5-2 — canonical routing contract for live player input.
 *
 * This is the bridge between D5-1's capability decision and the existing
 * PlayerPlaybackGestureLayer. It prevents two pointer/click systems from
 * owning the same external-display interaction at the same time.
 */
object HaloPlayerInputRouter {

    fun route(
        integration: HaloPlayerIntegrationState,
        input: HaloPlayerInput,
    ): HaloPlayerInputDestination {
        return when (integration.route) {
            HaloPlayerInteractionRoute.LOCAL_TOUCH ->
                HaloPlayerInputDestination.LOCAL_PLAYER

            HaloPlayerInteractionRoute.EXTERNAL_GESTURES_ONLY ->
                when (input) {
                    HaloPlayerInput.POINTER_MOVE,
                    HaloPlayerInput.TARGET_CLICK ->
                        HaloPlayerInputDestination.NONE

                    HaloPlayerInput.BRIGHTNESS_DRAG,
                    HaloPlayerInput.VOLUME_DRAG,
                    HaloPlayerInput.SEEK_DRAG,
                    HaloPlayerInput.DOUBLE_TAP,
                    HaloPlayerInput.PINCH_PAN,
                    HaloPlayerInput.EMERGENCY_RETURN ->
                        HaloPlayerInputDestination.EXTERNAL_GESTURE_LAYER
                }

            HaloPlayerInteractionRoute.CANONICAL_HALO ->
                when (input) {
                    HaloPlayerInput.POINTER_MOVE,
                    HaloPlayerInput.TARGET_CLICK ->
                        HaloPlayerInputDestination.CANONICAL_HALO

                    HaloPlayerInput.BRIGHTNESS_DRAG,
                    HaloPlayerInput.VOLUME_DRAG,
                    HaloPlayerInput.SEEK_DRAG,
                    HaloPlayerInput.DOUBLE_TAP,
                    HaloPlayerInput.PINCH_PAN,
                    HaloPlayerInput.EMERGENCY_RETURN ->
                        HaloPlayerInputDestination.EXTERNAL_GESTURE_LAYER
                }
        }
    }

    fun shouldPrioritizeTransientUi(
        integration: HaloPlayerIntegrationState,
    ): Boolean =
        integration.route == HaloPlayerInteractionRoute.CANONICAL_HALO &&
            integration.haloTargetingEnabled &&
            integration.transientUiHasPriority
}

enum class HaloPlayerInput {
    POINTER_MOVE,
    TARGET_CLICK,
    BRIGHTNESS_DRAG,
    VOLUME_DRAG,
    SEEK_DRAG,
    DOUBLE_TAP,
    PINCH_PAN,
    EMERGENCY_RETURN,
}

enum class HaloPlayerInputDestination {
    LOCAL_PLAYER,
    EXTERNAL_GESTURE_LAYER,
    CANONICAL_HALO,
    NONE,
}
