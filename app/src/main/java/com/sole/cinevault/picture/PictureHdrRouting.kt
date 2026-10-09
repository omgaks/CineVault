package com.sole.cinevault.picture

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/** Media3 -> P8 policy adapter. Production HDR GPU remains unverified and disabled. */
object PictureHdrRouting {
    @OptIn(UnstableApi::class)
    fun decide(format: Format): PictureHdrPolicy.Decision {
        val colorInfo = format.colorInfo
        val transfer = if (colorInfo == null) PictureHdrPolicy.Transfer.SDR else when (colorInfo.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> PictureHdrPolicy.Transfer.PQ
            C.COLOR_TRANSFER_HLG -> PictureHdrPolicy.Transfer.HLG
            C.COLOR_TRANSFER_SDR -> PictureHdrPolicy.Transfer.SDR
            else -> PictureHdrPolicy.Transfer.UNKNOWN
        }
        val kind = if (format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION)
            PictureHdrPolicy.Format.DOLBY_VISION else PictureHdrPolicy.Format.STANDARD

        // P8-S7: The existing PictureController already calls decide() when video tracks change.
        // Route through the S6 playback gate while retaining the original controller contract.
        // No device-verified stage or recovery capability is asserted by this adapter.
        val plan = PictureHdrPlaybackGate.plan(transfer, kind)
        val policy = PictureHdrPolicy.decide(PictureHdrPolicy.Input(transfer, kind))
        return when (plan.action) {
            PictureHdrPlaybackGate.Action.SDR_EFFECT -> policy
            PictureHdrPlaybackGate.Action.HDR_PASSTHROUGH -> policy
            PictureHdrPlaybackGate.Action.HDR_IDENTITY_EFFECT ->
                // Fail closed: controller does not yet support HDR identity effect installation.
                PictureHdrPolicy.Decision(
                    PictureHdrPolicy.Route.HDR_PASSTHROUGH,
                    policy.hdrDetected,
                    "HDR GPU integration pending device verification",
                )
        }
    }
}
