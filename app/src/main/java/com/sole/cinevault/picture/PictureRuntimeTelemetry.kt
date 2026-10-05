package com.sole.cinevault.picture

/**
 * Small P1 telemetry accumulator. It stores no Android objects and performs no
 * scheduling; callers feed measured samples when runtime wiring is introduced.
 */
data class PictureRuntimeTelemetry(
    val sampleCount: Int = 0,
    val droppedFrames: Int = 0,
    val worstGpuFrameMs: Float? = null,
    val latestState: PicturePerformanceState = PicturePerformanceState.HEALTHY,
) {
    fun record(
        sample: PicturePerformanceSample,
        budget: PictureFrameBudget,
    ): PictureRuntimeTelemetry {
        val state = PicturePerformancePolicy.classify(sample, budget)
        val worst = when {
            sample.gpuFrameMs == null -> worstGpuFrameMs
            worstGpuFrameMs == null -> sample.gpuFrameMs
            else -> maxOf(worstGpuFrameMs, sample.gpuFrameMs)
        }
        return copy(
            sampleCount = sampleCount + 1,
            droppedFrames = droppedFrames + sample.droppedFrames.coerceAtLeast(0),
            worstGpuFrameMs = worst,
            latestState = state,
        )
    }
}
