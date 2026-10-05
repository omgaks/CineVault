package com.sole.cinevault.picture

/**
 * P1 integration seam for PictureEnhanceController.
 *
 * The proven V1 controller remains the owner of Media3 and shader installation.
 * This adapter mirrors controller facts into the new architecture without making
 * rendering decisions or changing any shader parameter.
 */
class PictureControllerRuntimeAdapter(
    initialSelectedContent: PictureContent = PictureContent.AUTO,
    initialDetectedContent: PictureContent = PictureContent.FILM,
    initialTier: PictureQualityTier = PictureQualityTier.ECO,
) : PictureRuntimeContract {

    private var selectedContent = initialSelectedContent
    private var detectedContent = initialDetectedContent
    private var requestedTier = initialTier
    private var hdr = false
    private var dolbyVision = false
    private var width = 0
    private var height = 0
    private var effectPipelineAvailable = true
    private var thermalLimited = false
    private var performance = PicturePerformanceState.HEALTHY

    override var runtimeState: PictureRuntimeState = rebuild()
        private set

    fun onContent(
        selected: PictureContent,
        detected: PictureContent,
    ) {
        selectedContent = selected
        detectedContent = detected
        publish()
    }

    fun onVideoFormat(
        width: Int,
        height: Int,
        hdr: Boolean,
        dolbyVision: Boolean,
    ) {
        this.width = width.coerceAtLeast(0)
        this.height = height.coerceAtLeast(0)
        this.hdr = hdr
        this.dolbyVision = dolbyVision
        publish()
    }

    fun onRequestedTier(tier: PictureQualityTier) {
        requestedTier = tier
        publish()
    }

    fun onEffectPipelineAvailable(available: Boolean) {
        effectPipelineAvailable = available
        publish()
    }

    fun onThermalLimited(limited: Boolean) {
        thermalLimited = limited
        publish()
    }

    fun onPerformance(state: PicturePerformanceState) {
        performance = state
        publish()
    }

    fun resetForVideo(
        selected: PictureContent,
        detected: PictureContent,
    ) {
        selectedContent = selected
        detectedContent = detected
        hdr = false
        dolbyVision = false
        width = 0
        height = 0
        effectPipelineAvailable = true
        thermalLimited = false
        performance = PicturePerformanceState.HEALTHY
        publish()
    }

    private fun publish() {
        runtimeState = rebuild()
    }

    private fun rebuild(): PictureRuntimeState =
        PictureRuntimeBridge.snapshot(
            PictureRuntimeInputs(
                selectedContent = selectedContent,
                detectedContent = detectedContent,
                requestedTier = requestedTier,
                hdr = hdr,
                dolbyVision = dolbyVision,
                width = width,
                height = height,
                effectPipelineAvailable = effectPipelineAvailable,
                thermalLimited = thermalLimited,
                performance = performance,
            )
        )
}
