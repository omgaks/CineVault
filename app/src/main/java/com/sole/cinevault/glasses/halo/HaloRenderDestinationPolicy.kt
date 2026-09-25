package com.sole.cinevault.glasses.halo

import com.sole.cinevault.glasses.display.CineVaultRenderDestination

/**
 * R1: Halo is an external-display cursor only.
 *
 * Host phone/tablet surfaces must never render or own Halo. They may publish
 * Cinema Void remote samples while an external display is active, but the
 * cursor/target receiver belongs exclusively to EXTERNAL_DISPLAY.
 */
internal object HaloRenderDestinationPolicy {
    fun shouldOwnHalo(destination: CineVaultRenderDestination): Boolean =
        destination == CineVaultRenderDestination.EXTERNAL_DISPLAY
}
