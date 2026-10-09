package com.sole.cinevault.picture

/**
 * P8-S5 rollout contract: GPU identity pass exists, but live HDR routing stays
 * disabled until hardware HDR10/HLG validation and safe fallback are completed.
 */
object PictureHdrGpuContract {
    enum class Stage { ISOLATED_GPU_IDENTITY, DEVICE_VERIFIED }

    fun canInstallForPlayback(
        transfer: PictureHdrPolicy.Transfer,
        format: PictureHdrPolicy.Format,
        stage: Stage = Stage.ISOLATED_GPU_IDENTITY,
    ): Boolean = stage == Stage.DEVICE_VERIFIED &&
        format == PictureHdrPolicy.Format.STANDARD &&
        (transfer == PictureHdrPolicy.Transfer.PQ || transfer == PictureHdrPolicy.Transfer.HLG)
}
