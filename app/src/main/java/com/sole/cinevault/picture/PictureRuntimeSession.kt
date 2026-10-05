package com.sole.cinevault.picture

/**
 * P1 controller-facing runtime session.
 *
 * This is the final architecture boundary before P2. It owns only architectural
 * state and telemetry; it never installs Media3 effects or writes shader uniforms.
 */
class PictureRuntimeSession(
    selectedContent: PictureContent = PictureContent.AUTO,
    detectedContent: PictureContent = PictureContent.FILM,
    requestedTier: PictureQualityTier = PictureQualityTier.ECO,
    refreshRateHz: Float = 60f,
) : PictureRuntimeContract {

    private val adapter = PictureControllerRuntimeAdapter(
        initialSelectedContent = selectedContent,
        initialDetectedContent = detectedContent,
        initialTier = requestedTier,
    )

    private var budget = PictureFrameBudget(refreshRateHz)
    private var telemetry = PictureRuntimeTelemetry()

    override val runtimeState: PictureRuntimeState
        get() = adapter.runtimeState

    val runtimeTelemetry: PictureRuntimeTelemetry
        get() = telemetry

    fun onContent(selected: PictureContent, detected: PictureContent) {
        adapter.onContent(selected, detected)
    }

    fun onVideoFormat(
        width: Int,
        height: Int,
        hdr: Boolean,
        dolbyVision: Boolean,
    ) {
        adapter.onVideoFormat(width, height, hdr, dolbyVision)
    }

    fun onRequestedTier(tier: PictureQualityTier) {
        adapter.onRequestedTier(tier)
    }

    fun onEffectPipelineAvailable(available: Boolean) {
        adapter.onEffectPipelineAvailable(available)
    }

    fun onThermalLimited(limited: Boolean) {
        adapter.onThermalLimited(limited)
    }

    fun onRefreshRate(refreshRateHz: Float) {
        budget = PictureFrameBudget(refreshRateHz)
    }

    fun recordPerformance(sample: PicturePerformanceSample) {
        telemetry = telemetry.record(sample, budget)
        adapter.onPerformance(telemetry.latestState)
    }

    fun resetForVideo(
        selected: PictureContent,
        detected: PictureContent,
    ) {
        telemetry = PictureRuntimeTelemetry()
        adapter.resetForVideo(selected, detected)
    }
}
