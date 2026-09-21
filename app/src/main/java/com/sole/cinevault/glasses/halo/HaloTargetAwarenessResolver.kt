package com.sole.cinevault.glasses.halo

/**
 * D4-13 — combines canonical focus confidence with root-surface presence.
 *
 * This is still descriptive. It does not register individual controls, snap the
 * Halo, auto-click, or capture Compose focus.
 */
object HaloTargetAwarenessResolver {
    fun resolve(
        position: HaloVector,
        widthPx: Int,
        heightPx: Int,
        focusConfidence: HaloFocusConfidence,
        edgeInsetFraction: Float = 0f,
    ): HaloTargetAwareness {
        val presence = HaloTargetPresenceResolver.resolve(
            position = position,
            widthPx = widthPx,
            heightPx = heightPx,
            edgeInsetFraction = edgeInsetFraction,
        )

        val confidence = if (presence == HaloTargetPresence.AVAILABLE) {
            focusConfidence
        } else {
            HaloFocusConfidence.NONE
        }

        return HaloTargetAwareness(
            presence = presence,
            focusConfidence = confidence,
            canPresentFocusFeedback =
                presence == HaloTargetPresence.AVAILABLE &&
                    confidence != HaloFocusConfidence.NONE,
        )
    }
}

data class HaloTargetAwareness(
    val presence: HaloTargetPresence,
    val focusConfidence: HaloFocusConfidence,
    val canPresentFocusFeedback: Boolean,
)
