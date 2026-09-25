package com.sole.cinevault.glasses.stereo

internal enum class StereoPlaybackStatusKind { TWO_D, SBS_READY, TOP_BOTTOM_UNSUPPORTED }

internal data class StereoPlaybackStatus(
    val kind: StereoPlaybackStatusKind,
    val compactLabel: String?,
    val guidance: String?,
)

internal object StereoPlaybackStatusResolver {
    fun resolve(plan: ExternalStereoRenderPlan): StereoPlaybackStatus = when {
        plan.mode == StereoPlaybackMode.SIDE_BY_SIDE && plan.supportedByNativeSbsOutput ->
            StereoPlaybackStatus(
                StereoPlaybackStatusKind.SBS_READY,
                "3D • SBS",
                if (plan.requiresHardware3dMode) "Enable 3D mode on the connected glasses." else null,
            )
        plan.mode == StereoPlaybackMode.TOP_BOTTOM && !plan.supportedByNativeSbsOutput ->
            StereoPlaybackStatus(
                StereoPlaybackStatusKind.TOP_BOTTOM_UNSUPPORTED,
                "3D • TAB",
                "Top/Bottom requires conversion to SBS.",
            )
        else -> StereoPlaybackStatus(StereoPlaybackStatusKind.TWO_D, null, null)
    }
}
