package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloTargetAwarenessResolverTest {

    @Test fun availableStrongTargetCanPresentFeedback() {
        val result = HaloTargetAwarenessResolver.resolve(
            position = HaloVector(0.5f, 0.5f),
            widthPx = 1920,
            heightPx = 1080,
            focusConfidence = HaloFocusConfidence.STRONG,
        )

        assertEquals(HaloTargetPresence.AVAILABLE, result.presence)
        assertEquals(HaloFocusConfidence.STRONG, result.focusConfidence)
        assertTrue(result.canPresentFocusFeedback)
    }

    @Test fun outsideTargetSuppressesConfidence() {
        val result = HaloTargetAwarenessResolver.resolve(
            position = HaloVector(0.01f, 0.5f),
            widthPx = 1920,
            heightPx = 1080,
            focusConfidence = HaloFocusConfidence.STRONG,
            edgeInsetFraction = 0.02f,
        )

        assertEquals(HaloTargetPresence.OUTSIDE, result.presence)
        assertEquals(HaloFocusConfidence.NONE, result.focusConfidence)
        assertFalse(result.canPresentFocusFeedback)
    }

    @Test fun unavailableRootSuppressesConfidence() {
        val result = HaloTargetAwarenessResolver.resolve(
            position = HaloVector(0.5f, 0.5f),
            widthPx = 0,
            heightPx = 1080,
            focusConfidence = HaloFocusConfidence.READY,
        )

        assertEquals(HaloTargetPresence.UNAVAILABLE, result.presence)
        assertEquals(HaloFocusConfidence.NONE, result.focusConfidence)
        assertFalse(result.canPresentFocusFeedback)
    }

    @Test fun availableButNoConfidenceDoesNotPresentFeedback() {
        val result = HaloTargetAwarenessResolver.resolve(
            position = HaloVector(0.5f, 0.5f),
            widthPx = 800,
            heightPx = 600,
            focusConfidence = HaloFocusConfidence.NONE,
        )

        assertEquals(HaloTargetPresence.AVAILABLE, result.presence)
        assertFalse(result.canPresentFocusFeedback)
    }
}
