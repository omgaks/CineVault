package com.sole.cinevault.picture

import org.junit.Assert.*
import org.junit.Test

class PicturePipelineTest {
    @Test fun v1Foundation_marksFutureStagesPlannedAndDisabled() {
        val p = PicturePipelineProfiles.v1Foundation(PictureContent.ANIME)
        assertEquals(PictureStageCapability.AVAILABLE, p.stage(PictureProcessingStage.ARTIFACT_REPAIR)?.capability)
        assertEquals(PictureStageCapability.PLANNED, p.stage(PictureProcessingStage.RECONSTRUCTION)?.capability)
        assertFalse(p.stage(PictureProcessingStage.RECONSTRUCTION)!!.enabled)
        assertTrue(p.isRuntimeReady())
    }

    @Test fun contentTexturePolicy_preservesCurrentV1Intent() {
        assertFalse(PicturePipelineProfiles.v1Foundation(PictureContent.ANIME).stage(PictureProcessingStage.TEXTURE)!!.enabled)
        assertTrue(PicturePipelineProfiles.v1Foundation(PictureContent.FILM).stage(PictureProcessingStage.TEXTURE)!!.enabled)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateStages_areRejected() {
        PicturePipelineProfile("bad", PictureContent.FILM, PictureQualityTier.ECO, listOf(
            PictureStageSpec(PictureProcessingStage.ADAPTIVE_SHARPEN, PictureStageCapability.AVAILABLE, true),
            PictureStageSpec(PictureProcessingStage.ADAPTIVE_SHARPEN, PictureStageCapability.AVAILABLE, true),
        ))
    }

    @Test fun performancePolicy_usesMeasuredFrameBudget() {
        val b = PictureFrameBudget(60f)
        assertEquals(16.6667f, b.frameBudgetMs, 0.01f)
        assertEquals(PicturePerformanceState.OVER_BUDGET,
            PicturePerformancePolicy.classify(PicturePerformanceSample(gpuFrameMs=18f), b))
        assertEquals(PicturePerformanceState.PRESSURED,
            PicturePerformancePolicy.classify(PicturePerformanceSample(gpuFrameMs=15f), b))
        assertEquals(PicturePerformanceState.THERMAL_LIMITED,
            PicturePerformancePolicy.classify(PicturePerformanceSample(thermalLimited=true), b))
    }

    @Test fun qualityDowngrade_neverFallsBelowEco() {
        assertEquals(PictureQualityTier.BALANCED, PicturePerformancePolicy.downgrade(PictureQualityTier.MAX))
        assertEquals(PictureQualityTier.ECO, PicturePerformancePolicy.downgrade(PictureQualityTier.BALANCED))
        assertEquals(PictureQualityTier.ECO, PicturePerformancePolicy.downgrade(PictureQualityTier.ECO))
    }
}
