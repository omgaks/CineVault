package com.sole.cinevault.glasses.halo

/**
 * D4-15 — canonical click-delivery policy.
 *
 * A qualified Halo click may be delivered only while the current root surface
 * is available. Focus confidence is intentionally NOT required: confidence is
 * visual precision guidance, not permission to activate a normal target.
 */
object HaloTargetActivationPolicy {

    fun decide(
        awareness: HaloTargetAwareness,
        clickQualified: Boolean,
        dragging: Boolean,
    ): HaloTargetActivationDecision {
        if (!clickQualified) return HaloTargetActivationDecision.IGNORE
        if (dragging) return HaloTargetActivationDecision.BLOCK_DRAG
        return when (awareness.presence) {
            HaloTargetPresence.AVAILABLE -> HaloTargetActivationDecision.DISPATCH
            HaloTargetPresence.OUTSIDE -> HaloTargetActivationDecision.BLOCK_OUTSIDE
            HaloTargetPresence.UNAVAILABLE -> HaloTargetActivationDecision.BLOCK_UNAVAILABLE
        }
    }
}

enum class HaloTargetActivationDecision {
    IGNORE,
    DISPATCH,
    BLOCK_DRAG,
    BLOCK_OUTSIDE,
    BLOCK_UNAVAILABLE,
}
