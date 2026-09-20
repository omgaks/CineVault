package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloPlayerGestureRouterTest {

    @Test
    fun leftTwentyPercentRoutesToBrightness() {
        assertEquals(
            HaloPlayerGestureZone.BRIGHTNESS,
            HaloPlayerGestureRouter.zoneFor(HaloVector(0.10f, 0.60f)),
        )
    }

    @Test
    fun rightTwentyPercentRoutesToVolume() {
        assertEquals(
            HaloPlayerGestureZone.VOLUME,
            HaloPlayerGestureRouter.zoneFor(HaloVector(0.90f, 0.60f)),
        )
    }

    @Test
    fun topThirtyPercentRoutesToSeekAcrossWholeWidth() {
        assertEquals(
            HaloPlayerGestureZone.SEEK,
            HaloPlayerGestureRouter.zoneFor(HaloVector(0.05f, 0.10f)),
        )
        assertEquals(
            HaloPlayerGestureZone.SEEK,
            HaloPlayerGestureRouter.zoneFor(HaloVector(0.50f, 0.20f)),
        )
        assertEquals(
            HaloPlayerGestureZone.SEEK,
            HaloPlayerGestureRouter.zoneFor(HaloVector(0.95f, 0.30f)),
        )
    }

    @Test
    fun centreRemainsCanonicalCineVaultInteraction() {
        assertEquals(
            HaloPlayerGestureZone.CANONICAL_UI,
            HaloPlayerGestureRouter.zoneFor(HaloVector(0.50f, 0.60f)),
        )
    }

    @Test
    fun upwardLeftDragProducesPositiveBrightnessDelta() {
        val intent = HaloPlayerGestureRouter.classifyDrag(
            start = HaloVector(0.10f, 0.70f),
            current = HaloVector(0.10f, 0.50f),
        )

        assertTrue(intent is HaloPlayerGestureIntent.Brightness)
        assertEquals(
            0.20f,
            (intent as HaloPlayerGestureIntent.Brightness).verticalDeltaFraction,
            0.0001f,
        )
    }

    @Test
    fun upwardRightDragProducesPositiveVolumeDelta() {
        val intent = HaloPlayerGestureRouter.classifyDrag(
            start = HaloVector(0.90f, 0.70f),
            current = HaloVector(0.90f, 0.40f),
        )

        assertTrue(intent is HaloPlayerGestureIntent.Volume)
        assertEquals(
            0.30f,
            (intent as HaloPlayerGestureIntent.Volume).verticalDeltaFraction,
            0.0001f,
        )
    }

    @Test
    fun rightwardTopDragProducesPositiveSeekDelta() {
        val intent = HaloPlayerGestureRouter.classifyDrag(
            start = HaloVector(0.30f, 0.15f),
            current = HaloVector(0.65f, 0.15f),
        )

        assertTrue(intent is HaloPlayerGestureIntent.Seek)
        assertEquals(
            0.35f,
            (intent as HaloPlayerGestureIntent.Seek).horizontalDeltaFraction,
            0.0001f,
        )
    }

    @Test
    fun gestureOwnershipIsLockedToStartingZone() {
        val intent = HaloPlayerGestureRouter.classifyDrag(
            start = HaloVector(0.10f, 0.60f),
            current = HaloVector(0.80f, 0.10f),
        )

        assertTrue(intent is HaloPlayerGestureIntent.Brightness)
    }
}
