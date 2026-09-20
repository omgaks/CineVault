package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloCanonicalTargetDispatcherTest {

    @Test
    fun centreProjectsToCentreOfCurrentRoot() {
        val point = HaloTargetProjection.toPixels(
            position = HaloVector(0.5f, 0.5f),
            widthPx = 1920,
            heightPx = 1080,
        )

        assertEquals(960f, point.xPx, 0.001f)
        assertEquals(540f, point.yPx, 0.001f)
    }

    @Test
    fun projectionAdaptsToPortraitWithoutDeviceConstants() {
        val point = HaloTargetProjection.toPixels(
            position = HaloVector(0.25f, 0.75f),
            widthPx = 1080,
            heightPx = 1920,
        )

        assertEquals(270f, point.xPx, 0.001f)
        assertEquals(1440f, point.yPx, 0.001f)
    }

    @Test
    fun outOfRangeHaloPositionIsClampedToRoot() {
        val point = HaloTargetProjection.toPixels(
            position = HaloVector(-0.2f, 1.4f),
            widthPx = 1000,
            heightPx = 500,
        )

        assertEquals(0f, point.xPx, 0.001f)
        assertEquals(500f, point.yPx, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroWidthIsRejected() {
        HaloTargetProjection.toPixels(
            position = HaloVector(0.5f, 0.5f),
            widthPx = 0,
            heightPx = 500,
        )
    }
}
