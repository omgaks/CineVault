package com.sole.cinevault.picture

import org.junit.Assert.*
import org.junit.Test

class PictureRuntimePolicyTest {

    private val film = PicturePipelineProfiles.v1Foundation(
        PictureContent.FILM,
        PictureQualityTier.MAX,
    )

    @Test fun planner_resolvesAutoBeforeBuildingProfile() {
        val profile = PicturePipelinePlanner.plan(
            selectedContent = PictureContent.AUTO,
            detectedContent = PictureContent.ANIME,
            requestedTier = PictureQualityTier.BALANCED,
        )
        assertEquals(PictureContent.ANIME, profile.content)
        assertEquals(PictureQualityTier.BALANCED, profile.qualityTier)
    }

    @Test fun hdrAndDolbyVision_areHardBypasses() {
        assertTrue(
            PictureRuntimePolicy.decide(
                film,
                PictureRuntimeCapabilities(hdr = true),
            ) is PictureRuntimeDecision.Bypass
        )
        assertTrue(
            PictureRuntimePolicy.decide(
                film,
                PictureRuntimeCapabilities(dolbyVision = true),
            ) is PictureRuntimeDecision.Bypass
        )
    }

    @Test fun fourK_isBypassedLikeCurrentV1() {
        val decision = PictureRuntimePolicy.decide(
            film,
            PictureRuntimeCapabilities(width = 3840, height = 2160),
        )
        assertEquals(
            PictureRuntimeDecision.Bypass("Not needed for 4K video"),
            decision,
        )
    }

    @Test fun missingEffectPipeline_bypassesCleanly() {
        assertEquals(
            PictureRuntimeDecision.Bypass("Video effects aren't available here"),
            PictureRuntimePolicy.decide(
                film,
                PictureRuntimeCapabilities(effectPipelineAvailable = false),
            ),
        )
    }

    @Test fun healthyRuntime_keepsRequestedTier() {
        val ready = PictureRuntimePolicy.decide(
            film,
            PictureRuntimeCapabilities(width = 1920, height = 1080),
        ) as PictureRuntimeDecision.Ready
        assertEquals(PictureQualityTier.MAX, ready.effectiveTier)
    }

    @Test fun measuredPressure_downgradesExactlyOneTier() {
        val ready = PictureRuntimePolicy.decide(
            film,
            PictureRuntimeCapabilities(width = 1920, height = 1080),
            PicturePerformanceState.OVER_BUDGET,
        ) as PictureRuntimeDecision.Ready
        assertEquals(PictureQualityTier.BALANCED, ready.effectiveTier)
    }

    @Test fun planner_exposesOnlyAvailableEnabledStages() {
        val profile = PicturePipelinePlanner.plan(
            PictureContent.ANIME,
            PictureContent.FILM,
        )
        val stages = PicturePipelinePlanner.enabledStages(profile)
        assertTrue(PictureProcessingStage.ARTIFACT_REPAIR in stages)
        assertTrue(PictureProcessingStage.ADAPTIVE_SHARPEN in stages)
        assertFalse(PictureProcessingStage.RECONSTRUCTION in stages)
        assertFalse(PictureProcessingStage.CHROMA_RECONSTRUCTION in stages)
    }
}
