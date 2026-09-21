package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloFocusConfidenceControllerTest {

    private val controller = HaloFocusConfidenceController()

    @Test fun unstableHaloHasNoFocusConfidence() {
        val result = controller.evaluate(
            stability = HaloTargetStability(false, 200L, 0.001f),
            pressed = false,
            dragging = false,
        )
        assertEquals(HaloFocusConfidence.NONE, result)
    }

    @Test fun pressCancelsFocusConfidence() {
        val result = controller.evaluate(
            stability = HaloTargetStability(true, 320L, 0f),
            pressed = true,
            dragging = false,
        )
        assertEquals(HaloFocusConfidence.NONE, result)
    }

    @Test fun dragCancelsFocusConfidence() {
        val result = controller.evaluate(
            stability = HaloTargetStability(true, 320L, 0f),
            pressed = false,
            dragging = true,
        )
        assertEquals(HaloFocusConfidence.NONE, result)
    }

    @Test fun newlyStableHaloStartsSettling() {
        val result = controller.evaluate(
            stability = HaloTargetStability(true, 140L, 0.012f),
            pressed = false,
            dragging = false,
        )
        assertEquals(HaloFocusConfidence.SETTLING, result)
    }

    @Test fun settledAccurateHaloBecomesReady() {
        val result = controller.evaluate(
            stability = HaloTargetStability(true, 220L, 0.004f),
            pressed = false,
            dragging = false,
        )
        assertEquals(HaloFocusConfidence.READY, result)
    }

    @Test fun longAccurateDwellBecomesStrong() {
        val result = controller.evaluate(
            stability = HaloTargetStability(true, 320L, 0f),
            pressed = false,
            dragging = false,
        )
        assertEquals(HaloFocusConfidence.STRONG, result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidThresholdOrderIsRejected() {
        HaloFocusConfidenceConfig(
            readyThreshold = 0.9f,
            strongThreshold = 0.8f,
        )
    }
}
