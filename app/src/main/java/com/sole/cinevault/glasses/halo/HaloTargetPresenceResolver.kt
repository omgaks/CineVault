package com.sole.cinevault.glasses.halo

/**
 * D4-12 — canonical target-presence gate.
 *
 * Converts a normalized Halo position into an adaptive-window target probe.
 * This does not identify or register individual CineVault controls. It only
 * answers whether the pointer is inside the currently usable root surface.
 */
object HaloTargetPresenceResolver {

    fun resolve(
        position: HaloVector,
        widthPx: Int,
        heightPx: Int,
        edgeInsetFraction: Float = 0f,
    ): HaloTargetPresence {
        if (widthPx <= 0 || heightPx <= 0) return HaloTargetPresence.UNAVAILABLE

        val inset = edgeInsetFraction.coerceIn(0f, 0.49f)
        val inside = position.x in inset..(1f - inset) &&
            position.y in inset..(1f - inset)

        return if (inside) HaloTargetPresence.AVAILABLE
        else HaloTargetPresence.OUTSIDE
    }
}

enum class HaloTargetPresence {
    UNAVAILABLE,
    OUTSIDE,
    AVAILABLE,
}
