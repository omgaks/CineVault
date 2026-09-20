package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloClickControllerTest {

    @Test
    fun shortStationaryTouchProducesPressClickRelease() {
        val controller = HaloClickController()
        val p = HaloVector(0.5f, 0.5f)

        val down = controller.update(p, pressed = true, eventTimeMillis = 1000L)
        val up = controller.update(p, pressed = false, eventTimeMillis = 1100L)

        assertEquals(listOf(HaloClickEventType.PRESS), down.map { it.type })
        assertEquals(
            listOf(HaloClickEventType.CLICK, HaloClickEventType.RELEASE),
            up.map { it.type },
        )
        assertTrue(up.first().feedback.visualPulse)
        assertEquals(HaloHapticRequest.CLICK, up.first().feedback.haptic)
    }

    @Test
    fun longHoldDoesNotAccidentallyClick() {
        val controller = HaloClickController()
        val p = HaloVector(0.4f, 0.4f)

        controller.update(p, true, 1000L)
        val up = controller.update(p, false, 1500L)

        assertEquals(
            listOf(HaloClickEventType.RELEASE),
            up.map { it.type },
        )
    }

    @Test
    fun largeMovementDoesNotAccidentallyClick() {
        val controller = HaloClickController()

        controller.update(HaloVector(0.2f, 0.2f), true, 1000L)
        val up = controller.update(HaloVector(0.4f, 0.4f), false, 1100L)

        assertFalse(up.any { it.type == HaloClickEventType.CLICK })
    }

    @Test
    fun cancelClearsPendingPress() {
        val controller = HaloClickController()
        val p = HaloVector(0.5f, 0.5f)

        controller.update(p, true, 1000L)
        controller.cancel()
        val up = controller.update(p, false, 1100L)

        assertTrue(up.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidTapDurationIsRejected() {
        HaloClickConfig(maxTapDurationMillis = 0L)
    }
}
