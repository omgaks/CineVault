package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloSpeedHoldPolicyTest {
    @Test fun fingerCountsMapToSpeeds() {
        assertNull(HaloSpeedHoldPolicy.speedFor(1))
        assertEquals(2f, HaloSpeedHoldPolicy.speedFor(2)!!, 0f)
        assertEquals(3f, HaloSpeedHoldPolicy.speedFor(3)!!, 0f)
        assertEquals(5f, HaloSpeedHoldPolicy.speedFor(4)!!, 0f)
    }

    @Test fun fiveFingersAreReservedForReturnToTablet() {
        assertNull(HaloSpeedHoldPolicy.speedFor(5))
        assertNull(HaloSpeedHoldPolicy.speedFor(6))
    }

    @Test fun smallJitterStillCountsAsHolding() {
        assertTrue(HaloSpeedHoldPolicy.isStill(travelPx = 6f, sizeRatio = 1.02f))
    }

    @Test fun pinchOrDragIsNotAHold() {
        assertFalse(HaloSpeedHoldPolicy.isStill(travelPx = 60f, sizeRatio = 1f))
        assertFalse(HaloSpeedHoldPolicy.isStill(travelPx = 0f, sizeRatio = 1.3f))
        assertFalse(HaloSpeedHoldPolicy.isStill(travelPx = 0f, sizeRatio = 0.7f))
    }

    @Test fun labelsAreReadable() {
        assertEquals("2x", HaloSpeedHoldPolicy.label(2f))
        assertEquals("5x", HaloSpeedHoldPolicy.label(5f))
    }
}
