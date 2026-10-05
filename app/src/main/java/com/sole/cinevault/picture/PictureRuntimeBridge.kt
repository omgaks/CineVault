package com.sole.cinevault.picture

/**
 * P1 bridge between the existing V1 controller inputs and the new architecture.
 *
 * This remains pure Kotlin: it does not own Media3, shader installation or UI state.
 * The controller can feed its already-known facts into this bridge while keeping
 * the proven V1 rendering path untouched.
 */
data class PictureRuntimeInputs(
    val selectedContent: PictureContent,
    val detectedContent: PictureContent,
    val requestedTier: PictureQualityTier = PictureQualityTier.ECO,
    val hdr: Boolean = false,
    val dolbyVision: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val effectPipelineAvailable: Boolean = true,
    val thermalLimited: Boolean = false,
    val performance: PicturePerformanceState = PicturePerformanceState.HEALTHY,
)

object PictureRuntimeBridge {

    fun snapshot(inputs: PictureRuntimeInputs): PictureRuntimeState =
        PictureRuntimeStateFactory.create(
            selectedContent = inputs.selectedContent,
            detectedContent = inputs.detectedContent,
            requestedTier = inputs.requestedTier,
            capabilities = PictureRuntimeCapabilities(
                hdr = inputs.hdr,
                dolbyVision = inputs.dolbyVision,
                width = inputs.width,
                height = inputs.height,
                effectPipelineAvailable = inputs.effectPipelineAvailable,
                thermalLimited = inputs.thermalLimited,
            ),
            performance = inputs.performance,
        )
}
