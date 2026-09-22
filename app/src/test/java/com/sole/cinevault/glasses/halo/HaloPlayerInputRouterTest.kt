package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloPlayerInputRouterTest {

    @Test fun localRouteLeavesEveryInputWithLocalPlayer() {
        val state = HaloPlayerIntegrationPolicy.resolve(false, false, false)
        HaloPlayerInput.entries.forEach { input ->
            assertEquals(
                HaloPlayerInputDestination.LOCAL_PLAYER,
                HaloPlayerInputRouter.route(state, input),
            )
        }
    }

    @Test fun gesturesOnlyRouteNeverCreatesSecondPointerOwner() {
        val state = HaloPlayerIntegrationPolicy.resolve(true, false, false)
        assertEquals(
            HaloPlayerInputDestination.NONE,
            HaloPlayerInputRouter.route(state, HaloPlayerInput.POINTER_MOVE),
        )
        assertEquals(
            HaloPlayerInputDestination.NONE,
            HaloPlayerInputRouter.route(state, HaloPlayerInput.TARGET_CLICK),
        )
        assertEquals(
            HaloPlayerInputDestination.EXTERNAL_GESTURE_LAYER,
            HaloPlayerInputRouter.route(state, HaloPlayerInput.SEEK_DRAG),
        )
    }

    @Test fun canonicalRouteOwnsPointerAndClickOnly() {
        val state = HaloPlayerIntegrationPolicy.resolve(true, true, false)
        assertEquals(
            HaloPlayerInputDestination.CANONICAL_HALO,
            HaloPlayerInputRouter.route(state, HaloPlayerInput.POINTER_MOVE),
        )
        assertEquals(
            HaloPlayerInputDestination.CANONICAL_HALO,
            HaloPlayerInputRouter.route(state, HaloPlayerInput.TARGET_CLICK),
        )
        assertEquals(
            HaloPlayerInputDestination.EXTERNAL_GESTURE_LAYER,
            HaloPlayerInputRouter.route(state, HaloPlayerInput.BRIGHTNESS_DRAG),
        )
        assertEquals(
            HaloPlayerInputDestination.EXTERNAL_GESTURE_LAYER,
            HaloPlayerInputRouter.route(state, HaloPlayerInput.VOLUME_DRAG),
        )
        assertEquals(
            HaloPlayerInputDestination.EXTERNAL_GESTURE_LAYER,
            HaloPlayerInputRouter.route(state, HaloPlayerInput.SEEK_DRAG),
        )
    }

    @Test fun transientUiPriorityRequiresLiveCanonicalHalo() {
        assertTrue(
            HaloPlayerInputRouter.shouldPrioritizeTransientUi(
                HaloPlayerIntegrationPolicy.resolve(true, true, true)
            )
        )
        assertFalse(
            HaloPlayerInputRouter.shouldPrioritizeTransientUi(
                HaloPlayerIntegrationPolicy.resolve(true, false, true)
            )
        )
        assertFalse(
            HaloPlayerInputRouter.shouldPrioritizeTransientUi(
                HaloPlayerIntegrationPolicy.resolve(false, false, true)
            )
        )
    }
}
