package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloTargetActivationPolicyTest {

    private fun awareness(
        presence: HaloTargetPresence,
        confidence: HaloFocusConfidence = HaloFocusConfidence.NONE,
    ) = HaloTargetAwareness(
        presence = presence,
        focusConfidence = confidence,
        canPresentFocusFeedback =
            presence == HaloTargetPresence.AVAILABLE &&
                confidence != HaloFocusConfidence.NONE,
    )

    @Test fun qualifiedClickDispatchesOnAvailableSurfaceWithoutConfidence() {
        assertEquals(
            HaloTargetActivationDecision.DISPATCH,
            HaloTargetActivationPolicy.decide(
                awareness(HaloTargetPresence.AVAILABLE),
                clickQualified = true,
                dragging = false,
            ),
        )
    }

    @Test fun strongConfidenceDoesNotOverrideOutsideSurface() {
        assertEquals(
            HaloTargetActivationDecision.BLOCK_OUTSIDE,
            HaloTargetActivationPolicy.decide(
                awareness(HaloTargetPresence.OUTSIDE, HaloFocusConfidence.STRONG),
                clickQualified = true,
                dragging = false,
            ),
        )
    }

    @Test fun unavailableRootBlocksDelivery() {
        assertEquals(
            HaloTargetActivationDecision.BLOCK_UNAVAILABLE,
            HaloTargetActivationPolicy.decide(
                awareness(HaloTargetPresence.UNAVAILABLE),
                clickQualified = true,
                dragging = false,
            ),
        )
    }

    @Test fun dragAlwaysBlocksClickDelivery() {
        assertEquals(
            HaloTargetActivationDecision.BLOCK_DRAG,
            HaloTargetActivationPolicy.decide(
                awareness(HaloTargetPresence.AVAILABLE, HaloFocusConfidence.STRONG),
                clickQualified = true,
                dragging = true,
            ),
        )
    }

    @Test fun nonClickIsIgnored() {
        assertEquals(
            HaloTargetActivationDecision.IGNORE,
            HaloTargetActivationPolicy.decide(
                awareness(HaloTargetPresence.AVAILABLE),
                clickQualified = false,
                dragging = false,
            ),
        )
    }
}
