package com.sole.cinevault.picture

/** Device-verified HDR processing is never enabled implicitly. */
object PictureHdrRolloutPolicy {
    fun canActivateHdrGpu(
        stage: PictureHdrGpuContract.Stage,
        deviceSupportsHdrProcessing: Boolean,
        playbackRecoveryReady: Boolean,
        transfer: PictureHdrPolicy.Transfer,
        format: PictureHdrPolicy.Format,
    ): Boolean =
        deviceSupportsHdrProcessing &&
            playbackRecoveryReady &&
            PictureHdrGpuContract.canInstallForPlayback(transfer, format, stage)
}
