package com.sole.cinevault.picture

import org.junit.Assert.*
import org.junit.Test

class PictureRuntimeStateTest {

    @Test fun stateFactory_resolvesAutoAndExposesOnlyReadyStages() {
        val state = PictureRuntimeStateFactory.create(
            selectedContent = PictureContent.AUTO,
            detectedContent = PictureContent.ANIME,
            requestedTier = PictureQualityTier.BALANCED,
            capabilities = PictureRuntimeCapabilities(width = 1920, height = 1080),
        )

        assertEquals(PictureContent.ANIME, state.profile.content)
        assertFalse(state.bypassed)
        assertEquals(PictureQualityTier.BALANCED, state.effectiveTier)
        assertTrue(PictureProcessingStage.ARTIFACT_REPAIR in state.enabledStages)
        assertFalse(PictureProcessingStage.RECONSTRUCTION in state.enabledStages)
    }

    @Test fun bypassState_neverExposesActiveStages() {
        val state = PictureRuntimeStateFactory.create(
            selectedContent = PictureContent.FILM,
            detectedContent = PictureContent.FILM,
            requestedTier = PictureQualityTier.MAX,
            capabilities = PictureRuntimeCapabilities(hdr = true, width = 1920, height = 1080),
        )

        assertTrue(state.bypassed)
        assertNotNull(state.bypassReason)
        assertNull(state.effectiveTier)
        assertTrue(state.enabledStages.isEmpty())
    }

    @Test fun telemetry_accumulatesWithoutChangingPolicy() {
        val budget = PictureFrameBudget(60f)
        val telemetry = PictureRuntimeTelemetry()
            .record(PicturePerformanceSample(gpuFrameMs = 10f), budget)
            .record(PicturePerformanceSample(gpuFrameMs = 18f, droppedFrames = 2), budget)

        assertEquals(2, telemetry.sampleCount)
        assertEquals(2, telemetry.droppedFrames)
        assertEquals(18f, telemetry.worstGpuFrameMs!!, 0f)
        assertEquals(PicturePerformanceState.OVER_BUDGET, telemetry.latestState)
    }

    @Test fun thermalCapability_downgradesRequestedTier() {
        val state = PictureRuntimeStateFactory.create(
            selectedContent = PictureContent.ANIMATION,
            detectedContent = PictureContent.FILM,
            requestedTier = PictureQualityTier.MAX,
            capabilities = PictureRuntimeCapabilities(
                width = 1920,
                height = 1080,
                thermalLimited = true,
            ),
        )

        assertEquals(PictureQualityTier.BALANCED, state.effectiveTier)
    }
}
