package com.sole.cinevault.picture

data class PictureFrameBudget(val refreshRateHz: Float) {
    val frameBudgetMs: Float
        get() = if (refreshRateHz > 0f) 1000f / refreshRateHz else 16.6667f
}
data class PicturePerformanceSample(
    val gpuFrameMs: Float? = null,
    val droppedFrames: Int = 0,
    val thermalLimited: Boolean = false,
)
enum class PicturePerformanceState { HEALTHY, PRESSURED, OVER_BUDGET, THERMAL_LIMITED }

object PicturePerformancePolicy {
    fun classify(sample: PicturePerformanceSample, budget: PictureFrameBudget): PicturePerformanceState {
        if (sample.thermalLimited) return PicturePerformanceState.THERMAL_LIMITED
        val gpu = sample.gpuFrameMs
        if (gpu != null && gpu > budget.frameBudgetMs) return PicturePerformanceState.OVER_BUDGET
        if (sample.droppedFrames > 0 || (gpu != null && gpu >= budget.frameBudgetMs * 0.85f)) {
            return PicturePerformanceState.PRESSURED
        }
        return PicturePerformanceState.HEALTHY
    }
    fun downgrade(tier: PictureQualityTier) = when (tier) {
        PictureQualityTier.MAX -> PictureQualityTier.BALANCED
        PictureQualityTier.BALANCED -> PictureQualityTier.ECO
        PictureQualityTier.ECO -> PictureQualityTier.ECO
    }
}
