package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloDragControllerTest {

    @Test
    fun smallMovementDoesNotStealPossibleClick() {
        val controller = HaloDragController()

        controller.update(HaloVector(0.50f, 0.50f), pressed = true)
        val events = controller.update(
            HaloVector(0.505f, 0.505f),
            pressed = true,
        )

        assertTrue(events.isEmpty())
        assertFalse(controller.isDragging())
    }

    @Test
    fun thresholdCrossingStartsDrag() {
        val controller = HaloDragController()

        controller.update(HaloVector(0.20f, 0.20f), true)
        val events = controller.update(HaloVector(0.24f, 0.20f), true)

        assertEquals(
            listOf(HaloDragEventType.DRAG_START),
            events.map { it.type },
        )
        assertTrue(controller.isDragging())
    }

    @Test
    fun ownedDragEmitsIncrementalDelta() {
        val controller = HaloDragController()

        controller.update(HaloVector(0.20f, 0.20f), true)
        controller.update(HaloVector(0.24f, 0.20f), true)
        val events = controller.update(HaloVector(0.27f, 0.23f), true)

        val drag = events.single()
        assertEquals(HaloDragEventType.DRAG, drag.type)
        assertEquals(0.03f, drag.delta.x, 0.0001f)
        assertEquals(0.03f, drag.delta.y, 0.0001f)
    }

    @Test
    fun releaseEndsOwnedDrag() {
        val controller = HaloDragController()

        controller.update(HaloVector(0.20f, 0.20f), true)
        controller.update(HaloVector(0.24f, 0.20f), true)
        val events = controller.update(HaloVector(0.24f, 0.20f), false)

        assertEquals(
            listOf(HaloDragEventType.DRAG_END),
            events.map { it.type },
        )
        assertFalse(controller.isDragging())
    }

    @Test
    fun releaseWithoutDragEmitsNothing() {
        val controller = HaloDragController()

        controller.update(HaloVector(0.50f, 0.50f), true)
        val events = controller.update(HaloVector(0.50f, 0.50f), false)

        assertTrue(events.isEmpty())
    }

    @Test
    fun cancelReportsOnlyAnOwnedDrag() {
        val controller = HaloDragController()

        controller.update(HaloVector(0.10f, 0.10f), true)
        assertNull(controller.cancel())

        controller.update(HaloVector(0.10f, 0.10f), true)
        controller.update(HaloVector(0.20f, 0.10f), true)

        assertEquals(
            HaloDragEventType.DRAG_CANCEL,
            controller.cancel()?.type,
        )
        assertFalse(controller.isDragging())
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDragThresholdIsRejected() {
        HaloDragConfig(dragStartTravelFraction = 0f)
    }
}
