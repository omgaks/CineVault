package com.sole.cinevault.glasses.stereo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalStereoRenderPlanTest {
    private fun decision(mode: StereoPlaybackMode) =
        StereoPlaybackDecision(mode, StereoDetectionConfidence.STRONG, "test")

    @Test fun hostNeverReceivesStereoRenderPlan() {
        val plan = ExternalStereoRenderPlanner.plan(
            decision(StereoPlaybackMode.SIDE_BY_SIDE),
            externalDisplayDestination = false,
        )
        assertEquals(StereoPlaybackMode.NORMAL_2D, plan.mode)
        assertFalse(plan.requiresHardware3dMode)
    }

    @Test fun ordinaryExternalPlaybackKeepsNormalFit() {
        val plan = ExternalStereoRenderPlanner.plan(
            decision(StereoPlaybackMode.NORMAL_2D),
            externalDisplayDestination = true,
        )
        assertFalse(plan.forceFillExternalFrame)
        assertFalse(plan.requiresHardware3dMode)
    }

    @Test fun sbsUsesNativeFullFrameOutput() {
        val plan = ExternalStereoRenderPlanner.plan(
            decision(StereoPlaybackMode.SIDE_BY_SIDE),
            externalDisplayDestination = true,
        )
        assertTrue(plan.forceFillExternalFrame)
        assertTrue(plan.requiresHardware3dMode)
        assertTrue(plan.supportedByNativeSbsOutput)
    }

    @Test fun topBottomIsNotFalselyRenderedAsNativeSbs() {
        val plan = ExternalStereoRenderPlanner.plan(
            decision(StereoPlaybackMode.TOP_BOTTOM),
            externalDisplayDestination = true,
        )
        assertFalse(plan.forceFillExternalFrame)
        assertTrue(plan.requiresHardware3dMode)
        assertFalse(plan.supportedByNativeSbsOutput)
    }
}
