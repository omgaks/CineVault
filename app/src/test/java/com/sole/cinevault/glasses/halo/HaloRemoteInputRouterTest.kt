package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HaloRemoteInputRouterTest {

    @Test
    fun returnsNullUntilExternalTargetGeometryExists() {
        val router = HaloRemoteInputRouter()

        assertNull(
            router.map(
                sample = sample(0.25f, 0.75f),
                sourceWidthPx = 1000,
                sourceHeightPx = 600,
            )
        )
    }

    @Test
    fun sameOrientationPreservesNormalisedPosition() {
        val router = HaloRemoteInputRouter()
        router.updateTargetViewport(1920, 1080)

        val mapped =
            router.map(
                sample = sample(0.25f, 0.75f),
                sourceWidthPx = 1200,
                sourceHeightPx = 800,
            )!!

        assertEquals(0.25f, mapped.xFraction, 0.0001f)
        assertEquals(0.75f, mapped.yFraction, 0.0001f)
        assertEquals(480f, mapped.xPx, 0.0001f)
        assertEquals(810f, mapped.yPx, 0.0001f)
    }

    @Test
    fun portraitControllerMapsIntoLandscapeExternalDisplay() {
        val router = HaloRemoteInputRouter()
        router.updateTargetViewport(1920, 1080)

        val mapped =
            router.map(
                sample = sample(0.20f, 0.70f),
                sourceWidthPx = 800,
                sourceHeightPx = 1200,
            )!!

        assertEquals(0.70f, mapped.xFraction, 0.0001f)
        assertEquals(0.80f, mapped.yFraction, 0.0001f)
        assertEquals(1344f, mapped.xPx, 0.0001f)
        assertEquals(864f, mapped.yPx, 0.0001f)
    }

    @Test
    fun targetResizeUsesFreshGeometryWithoutDeviceAssumptions() {
        val router = HaloRemoteInputRouter()
        router.updateTargetViewport(1600, 900)

        val before =
            router.map(
                sample = sample(0.50f, 0.50f),
                sourceWidthPx = 1000,
                sourceHeightPx = 600,
            )!!

        router.updateTargetViewport(1000, 1600)

        val after =
            router.map(
                sample = sample(0.50f, 0.50f),
                sourceWidthPx = 1000,
                sourceHeightPx = 600,
            )!!

        assertEquals(800f, before.xPx, 0.0001f)
        assertEquals(450f, before.yPx, 0.0001f)
        assertEquals(500f, after.xPx, 0.0001f)
        assertEquals(800f, after.yPx, 0.0001f)
    }

    private fun sample(x: Float, y: Float) =
        HaloPointerSample(
            xPx = 0f,
            yPx = 0f,
            xFraction = x,
            yFraction = y,
            pressed = false,
        )
}
