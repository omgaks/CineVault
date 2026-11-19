package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloPadScrollTest {
    @Test fun fingerTravelBecomesWheelUnitsOfTheGlassesScreen() {
        // Full pad height on a 400dp-tall glasses screen: 400 * 1.6 / 64 = 10 wheel units.
        assertEquals(10f, wheelUnits(1f, 400f), 0.0001f)
        assertEquals(5f, wheelUnits(0.5f, 400f), 0.0001f)
    }

    @Test fun signFollowsTheFinger() {
        assertTrue(wheelUnits(-0.1f, 400f) < 0f)
        assertTrue(wheelUnits(0.1f, 400f) > 0f)
        assertEquals(0f, wheelUnits(0f, 400f), 0f)
    }

    @Test fun pointerOnlySampleNeverStartsADrag() {
        val c = HaloInteractionCoordinator()
        var drags = 0
        var t = 0L
        fun step(x: Float, pressed: Boolean) {
            t += 16
            drags += c.update(
                HaloPointerSample(x * 100, 50f, x, 0.5f, pressed, moveOnly = true), t, 16,
            ).dragEvents.size
        }
        step(0.1f, true); (1..20).forEach { step(0.1f + it * 0.03f, true) }; step(0.7f, false)
        assertEquals(0, drags)
    }
}
