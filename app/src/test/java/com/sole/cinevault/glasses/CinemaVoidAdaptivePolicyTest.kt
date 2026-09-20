package com.sole.cinevault.glasses

import org.junit.Assert.assertEquals
import org.junit.Test

class CinemaVoidAdaptivePolicyTest {

    @Test
    fun compactWindowUsesCompactProfile() {
        assertEquals(
            CinemaVoidWindowProfile.COMPACT,
            CinemaVoidAdaptivePolicy.resolve(390, 844).profile,
        )
    }

    @Test
    fun mediumWindowUsesMediumProfile() {
        assertEquals(
            CinemaVoidWindowProfile.MEDIUM,
            CinemaVoidAdaptivePolicy.resolve(700, 900).profile,
        )
    }

    @Test
    fun expandedWindowUsesExpandedProfile() {
        assertEquals(
            CinemaVoidWindowProfile.EXPANDED,
            CinemaVoidAdaptivePolicy.resolve(1000, 700).profile,
        )
    }

    @Test
    fun resizedWindowReclassifiesFromAvailableWidth() {
        val expanded = CinemaVoidAdaptivePolicy.resolve(1000, 700)
        val split = CinemaVoidAdaptivePolicy.resolve(480, 700)

        assertEquals(CinemaVoidWindowProfile.EXPANDED, expanded.profile)
        assertEquals(CinemaVoidWindowProfile.COMPACT, split.profile)
    }

    @Test
    fun gestureGeometryStaysProportionalAcrossProfiles() {
        val compact = CinemaVoidAdaptivePolicy.resolve(390, 844)
        val expanded = CinemaVoidAdaptivePolicy.resolve(1200, 800)

        assertEquals(0.20f, compact.edgeGestureFraction)
        assertEquals(0.20f, expanded.edgeGestureFraction)
        assertEquals(0.50f, compact.seekGestureFraction)
        assertEquals(0.50f, expanded.seekGestureFraction)
        assertEquals(1.00f, compact.haloSurfaceFraction)
        assertEquals(1.00f, expanded.haloSurfaceFraction)
    }
}
