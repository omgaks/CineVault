package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloCoordinateEngineTest {

    private fun sample(x: Float, y: Float) = HaloPointerSample(
        xPx = x * 1000f,
        yPx = y * 500f,
        xFraction = x,
        yFraction = y,
        pressed = true,
    )

    @Test
    fun fullViewportMapsCornersExactly() {
        val viewport = HaloViewport(widthPx = 1920, heightPx = 1080)

        val topLeft = HaloCoordinateEngine.map(sample(0f, 0f), viewport)
        val bottomRight = HaloCoordinateEngine.map(sample(1f, 1f), viewport)

        assertEquals(0f, topLeft.xPx, 0.001f)
        assertEquals(0f, topLeft.yPx, 0.001f)
        assertEquals(1920f, bottomRight.xPx, 0.001f)
        assertEquals(1080f, bottomRight.yPx, 0.001f)
    }

    @Test
    fun centreMapsAcrossDifferentResolutionWithoutHardCoding() {
        val viewport = HaloViewport(widthPx = 3840, heightPx = 2160)
        val point = HaloCoordinateEngine.map(sample(0.5f, 0.5f), viewport)

        assertEquals(1920f, point.xPx, 0.001f)
        assertEquals(1080f, point.yPx, 0.001f)
    }

    @Test
    fun contentBoundsMapIntoVisibleCineVaultArea() {
        val viewport = HaloViewport(
            widthPx = 1920,
            heightPx = 1080,
            contentBounds = HaloContentBounds(
                leftPx = 96f,
                topPx = 54f,
                rightPx = 1824f,
                bottomPx = 1026f,
            ),
        )

        val topLeft = HaloCoordinateEngine.map(sample(0f, 0f), viewport)
        val bottomRight = HaloCoordinateEngine.map(sample(1f, 1f), viewport)

        assertEquals(96f, topLeft.xPx, 0.001f)
        assertEquals(54f, topLeft.yPx, 0.001f)
        assertEquals(1824f, bottomRight.xPx, 0.001f)
        assertEquals(1026f, bottomRight.yPx, 0.001f)
    }

    @Test
    fun portraitViewportUsesSameNormalisedContract() {
        val viewport = HaloViewport(widthPx = 1080, heightPx = 1920)
        val point = HaloCoordinateEngine.map(sample(0.25f, 0.75f), viewport)

        assertEquals(270f, point.xPx, 0.001f)
        assertEquals(1440f, point.yPx, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidContentBoundsOutsideViewportAreRejected() {
        HaloViewport(
            widthPx = 1000,
            heightPx = 500,
            contentBounds = HaloContentBounds(
                leftPx = 0f,
                topPx = 0f,
                rightPx = 1200f,
                bottomPx = 500f,
            ),
        )
    }
}
