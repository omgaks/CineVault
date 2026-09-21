package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloClickPulseControllerTest {
    @Test fun pulseIsInactiveBeforeClick() {
        val controller = HaloClickPulseController()
        assertFalse(controller.isActive(1000L))
    }

    @Test fun clickStartsPulseImmediately() {
        val controller = HaloClickPulseController()
        controller.trigger(1000L)
        assertTrue(controller.isActive(1000L))
        assertEquals(1f, controller.progress(1000L), 0.0001f)
    }

    @Test fun pulseDecaysAndEnds() {
        val controller = HaloClickPulseController(durationMillis = 200L)
        controller.trigger(1000L)
        assertEquals(0.5f, controller.progress(1100L), 0.0001f)
        assertEquals(0f, controller.progress(1200L), 0.0001f)
        assertFalse(controller.isActive(1201L))
    }

    @Test fun retriggerRestartsPulse() {
        val controller = HaloClickPulseController(durationMillis = 200L)
        controller.trigger(1000L)
        controller.progress(1100L)
        controller.trigger(1150L)
        assertEquals(1f, controller.progress(1150L), 0.0001f)
    }

    @Test fun resetStopsPulse() {
        val controller = HaloClickPulseController()
        controller.trigger(1000L)
        controller.reset()
        assertFalse(controller.isActive(1001L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDurationIsRejected() {
        HaloClickPulseController(durationMillis = 0L)
    }
}
