package com.sole.cinevault.picture

/**
 * P7-S2 runtime telemetry accumulator. The player/controller owns event registration.
 * This class never changes the Media3 effect list or persisted picture settings.
 */
class PicturePerformanceRuntime {
    private var dropped = 0
    private var observed = 0
    private var thermal = 0
    private var width = 0
    private var height = 0
    private var fps = 30f

    fun setSource(sourceWidth: Int, sourceHeight: Int, frameRate: Float) {
        width = sourceWidth.coerceAtLeast(0)
        height = sourceHeight.coerceAtLeast(0)
        fps = frameRate.takeIf { it.isFinite() && it > 0f } ?: 30f
        resetWindow()
    }

    fun setThermalStatus(status: Int) { thermal = status.coerceAtLeast(0) }

    /** Accept one measured window, not a lifetime total. */
    fun recordWindow(droppedFrames: Int, totalFrames: Int) {
        val total = totalFrames.coerceAtLeast(0)
        observed = (observed.toLong() + total).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        dropped = (dropped.toLong() + droppedFrames.coerceIn(0, total)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun plan(intensity: Float): PictureAdaptivePerformancePolicy.Plan =
        PictureAdaptivePerformancePolicy.plan(
            PictureAdaptivePerformancePolicy.Input(
                sourceWidth = width,
                sourceHeight = height,
                frameRate = fps,
                intensity = intensity,
                droppedFrames = dropped,
                observedFrames = observed,
                thermalStatus = thermal,
            )
        )

    fun resetWindow() { dropped = 0; observed = 0 }
    fun reset() { resetWindow(); thermal = 0; width = 0; height = 0; fps = 30f }
}
