package com.sole.cinevault.picture

/**
 * P8-S6: single decision point for HDR effect rollout.
 * The isolated S5 identity shader is not device verified, so production always
 * uses the existing HDR passthrough path. No effect list is mutated here.
 */
object PictureHdrPlaybackGate {
    enum class Action { SDR_EFFECT, HDR_PASSTHROUGH, HDR_IDENTITY_EFFECT }

    data class Plan(val action: Action, val reason: String)

    fun plan(
        transfer: PictureHdrPolicy.Transfer,
        format: PictureHdrPolicy.Format,
        stage: PictureHdrGpuContract.Stage = PictureHdrGpuContract.Stage.ISOLATED_GPU_IDENTITY,
        deviceSupportsHdrProcessing: Boolean = false,
        playbackRecoveryReady: Boolean = false,
    ): Plan {
        val policy = PictureHdrPolicy.decide(PictureHdrPolicy.Input(transfer, format))
        if (policy.route == PictureHdrPolicy.Route.SDR_ENHANCEMENT) {
            return Plan(Action.SDR_EFFECT, "Existing SDR enhancement")
        }
        val canInstall = deviceSupportsHdrProcessing && playbackRecoveryReady &&
            PictureHdrGpuContract.canInstallForPlayback(transfer, format, stage)
        return if (canInstall) {
            Plan(Action.HDR_IDENTITY_EFFECT, "Verified HDR identity effect eligible")
        } else {
            Plan(Action.HDR_PASSTHROUGH, "Preserve original HDR or unknown-transfer playback")
        }
    }
}
