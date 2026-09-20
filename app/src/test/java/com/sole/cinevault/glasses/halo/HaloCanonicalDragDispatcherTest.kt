package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloCanonicalDragDispatcherTest {

    @Test
    fun dragStartIsReconstructedFromCoordinatorDelta() {
        val event = HaloDragEvent(
            type = HaloDragEventType.DRAG_START,
            position = HaloVector(0.44f, 0.62f),
            delta = HaloVector(0.04f, 0.02f),
        )

        val start = HaloDragStartReconstruction.startOf(event)

        assertEquals(0.40f, start.x, 0.0001f)
        assertEquals(0.60f, start.y, 0.0001f)
    }

    @Test
    fun syntheticGuardIsScopedAndReleased() {
        val guard = HaloSyntheticDispatchGuard()

        assertFalse(guard.isDispatching())
        guard.run {
            assertTrue(guard.isDispatching())
        }
        assertFalse(guard.isDispatching())
    }

    @Test
    fun nestedSyntheticDispatchRemainsGuardedUntilOuterDispatchEnds() {
        val guard = HaloSyntheticDispatchGuard()

        guard.run {
            assertTrue(guard.isDispatching())
            guard.run {
                assertTrue(guard.isDispatching())
            }
            assertTrue(guard.isDispatching())
        }

        assertFalse(guard.isDispatching())
    }

    @Test
    fun reconstructedStartProjectsAcrossCurrentViewport() {
        val event = HaloDragEvent(
            type = HaloDragEventType.DRAG_START,
            position = HaloVector(0.30f, 0.50f),
            delta = HaloVector(0.10f, 0.10f),
        )
        val start = HaloDragStartReconstruction.startOf(event)
        val point = HaloTargetProjection.toPixels(
            position = start,
            widthPx = 2000,
            heightPx = 1000,
        )

        assertEquals(400f, point.xPx, 0.001f)
        assertEquals(400f, point.yPx, 0.001f)
    }
}
