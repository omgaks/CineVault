package com.sole.cinevault.glasses.halo

/**
 * D5-3 — live player input bridge.
 *
 * Converts the D5 capability policy and D5-2 ownership router into simple
 * booleans/callback gates that PlayerPlaybackGestureLayer can consume without
 * knowing Halo routing details.
 */
class HaloPlayerInputBridge(
    externalDisplayActive: Boolean,
    externalTargetSurfaceAvailable: Boolean,
    transientUiVisible: Boolean,
) {
    val integration: HaloPlayerIntegrationState =
        HaloPlayerIntegrationPolicy.resolve(
            externalDisplayActive = externalDisplayActive,
            externalTargetSurfaceAvailable = externalTargetSurfaceAvailable,
            transientUiVisible = transientUiVisible,
        )

    fun owns(input: HaloPlayerInput, destination: HaloPlayerInputDestination): Boolean =
        HaloPlayerInputRouter.route(integration, input) == destination

    val canonicalPointerEnabled: Boolean
        get() =
            owns(
                HaloPlayerInput.POINTER_MOVE,
                HaloPlayerInputDestination.CANONICAL_HALO,
            )

    val canonicalClickEnabled: Boolean
        get() =
            owns(
                HaloPlayerInput.TARGET_CLICK,
                HaloPlayerInputDestination.CANONICAL_HALO,
            )

    val externalGestureLayerEnabled: Boolean
        get() =
            owns(
                HaloPlayerInput.SEEK_DRAG,
                HaloPlayerInputDestination.EXTERNAL_GESTURE_LAYER,
            )

    val transientUiHasPriority: Boolean
        get() = HaloPlayerInputRouter.shouldPrioritizeTransientUi(integration)
}
