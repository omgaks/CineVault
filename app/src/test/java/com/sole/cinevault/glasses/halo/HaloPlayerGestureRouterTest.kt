package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloPlayerGestureRouterTest {

    @Test
    fun topHalfHorizontalDragSeeks() {
        val intent =
            HaloPlayerGestureRouter.classifyDrag(
                start = HaloVector(0.50f, 0.45f),
                current = HaloVector(0.70f, 0.45f),
            )

        assertTrue(intent is HaloPlayerGestureIntent.Seek)
    }

    @Test
    fun belowTopHalfHorizontalDragRemainsCanonicalPointer() {
        val intent =
            HaloPlayerGestureRouter.classifyDrag(
                start = HaloVector(0.50f, 0.55f),
                current = HaloVector(0.70f, 0.55f),
            )

        assertEquals(HaloPlayerGestureIntent.CanonicalUi, intent)
    }

    @Test
    fun leftVerticalWinsEvenInsideTopHalf() {
        val intent =
            HaloPlayerGestureRouter.classifyDrag(
                start = HaloVector(0.10f, 0.25f),
                current = HaloVector(0.10f, 0.10f),
            )

        assertTrue(intent is HaloPlayerGestureIntent.Brightness)
    }

    @Test
    fun rightVerticalWinsEvenInsideTopHalf() {
        val intent =
            HaloPlayerGestureRouter.classifyDrag(
                start = HaloVector(0.90f, 0.25f),
                current = HaloVector(0.90f, 0.10f),
            )

        assertTrue(intent is HaloPlayerGestureIntent.Volume)
    }

    @Test
    fun topLeftHorizontalStillSeeksBecauseDirectionWinsOverlap() {
        val intent =
            HaloPlayerGestureRouter.classifyDrag(
                start = HaloVector(0.10f, 0.25f),
                current = HaloVector(0.30f, 0.25f),
            )

        assertTrue(intent is HaloPlayerGestureIntent.Seek)
    }

    @Test
    fun topRightHorizontalStillSeeksBecauseDirectionWinsOverlap() {
        val intent =
            HaloPlayerGestureRouter.classifyDrag(
                start = HaloVector(0.90f, 0.25f),
                current = HaloVector(0.70f, 0.25f),
            )

        assertTrue(intent is HaloPlayerGestureIntent.Seek)
    }

    @Test
    fun tinyMovementDoesNotArmPlayerAction() {
        val intent =
            HaloPlayerGestureRouter.classifyDrag(
                start = HaloVector(0.50f, 0.20f),
                current = HaloVector(0.505f, 0.20f),
            )

        assertEquals(HaloPlayerGestureIntent.CanonicalUi, intent)
    }

    @Test
    fun diagonalMovementRemainsCanonicalPointer() {
        val intent =
            HaloPlayerGestureRouter.classifyDrag(
                start = HaloVector(0.50f, 0.20f),
                current = HaloVector(0.60f, 0.10f),
            )

        assertEquals(HaloPlayerGestureIntent.CanonicalUi, intent)
    }

    @Test
    fun lockedGeometryUsesFractionsNotDevicePixels() {
        assertEquals(0.20f, HaloPlayerGestureGeometry.SIDE_FRACTION)
        assertEquals(0.50f, HaloPlayerGestureGeometry.TOP_SEEK_FRACTION)
    }
}
