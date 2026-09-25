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
    fun landscapeToLandscapePreservesNormalisedPositionAcrossAspectRatios() {
        val point = HaloOrientationMapper.map(
            sample = sample(0.25f, 0.75f),
            sourceWidthPx = 1280,
            sourceHeightPx = 800,
            targetViewport = HaloViewport(1920, 1080),
        )

        assertEquals(480f, point.xPx, 0.001f)
        assertEquals(810f, point.yPx, 0.001f)
    }

    @Test
    fun portraitControllerRotatesIntoLandscapeExternalDisplay() {
        val point = HaloOrientationMapper.map(
            sample = sample(0.25f, 0.75f),
            sourceWidthPx = 800,
            sourceHeightPx = 1200,
            targetViewport = HaloViewport(1920, 1080),
        )

        assertEquals(1440f, point.xPx, 0.001f)
        assertEquals(810f, point.yPx, 0.001f)
    }

    @Test
    fun landscapeControllerRotatesIntoPortraitExternalDisplay() {
        val point = HaloOrientationMapper.map(
            sample = sample(0.25f, 0.75f),
            sourceWidthPx = 1200,
            sourceHeightPx = 800,
            targetViewport = HaloViewport(1080, 1920),
        )

        assertEquals(270f, point.xPx, 0.001f)
        assertEquals(480f, point.yPx, 0.001f)
    }

    @Test
    fun compactPortraitControllerMapsToWideExternalDisplay() {
        val point = HaloOrientationMapper.map(
            sample = sample(0.10f, 0.90f),
            sourceWidthPx = 390,
            sourceHeightPx = 844,
            targetViewport = HaloViewport(2560, 1080),
        )

        assertEquals(2304f, point.xPx, 0.001f)
        assertEquals(972f, point.yPx, 0.001f)
    }

    @Test
    fun mediumLandscapeControllerMapsToWideExternalDisplay() {
        val point = HaloOrientationMapper.map(
            sample = sample(0.40f, 0.60f),
            sourceWidthPx = 700,
            sourceHeightPx = 500,
            targetViewport = HaloViewport(2560, 1080),
        )

        assertEquals(1024f, point.xPx, 0.001f)
        assertEquals(648f, point.yPx, 0.001f)
    }

    @Test
    fun squareFreeformSurfaceUsesStableLandscapeSemantics() {
        assertEquals(
            HaloOrientation.LANDSCAPE,
            HaloOrientation.fromSize(600, 600),
        )

        val point = HaloOrientationMapper.map(
            sample = sample(0.30f, 0.70f),
            sourceWidthPx = 600,
            sourceHeightPx = 600,
            targetViewport = HaloViewport(1920, 1080),
        )

        assertEquals(576f, point.xPx, 0.001f)
        assertEquals(756f, point.yPx, 0.001f)
    }

    @Test
    fun orientationIsDerivedOnlyFromCurrentAvailableGeometry() {
        assertEquals(HaloOrientation.PORTRAIT, HaloOrientation.fromSize(480, 900))
        assertEquals(HaloOrientation.LANDSCAPE, HaloOrientation.fromSize(900, 480))
        assertEquals(HaloOrientation.LANDSCAPE, HaloOrientation.fromSize(700, 700))
    }

    @Test
    fun rotatedMappingStillHonoursExternalContentBounds() {
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
            sourceWidthPx = 800,
            sourceHeightPx = 1200,
            targetViewport = viewport,
        )

        assertEquals(960f, point.xPx, 0.001f)
        assertEquals(540f, point.yPx, 0.001f)
    }
}
