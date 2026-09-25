package com.sole.cinevault.glasses

import org.junit.Assert.assertEquals
import org.junit.Test

class CinemaVoidAdaptivePolicyTest {

    @Test
    fun compactPortraitUsesCompactProfile() {
        assertEquals(
            CinemaVoidWindowProfile.COMPACT,
            CinemaVoidAdaptivePolicy.resolve(390, 844).profile,
        )
    }

    @Test
    fun compactLandscapeStillUsesAvailableWidth() {
        assertEquals(
            CinemaVoidWindowProfile.COMPACT,
            CinemaVoidAdaptivePolicy.resolve(590, 360).profile,
        )
    }

    @Test
    fun mediumPortraitUsesMediumProfile() {
        assertEquals(
            CinemaVoidWindowProfile.MEDIUM,
            CinemaVoidAdaptivePolicy.resolve(700, 1000).profile,
        )
    }

    @Test
    fun mediumLandscapeUsesMediumProfile() {
        assertEquals(
            CinemaVoidWindowProfile.MEDIUM,
            CinemaVoidAdaptivePolicy.resolve(839, 500).profile,
        )
    }

    @Test
    fun expandedLandscapeUsesExpandedProfile() {
        assertEquals(
            CinemaVoidWindowProfile.EXPANDED,
            CinemaVoidAdaptivePolicy.resolve(1200, 800).profile,
        )
    }

    @Test
    fun expandedPortraitUsesExpandedProfileWhenAvailableWidthAllowsIt() {
        assertEquals(
            CinemaVoidWindowProfile.EXPANDED,
            CinemaVoidAdaptivePolicy.resolve(900, 1200).profile,
        )
    }

    @Test
    fun splitScreenReclassifiesSameLargeWindowToCompact() {
        val full = CinemaVoidAdaptivePolicy.resolve(1200, 800)
        val split = CinemaVoidAdaptivePolicy.resolve(480, 800)

        assertEquals(CinemaVoidWindowProfile.EXPANDED, full.profile)
        assertEquals(CinemaVoidWindowProfile.COMPACT, split.profile)
    }

    @Test
    fun foldLikeResizeReclassifiesWithoutDeviceIdentity() {
        val unfolded = CinemaVoidAdaptivePolicy.resolve(1000, 900)
        val folded = CinemaVoidAdaptivePolicy.resolve(700, 900)

        assertEquals(CinemaVoidWindowProfile.EXPANDED, unfolded.profile)
        assertEquals(CinemaVoidWindowProfile.MEDIUM, folded.profile)
    }

    @Test
    fun gestureGeometryStaysProportionalAcrossEveryProfile() {
        val specs =
            listOf(
                CinemaVoidAdaptivePolicy.resolve(390, 844),
                CinemaVoidAdaptivePolicy.resolve(700, 900),
                CinemaVoidAdaptivePolicy.resolve(1200, 800),
            )

        specs.forEach { spec ->
            assertEquals(0.20f, spec.edgeGestureFraction)
            assertEquals(0.50f, spec.seekGestureFraction)
            assertEquals(1.00f, spec.haloSurfaceFraction)
        }
    }

    @Test
    fun tinyFreeformWindowRemainsSafeAndCompact() {
        val spec = CinemaVoidAdaptivePolicy.resolve(280, 240)

        assertEquals(CinemaVoidWindowProfile.COMPACT, spec.profile)
        assertEquals(0.92f, spec.statusCardWidthFraction)
    }
}
