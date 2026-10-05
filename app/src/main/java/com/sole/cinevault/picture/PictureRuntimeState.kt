package com.sole.cinevault.picture

/**
 * Read-only architectural view of the current Picture session.
 *
 * P1 intentionally keeps this independent from Android/Media3 and does not alter
 * the existing PictureController or shader installation path. Later slices can
 * publish this state from the controller without changing its visual output.
 */
data class PictureRuntimeState(
    val profile: PicturePipelineProfile,
    val decision: PictureRuntimeDecision,
    val enabledStages: List<PictureProcessingStage>,
) {
    val bypassed: Boolean
        get() = decision is PictureRuntimeDecision.Bypass

    val bypassReason: String?
        get() = (decision as? PictureRuntimeDecision.Bypass)?.reason

    val effectiveTier: PictureQualityTier?
        get() = (decision as? PictureRuntimeDecision.Ready)?.effectiveTier
}

/**
 * Single pure entry point that composes the P1 planner, capability policy and
 * stage exposure into one runtime snapshot.
 */
object PictureRuntimeStateFactory {

    fun create(
        selectedContent: PictureContent,
        detectedContent: PictureContent,
        requestedTier: PictureQualityTier,
        capabilities: PictureRuntimeCapabilities,
        performance: PicturePerformanceState = PicturePerformanceState.HEALTHY,
    ): PictureRuntimeState {
        val profile = PicturePipelinePlanner.plan(
            selectedContent = selectedContent,
            detectedContent = detectedContent,
            requestedTier = requestedTier,
        )
        val decision = PictureRuntimePolicy.decide(
            profile = profile,
            capabilities = capabilities,
            performance = performance,
        )
        val stages = if (decision is PictureRuntimeDecision.Ready) {
            PicturePipelinePlanner.enabledStages(profile)
        } else {
            emptyList()
        }
        return PictureRuntimeState(
            profile = profile,
            decision = decision,
            enabledStages = stages,
        )
    }
}
