package com.sole.cinevault.glasses.stereo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StereoPlaybackClosureTest {
    private fun decision(mode: StereoPlaybackMode) =
        StereoPlaybackDecision(mode, StereoDetectionConfidence.STRONG, "test")

    @Test fun twoDHasNoStereoBadgeOrGuidance() {
        val status = StereoPlaybackStatusResolver.resolve(
            ExternalStereoRenderPlanner.plan(decision(StereoPlaybackMode.NORMAL_2D), true)
        )
        assertEquals(StereoPlaybackStatusKind.TWO_D, status.kind)
        assertNull(status.compactLabel)
        assertNull(status.guidance)
    }

    @Test fun sbsExposesCompactReadyStateAndHardwareGuidance() {
        val status = StereoPlaybackStatusResolver.resolve(
            ExternalStereoRenderPlanner.plan(decision(StereoPlaybackMode.SIDE_BY_SIDE), true)
        )
        assertEquals(StereoPlaybackStatusKind.SBS_READY, status.kind)
        assertEquals("3D • SBS", status.compactLabel)
        assertTrue(status.guidance?.contains("3D mode") == true)
    }

    @Test fun topBottomIsExplicitlyNotClaimedAsNativeSbs() {
        val status = StereoPlaybackStatusResolver.resolve(
            ExternalStereoRenderPlanner.plan(decision(StereoPlaybackMode.TOP_BOTTOM), true)
        )
        assertEquals(StereoPlaybackStatusKind.TOP_BOTTOM_UNSUPPORTED, status.kind)
        assertEquals("3D • TAB", status.compactLabel)
        assertTrue(status.guidance?.contains("conversion") == true)
    }

    @Test fun hostDestinationSuppressesStereoStatusEvenForSbsSource() {
        val status = StereoPlaybackStatusResolver.resolve(
            ExternalStereoRenderPlanner.plan(decision(StereoPlaybackMode.SIDE_BY_SIDE), false)
        )
        assertEquals(StereoPlaybackStatusKind.TWO_D, status.kind)
        assertNull(status.compactLabel)
    }

    @Test fun conflictingDetectionNeverLeaksIntoStereoRendering() {
        val detected = StereoPlaybackDetector.detect("movie.SBS.HOU.mkv")
        val plan = ExternalStereoRenderPlanner.plan(detected, true)
        val status = StereoPlaybackStatusResolver.resolve(plan)
        assertEquals(StereoPlaybackMode.NORMAL_2D, detected.mode)
        assertEquals(StereoPlaybackStatusKind.TWO_D, status.kind)
    }
}
