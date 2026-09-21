package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloTargetPresenceResolverTest {

    @Test fun unavailableWhenRootHasNoUsableSize() {
        assertEquals(
            HaloTargetPresence.UNAVAILABLE,
            HaloTargetPresenceResolver.resolve(HaloVector(0.5f, 0.5f), 0, 1080),
        )
    }

    @Test fun centreIsAvailable() {
        assertEquals(
            HaloTargetPresence.AVAILABLE,
            HaloTargetPresenceResolver.resolve(HaloVector(0.5f, 0.5f), 1920, 1080),
        )
    }

    @Test fun adaptiveInsetCanRejectUnsafeEdge() {
        assertEquals(
            HaloTargetPresence.OUTSIDE,
            HaloTargetPresenceResolver.resolve(
                position = HaloVector(0.01f, 0.5f),
                widthPx = 1920,
                heightPx = 1080,
                edgeInsetFraction = 0.02f,
            ),
        )
    }

    @Test fun sameFractionWorksAcrossDifferentWindowSizes() {
        val compact = HaloTargetPresenceResolver.resolve(
            HaloVector(0.5f, 0.5f), 600, 900, 0.02f,
        )
        val expanded = HaloTargetPresenceResolver.resolve(
            HaloVector(0.5f, 0.5f), 2560, 1600, 0.02f,
        )
        assertEquals(compact, expanded)
        assertEquals(HaloTargetPresence.AVAILABLE, compact)
    }

    @Test fun excessiveInsetIsSafelyClamped() {
        assertEquals(
            HaloTargetPresence.AVAILABLE,
            HaloTargetPresenceResolver.resolve(
                HaloVector(0.5f, 0.5f), 1000, 1000, 5f,
            ),
        )
    }
}
