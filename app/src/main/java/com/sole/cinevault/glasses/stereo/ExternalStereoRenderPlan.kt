package com.sole.cinevault.glasses.stereo

/**
 * Render instructions for RayNeo-style native stereo external displays.
 *
 * Native RayNeo 3D expects a full SBS desktop/frame. CineVault therefore keeps
 * one canonical PlayerView and changes only how that view fits the external
 * destination. The glasses remain responsible for splitting the full SBS frame
 * into left/right eyes after the user enables their hardware 3D mode.
 */
internal data class ExternalStereoRenderPlan(
    val mode: StereoPlaybackMode,
    val forceFillExternalFrame: Boolean,
    val requiresHardware3dMode: Boolean,
    val supportedByNativeSbsOutput: Boolean,
)

internal object ExternalStereoRenderPlanner {
    fun plan(
        decision: StereoPlaybackDecision,
        externalDisplayDestination: Boolean,
    ): ExternalStereoRenderPlan {
        if (!externalDisplayDestination || decision.mode == StereoPlaybackMode.NORMAL_2D) {
            return ExternalStereoRenderPlan(
                mode = StereoPlaybackMode.NORMAL_2D,
                forceFillExternalFrame = false,
                requiresHardware3dMode = false,
                supportedByNativeSbsOutput = true,
            )
        }

        return when (decision.mode) {
            StereoPlaybackMode.SIDE_BY_SIDE ->
                ExternalStereoRenderPlan(
                    mode = StereoPlaybackMode.SIDE_BY_SIDE,
                    // Full-SBS sources already match the native wide frame.
                    // Half-SBS sources need expansion across that same frame.
                    forceFillExternalFrame = true,
                    requiresHardware3dMode = true,
                    supportedByNativeSbsOutput = true,
                )

            StereoPlaybackMode.TOP_BOTTOM ->
                ExternalStereoRenderPlan(
                    mode = StereoPlaybackMode.TOP_BOTTOM,
                    forceFillExternalFrame = false,
                    requiresHardware3dMode = true,
                    // Native RayNeo input is SBS. TAB needs a later conversion
                    // stage; never pretend it is correct by stretching it.
                    supportedByNativeSbsOutput = false,
                )

            StereoPlaybackMode.NORMAL_2D ->
                ExternalStereoRenderPlan(
                    mode = StereoPlaybackMode.NORMAL_2D,
                    forceFillExternalFrame = false,
                    requiresHardware3dMode = false,
                    supportedByNativeSbsOutput = true,
                )
        }
    }
}
