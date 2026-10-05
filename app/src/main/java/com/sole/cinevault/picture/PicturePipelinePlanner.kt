package com.sole.cinevault.picture

/**
 * P1 planner: converts current content selection into an explicit pipeline profile.
 * No shader/effect list is changed here.
 */
object PicturePipelinePlanner {

    fun plan(
        selectedContent: PictureContent,
        detectedContent: PictureContent,
        requestedTier: PictureQualityTier = PictureQualityTier.ECO,
    ): PicturePipelineProfile {
        val resolved = PictureProfiles.resolveContent(selectedContent, detectedContent)
        return PicturePipelineProfiles.v1Foundation(
            content = resolved,
            qualityTier = requestedTier,
        )
    }

    fun enabledStages(profile: PicturePipelineProfile): List<PictureProcessingStage> =
        profile.stages
            .filter { it.enabled && it.capability == PictureStageCapability.AVAILABLE }
            .map { it.stage }
}
