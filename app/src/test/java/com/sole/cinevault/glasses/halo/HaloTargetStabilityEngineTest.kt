package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloTargetStabilityEngineTest {

    @Test fun firstSampleStartsDwellButIsNotImmediatelyStable() {
        val engine = HaloTargetStabilityEngine()
        val result = engine.update(HaloVector(0.5f, 0.5f), false, 1000L)
        assertFalse(result.isStable)
        assertEquals(0L, result.dwellMillis)
    }

    @Test fun pointerBecomesStableAfterShortDwellInsideRadius() {
        val engine = HaloTargetStabilityEngine()
        engine.update(HaloVector(0.5f, 0.5f), false, 1000L)
        val result = engine.update(HaloVector(0.505f, 0.503f), false, 1150L)
        assertTrue(result.isStable)
        assertEquals(150L, result.dwellMillis)
    }

    @Test fun deliberateTravelResetsDwellAnchor() {
        val engine = HaloTargetStabilityEngine()
        engine.update(HaloVector(0.2f, 0.2f), false, 1000L)
        engine.update(HaloVector(0.205f, 0.2f), false, 1150L)

        val moved = engine.update(HaloVector(0.5f, 0.5f), false, 1160L)
        assertFalse(moved.isStable)
        assertEquals(0L, moved.dwellMillis)
    }

    @Test fun pressCancelsStabilitySoClickAndDragRemainAuthoritative() {
        val engine = HaloTargetStabilityEngine()
        engine.update(HaloVector(0.5f, 0.5f), false, 1000L)
        assertTrue(engine.update(HaloVector(0.5f, 0.5f), false, 1150L).isStable)

        val pressed = engine.update(HaloVector(0.5f, 0.5f), true, 1160L)
        assertFalse(pressed.isStable)
        assertEquals(0L, pressed.dwellMillis)
    }

    @Test fun resetRequiresFreshDwell() {
        val engine = HaloTargetStabilityEngine()
        engine.update(HaloVector(0.5f, 0.5f), false, 1000L)
        assertTrue(engine.update(HaloVector(0.5f, 0.5f), false, 1150L).isStable)

        engine.reset(HaloVector(0.5f, 0.5f))
        val afterReset = engine.update(HaloVector(0.5f, 0.5f), false, 1160L)
        assertFalse(afterReset.isStable)
    }

    @Test(expected = IllegalArgumentException::class)
    fun excessiveStabilityRadiusIsRejected() {
        HaloTargetStabilityConfig(stabilityRadiusFraction = 0.11f)
    }
}
