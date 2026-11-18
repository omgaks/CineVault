package com.sole.cinevault.glasses.stereo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StereoModeChoiceTest {
    @Test fun storedValuesRoundTripAndUnknownFallsBackToAuto() {
        StereoModeChoice.entries.forEach { assertEquals(it, StereoModeChoice.fromStored(it.stored)) }
        assertEquals(StereoModeChoice.AUTO, StereoModeChoice.fromStored(null))
        assertEquals(StereoModeChoice.AUTO, StereoModeChoice.fromStored("tab"))
    }

    @Test fun topBottomCanNeverBeForced() {
        assertNull(StereoModeChoice.AUTO.toOverride())
        assertEquals(StereoPlaybackMode.NORMAL_2D, StereoModeChoice.OFF.toOverride())
        assertEquals(StereoPlaybackMode.SIDE_BY_SIDE, StereoModeChoice.SIDE_BY_SIDE.toOverride())
        StereoModeChoice.entries.forEach { assert(it.toOverride() != StereoPlaybackMode.TOP_BOTTOM) }
    }

    @Test fun forcedSbsOnPlainFilmStillPlansNativeSbs() {
        val decision = StereoPlaybackPolicy.resolve(
            detected = StereoPlaybackDetector.detect("holiday.mkv"),
            externalDisplayActive = true,
            userOverride = StereoModeChoice.SIDE_BY_SIDE.toOverride(),
        )
        val plan = ExternalStereoRenderPlanner.plan(decision, externalDisplayDestination = true)
        assertEquals(StereoPlaybackMode.SIDE_BY_SIDE, plan.mode)
        assertEquals(true, plan.forceFillExternalFrame)
    }

    @Test fun offOverridesAMarkedSbsFile() {
        val decision = StereoPlaybackPolicy.resolve(
            detected = StereoPlaybackDetector.detect("movie.3D.SBS.mkv"),
            externalDisplayActive = true,
            userOverride = StereoModeChoice.OFF.toOverride(),
        )
        assertEquals(StereoPlaybackMode.NORMAL_2D, decision.mode)
    }

    @Test fun noticesAreHonest() {
        val sbs = ExternalStereoRenderPlanner.plan(
            StereoPlaybackDetector.detect("a.sbs.mkv"), externalDisplayDestination = true)
        val tab = ExternalStereoRenderPlanner.plan(
            StereoPlaybackDetector.detect("a.tab.mkv"), externalDisplayDestination = true)
        val flat = ExternalStereoRenderPlanner.plan(
            StereoPlaybackDetector.detect("a.mkv"), externalDisplayDestination = true)
        assertNotNull(StereoNoticeResolver.resolve(sbs))
        assert(StereoNoticeResolver.resolve(tab)!!.detail.contains("flat"))
        assertNull(StereoNoticeResolver.resolve(flat))
    }
}
