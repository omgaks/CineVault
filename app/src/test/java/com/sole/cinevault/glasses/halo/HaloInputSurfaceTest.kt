package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloInputSurfaceTest {

    @Test
    fun topLeftMapsToZeroZero() {
        val sample = HaloPointerMapper.sample(
            xPx = 0f,
            yPx = 0f,
            widthPx = 1000,
            heightPx = 500,
            pressed = true,
        )

        assertEquals(0f, sample.xFraction, 0.0001f)
        assertEquals(0f, sample.yFraction, 0.0001f)
    }

    @Test
    fun bottomRightMapsToOneOne() {
        val sample = HaloPointerMapper.sample(
            xPx = 1000f,
            yPx = 500f,
            widthPx = 1000,
            heightPx = 500,
            pressed = true,
        )

        assertEquals(1f, sample.xFraction, 0.0001f)
        assertEquals(1f, sample.yFraction, 0.0001f)
    }

    @Test
    fun centreMapsToHalfHalf() {
        val sample = HaloPointerMapper.sample(
            xPx = 500f,
            yPx = 250f,
            widthPx = 1000,
            heightPx = 500,
            pressed = false,
        )

        assertEquals(0.5f, sample.xFraction, 0.0001f)
        assertEquals(0.5f, sample.yFraction, 0.0001f)
    }

    @Test
    fun outOfBoundsInputIsClampedToFullSurface() {
        val sample = HaloPointerMapper.sample(
            xPx = 1300f,
            yPx = -50f,
            widthPx = 1000,
            heightPx = 500,
            pressed = true,
        )

        assertEquals(1f, sample.xFraction, 0.0001f)
        assertEquals(0f, sample.yFraction, 0.0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroWidthIsRejected() {
        HaloPointerMapper.sample(
            xPx = 0f,
            yPx = 0f,
            widthPx = 0,
            heightPx = 500,
            pressed = false,
        )
    }
}
