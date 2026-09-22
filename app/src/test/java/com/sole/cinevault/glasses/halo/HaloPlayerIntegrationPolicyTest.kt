package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloPlayerIntegrationPolicyTest {

    @Test fun localPlaybackKeepsNormalTouchRoute() {
        val state = HaloPlayerIntegrationPolicy.resolve(
            externalDisplayActive = false,
            externalTargetSurfaceAvailable = false,
            transientUiVisible = false,
        )
        assertEquals(HaloPlayerInteractionRoute.LOCAL_TOUCH, state.route)
        assertFalse(state.haloTargetingEnabled)
    }

    @Test fun externalPlaybackWithoutTargetSurfaceKeepsGesturesButNotTargeting() {
        val state = HaloPlayerIntegrationPolicy.resolve(
            externalDisplayActive = true,
            externalTargetSurfaceAvailable = false,
            transientUiVisible = false,
        )
        assertEquals(HaloPlayerInteractionRoute.EXTERNAL_GESTURES_ONLY, state.route)
        assertFalse(state.haloTargetingEnabled)
    }

    @Test fun externalPlaybackWithTargetSurfaceUsesCanonicalHalo() {
        val state = HaloPlayerIntegrationPolicy.resolve(
            externalDisplayActive = true,
            externalTargetSurfaceAvailable = true,
            transientUiVisible = false,
        )
        assertEquals(HaloPlayerInteractionRoute.CANONICAL_HALO, state.route)
        assertTrue(state.haloTargetingEnabled)
    }

    @Test fun transientWindowRetainsInteractionPriority() {
        val state = HaloPlayerIntegrationPolicy.resolve(
            externalDisplayActive = true,
            externalTargetSurfaceAvailable = true,
            transientUiVisible = true,
        )
        assertTrue(state.haloTargetingEnabled)
        assertTrue(state.transientUiHasPriority)
    }
}
