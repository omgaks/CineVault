package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloOrientationMapperTest {

    private fun sample(x: Float, y: Float) = HaloPointerSample(
        xPx = x * 1000f,
        yPx = y * 1000f,
        xFraction = x,
        yFraction = y,
        pressed = true,
    )

    @Test
    fun sameLandscapeOrientationPreservesMapping() {
        val point = HaloOrientationMapper.map(
            sample = sample(0.25f, 0.75f),
            sourceWidthPx = 1600,
            sourceHeightPx = 900,
            targetViewport = HaloViewport(1920, 1080),
        )

        assertEquals(480f, point.xPx, 0.001f)
        assertEquals(810f, point.yPx, 0.001f)
    }

    @Test
    fun portraitTouchSurfaceRotatesIntoLandscapeDisplay() {
        val point = HaloOrientationMapper.map(
            sample = sample(0.25f, 0.75f),
            sourceWidthPx = 900,
            sourceHeightPx = 1600,
            targetViewport = HaloViewport(1920, 1080),
        )

        assertEquals(1440f, point.xPx, 0.001f)
        assertEquals(810f, point.yPx, 0.001f)
    }

    @Test
    fun landscapeTouchSurfaceRotatesIntoPortraitDisplay() {
        val point = HaloOrientationMapper.map(
            sample = sample(0.25f, 0.75f),
            sourceWidthPx = 1600,
            sourceHeightPx = 900,
            targetViewport = HaloViewport(1080, 1920),
        )

        assertEquals(270f, point.xPx, 0.001f)
        assertEquals(480f, point.yPx, 0.001f)
    }

    @Test
    fun orientationIsDerivedFromAvailableGeometry() {
        assertEquals(
            HaloOrientation.PORTRAIT,
            HaloOrientation.fromSize(800, 1200),
        )
        assertEquals(
            HaloOrientation.LANDSCAPE,
            HaloOrientation.fromSize(1200, 800),
        )
        assertEquals(
            HaloOrientation.LANDSCAPE,
            HaloOrientation.fromSize(1000, 1000),
        )
    }

    @Test
    fun rotatedMappingStillHonoursContentBounds() {
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

        val point = HaloOrientationMapper.map(
            sample = sample(0.5f, 0.5f),
            sourceWidthPx = 900,
            sourceHeightPx = 1600,
            targetViewport = viewport,
        )

        assertEquals(960f, point.xPx, 0.001f)
        assertEquals(540f, point.yPx, 0.001f)
    }
}
