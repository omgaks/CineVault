package com.sole.cinevault.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkRadarMathTest {
    @Test
    fun placementIsStableAndInRange() {
        val a = radarPlacement("tv-living-room")
        assertEquals(a, radarPlacement("tv-living-room"))
        assertTrue(a.first in 0f..360f)
        assertTrue(a.second in 0.35f..0.9f)
    }

    @Test
    fun glowPeaksJustAfterTheSweepPasses() {
        assertEquals(1f, sweepGlow(100f, 100f), 0.001f)
        assertTrue(sweepGlow(110f, 100f) > sweepGlow(200f, 100f))
        assertTrue(sweepGlow(99f, 100f) < 0.05f)
    }
}
