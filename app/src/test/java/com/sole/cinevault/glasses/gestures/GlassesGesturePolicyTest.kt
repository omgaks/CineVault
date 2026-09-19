package com.sole.cinevault.glasses.gestures

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassesGesturePolicyTest {
    private val width = 1000f

    @Test
    fun edgeZonesStayReservedForEpisodeNavigation() {
        assertTrue(GlassesGesturePolicy.isLeftEdge(50f, width))
        assertTrue(GlassesGesturePolicy.isRightEdge(950f, width))
        assertFalse(GlassesGesturePolicy.isLeftEdge(150f, width))
        assertFalse(GlassesGesturePolicy.isRightEdge(850f, width))
    }

    @Test
    fun brightnessAndVolumeZonesDoNotConsumeEdgeNavigation() {
        assertFalse(GlassesGesturePolicy.isBrightnessZone(50f, width))
        assertTrue(GlassesGesturePolicy.isBrightnessZone(200f, width))

        assertTrue(GlassesGesturePolicy.isVolumeZone(800f, width))
        assertFalse(GlassesGesturePolicy.isVolumeZone(950f, width))
    }

    @Test
    fun centerZoneIsDedicatedToPointerOrSeek() {
        assertTrue(GlassesGesturePolicy.isCenterZone(500f, width))
        assertFalse(GlassesGesturePolicy.isCenterZone(200f, width))
        assertFalse(GlassesGesturePolicy.isCenterZone(800f, width))
    }

    @Test
    fun invalidWidthNeverClaimsGestureZone() {
        assertFalse(GlassesGesturePolicy.isLeftEdge(0f, 0f))
        assertFalse(GlassesGesturePolicy.isRightEdge(0f, 0f))
        assertFalse(GlassesGesturePolicy.isBrightnessZone(0f, 0f))
        assertFalse(GlassesGesturePolicy.isVolumeZone(0f, 0f))
        assertFalse(GlassesGesturePolicy.isCenterZone(0f, 0f))
    }
}
