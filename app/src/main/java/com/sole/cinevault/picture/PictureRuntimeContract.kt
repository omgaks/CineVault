package com.sole.cinevault.picture

/**
 * Stable read-only contract for exposing P1 architecture state to UI/debug surfaces.
 *
 * Keeping the contract tiny prevents future panels from reaching into the Media3
 * controller internals and gives P2 one safe source of truth.
 */
interface PictureRuntimeContract {
    val runtimeState: PictureRuntimeState
}

/**
 * Safe initial state for a controller before video format metadata is available.
 * It deliberately describes an SDR/unknown-size V1 foundation and performs no work.
 */
fun initialPictureRuntimeState(
    selectedContent: PictureContent = PictureContent.AUTO,
    detectedContent: PictureContent = PictureContent.FILM,
    tier: PictureQualityTier = PictureQualityTier.ECO,
): PictureRuntimeState =
    PictureRuntimeBridge.snapshot(
        PictureRuntimeInputs(
            selectedContent = selectedContent,
            detectedContent = detectedContent,
            requestedTier = tier,
        )
    )
