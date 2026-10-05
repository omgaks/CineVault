package com.sole.cinevault.picture

/**
 * Pure capability snapshot for P1. Runtime/Android discovery is deliberately kept
 * outside this file so policy remains covered by plain JVM tests.
 */
data class PictureRuntimeCapabilities(
    val hdr: Boolean = false,
    val dolbyVision: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val effectPipelineAvailable: Boolean = true,
    val thermalLimited: Boolean = false,
) {
    val isUltraHd: Boolean
        get() = width > 2560 || height > 1440
}

sealed interface PictureRuntimeDecision {
    data class Ready(
        val profile: PicturePipelineProfile,
        val effectiveTier: PictureQualityTier,
    ) : PictureRuntimeDecision

    data class Bypass(val reason: String) : PictureRuntimeDecision
}

/**
 * Central P1 safety gate. It preserves today's rules:
 * - HDR / Dolby Vision bypass until the dedicated HDR phase.
 * - >1440p / >2560-wide content bypasses the enhancement path.
 * - missing effect support bypasses cleanly.
 * It also gives later slices one place to apply measured tier pressure.
 */
object PictureRuntimePolicy {

    fun decide(
        profile: PicturePipelineProfile,
        capabilities: PictureRuntimeCapabilities,
        performance: PicturePerformanceState = PicturePerformanceState.HEALTHY,
    ): PictureRuntimeDecision {
        if (!capabilities.effectPipelineAvailable) {
            return PictureRuntimeDecision.Bypass("Video effects aren't available here")
        }
        if (capabilities.hdr || capabilities.dolbyVision) {
            return PictureRuntimeDecision.Bypass("Not available for HDR / Dolby Vision video")
        }
        if (capabilities.isUltraHd) {
            return PictureRuntimeDecision.Bypass("Not needed for 4K video")
        }
        if (!profile.isRuntimeReady()) {
            return PictureRuntimeDecision.Bypass("Picture pipeline isn't ready on this device")
        }

        val pressured = capabilities.thermalLimited ||
            performance == PicturePerformanceState.THERMAL_LIMITED ||
            performance == PicturePerformanceState.OVER_BUDGET

        val tier = if (pressured) {
            PicturePerformancePolicy.downgrade(profile.qualityTier)
        } else {
            profile.qualityTier
        }

        return PictureRuntimeDecision.Ready(profile, tier)
    }
}
