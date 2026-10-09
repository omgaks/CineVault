package com.sole.cinevault.picture

/** Fail-closed production rollout until hardware and fallback are verified. */
object PictureHdrRolloutPolicy {
    fun canActivateHdrGpu(
        stage: PictureHdrGpuContract.Stage,
        supportsHdr: Boolean,
        recoveryVerified: Boolean,
        transfer: PictureHdrPolicy.Transfer,
        format: PictureHdrPolicy.Format,
    ): Boolean = PictureHdrPlaybackGate.plan(
        transfer, format, stage, supportsHdr, recoveryVerified,
    ).action == PictureHdrPlaybackGate.Action.HDR_IDENTITY_EFFECT
}
