package com.sole.cinevault.picture

import org.junit.Assert.*
import org.junit.Test

class PictureControllerRuntimeAdapterTest {

    @Test fun contentChanges_publishResolvedProfileWithoutRenderingSideEffects() {
        val adapter = PictureControllerRuntimeAdapter()
        adapter.onContent(PictureContent.AUTO, PictureContent.ANIME)

        assertEquals(PictureContent.ANIME, adapter.runtimeState.profile.content)
        assertFalse(adapter.runtimeState.bypassed)
    }

    @Test fun videoFormatFacts_publishExistingSafetyBypasses() {
        val adapter = PictureControllerRuntimeAdapter(
            initialSelectedContent = PictureContent.FILM,
            initialDetectedContent = PictureContent.FILM,
        )

        adapter.onVideoFormat(1920, 1080, hdr = true, dolbyVision = false)
        assertTrue(adapter.runtimeState.bypassed)
        assertEquals(
            "Not available for HDR / Dolby Vision video",
            adapter.runtimeState.bypassReason,
        )

        adapter.onVideoFormat(3840, 2160, hdr = false, dolbyVision = false)
        assertTrue(adapter.runtimeState.bypassed)
        assertEquals("Not needed for 4K video", adapter.runtimeState.bypassReason)
    }

    @Test fun pipelineFailure_isRepresentedWithoutTouchingPlayer() {
        val adapter = PictureControllerRuntimeAdapter()
        adapter.onEffectPipelineAvailable(false)

        assertTrue(adapter.runtimeState.bypassed)
        assertEquals(
            "Video effects aren't available here",
            adapter.runtimeState.bypassReason,
        )
    }

    @Test fun thermalAndPerformancePressure_onlyAffectEffectiveTier() {
        val adapter = PictureControllerRuntimeAdapter(
            initialTier = PictureQualityTier.MAX,
        )

        adapter.onThermalLimited(true)
        assertEquals(PictureQualityTier.MAX, adapter.runtimeState.profile.qualityTier)
        assertEquals(PictureQualityTier.BALANCED, adapter.runtimeState.effectiveTier)

        adapter.onThermalLimited(false)
        adapter.onPerformance(PicturePerformanceState.OVER_BUDGET)
        assertEquals(PictureQualityTier.BALANCED, adapter.runtimeState.effectiveTier)
    }

    @Test fun resetForVideo_clearsTransientRuntimePressure() {
        val adapter = PictureControllerRuntimeAdapter(
            initialTier = PictureQualityTier.MAX,
        )
        adapter.onVideoFormat(3840, 2160, hdr = false, dolbyVision = false)
        adapter.onThermalLimited(true)
        assertTrue(adapter.runtimeState.bypassed)

        adapter.resetForVideo(PictureContent.AUTO, PictureContent.ANIMATION)

        assertFalse(adapter.runtimeState.bypassed)
        assertEquals(PictureContent.ANIMATION, adapter.runtimeState.profile.content)
        assertEquals(PictureQualityTier.MAX, adapter.runtimeState.effectiveTier)
    }
}
