package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloPlayerInputBridgeTest {

    @Test fun canonicalSurfaceOwnsPointerAndClickButKeepsGestureLayer() {
        val bridge = HaloPlayerInputBridge(
            externalDisplayActive = true,
            externalTargetSurfaceAvailable = true,
            transientUiVisible = false,
        )
        assertTrue(bridge.canonicalPointerEnabled)
        assertTrue(bridge.canonicalClickEnabled)
        assertTrue(bridge.externalGestureLayerEnabled)
    }

    @Test fun missingTargetSurfaceDisablesCanonicalPointerAndClick() {
        val bridge = HaloPlayerInputBridge(
            externalDisplayActive = true,
            externalTargetSurfaceAvailable = false,
            transientUiVisible = false,
        )
        assertFalse(bridge.canonicalPointerEnabled)
        assertFalse(bridge.canonicalClickEnabled)
        assertTrue(bridge.externalGestureLayerEnabled)
    }

    @Test fun localPlaybackLeavesHaloAndExternalLayerDisabled() {
        val bridge = HaloPlayerInputBridge(
            externalDisplayActive = false,
            externalTargetSurfaceAvailable = false,
            transientUiVisible = false,
        )
        assertFalse(bridge.canonicalPointerEnabled)
        assertFalse(bridge.canonicalClickEnabled)
        assertFalse(bridge.externalGestureLayerEnabled)
    }

    @Test fun transientUiPriorityPropagatesOnlyForCanonicalHalo() {
        val canonical = HaloPlayerInputBridge(true, true, true)
        val fallback = HaloPlayerInputBridge(true, false, true)
        assertTrue(canonical.transientUiHasPriority)
        assertFalse(fallback.transientUiHasPriority)
    }
}
