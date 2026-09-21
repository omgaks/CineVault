package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloClickControllerTest {

    @Test
    fun shortStationaryTouchProducesPressClickRelease() {
        val controller = HaloClickController()
        val p = HaloVector(0.5f, 0.5f)

        val down = controller.update(p, true, 1000L)
        val up = controller.update(p, false, 1100L)

        assertTrue(down.any { it.type == HaloClickEventType.PRESS })
        assertTrue(up.any { it.type == HaloClickEventType.CLICK })
        assertTrue(up.any { it.type == HaloClickEventType.RELEASE })
    }

    @Test
    fun longHoldDoesNotAccidentallyClick() {
        val controller = HaloClickController()
        val p = HaloVector(0.4f, 0.4f)

        controller.update(p, true, 1000L)
        val up = controller.update(p, false, 1400L)

        assertFalse(up.any { it.type == HaloClickEventType.CLICK })
        assertTrue(up.any { it.type == HaloClickEventType.RELEASE })
    }

    @Test
    fun standardTapAllowsExistingTravelEnvelope() {
        val controller = HaloClickController()
        controller.update(HaloVector(0.5f, 0.5f), true, 1000L)
        val up = controller.update(HaloVector(0.52f, 0.5f), false, 1100L)

        assertTrue(up.any { it.type == HaloClickEventType.CLICK })
    }

    @Test
    fun precisionTapUsesTighterTravelEnvelope() {
        val controller = HaloClickController()
        controller.update(
            position = HaloVector(0.5f, 0.5f),
            pressed = true,
            eventTimeMillis = 1000L,
            precisionStable = true,
        )
        val up = controller.update(
            position = HaloVector(0.52f, 0.5f),
            pressed = false,
            eventTimeMillis = 1100L,
            precisionStable = false,
        )

        assertFalse(up.any { it.type == HaloClickEventType.CLICK })
        assertTrue(up.any { it.type == HaloClickEventType.RELEASE })
    }

    @Test
    fun precisionStateIsCapturedAtPressDown() {
        val controller = HaloClickController()
        controller.update(
            position = HaloVector(0.5f, 0.5f),
            pressed = true,
            eventTimeMillis = 1000L,
            precisionStable = true,
        )
        val up = controller.update(
            position = HaloVector(0.51f, 0.5f),
            pressed = false,
            eventTimeMillis = 1100L,
            precisionStable = false,
        )

        assertTrue(up.any { it.type == HaloClickEventType.CLICK })
    }

    @Test
    fun cancelClearsPendingPrecisionTap() {
        val controller = HaloClickController()
        controller.update(
            position = HaloVector(0.5f, 0.5f),
            pressed = true,
            eventTimeMillis = 1000L,
            precisionStable = true,
        )
        controller.cancel()

        val up = controller.update(
            position = HaloVector(0.5f, 0.5f),
            pressed = false,
            eventTimeMillis = 1100L,
        )
        assertFalse(up.any { it.type == HaloClickEventType.CLICK })
    }

    @Test(expected = IllegalArgumentException::class)
    fun precisionTravelCannotExceedStandardTravel() {
        HaloClickConfig(
            maxTapTravelFraction = 0.02f,
            precisionTapTravelFraction = 0.03f,
        )
    }
}
