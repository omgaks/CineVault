package com.sole.cinevault.glasses.halo

/**
 * D5-5 — canonical player interaction ownership for live playback.
 *
 * This policy is deliberately window/capability based. It does not know device
 * models, resolutions, orientation names, or individual CineVault menu types.
 *
 * Pointer/click belong to canonical Halo when a target surface exists.
 * Playback drags remain with the existing gesture layer. When transient UI is
 * visible, background video tap actions are suppressed so the floating surface
 * keeps interaction priority.
 */
object HaloPlayerInteractionOwnership {

    fun resolve(bridge: HaloPlayerInputBridge): HaloPlayerInteractionOwnershipState =
        HaloPlayerInteractionOwnershipState(
            canonicalPointer = bridge.canonicalPointerEnabled,
            canonicalClick = bridge.canonicalClickEnabled,
            playbackDrags = bridge.externalGestureLayerEnabled,
            backgroundTap = !bridge.transientUiHasPriority,
        )
}

data class HaloPlayerInteractionOwnershipState(
    val canonicalPointer: Boolean,
    val canonicalClick: Boolean,
    val playbackDrags: Boolean,
    val backgroundTap: Boolean,
)
