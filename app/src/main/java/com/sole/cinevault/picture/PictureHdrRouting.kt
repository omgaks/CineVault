package com.sole.cinevault.picture

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/** Media3 -> P8 policy adapter. Production HDR GPU remains unverified and disabled. */
object PictureHdrRouting {
    /** Explains why the current local video is protected, without treating missing metadata as SDR. */
    @OptIn(UnstableApi::class)
    fun blockReason(format: Format): String {
        val info = format.colorInfo
        val codec = format.sampleMimeType ?: "unknown codec"
        val transfer = when (info?.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> "PQ HDR"
            C.COLOR_TRANSFER_HLG -> "HLG HDR"
            C.COLOR_TRANSFER_SDR -> "SDR"
            else -> "unknown"
        }
        val colourSpace = when (info?.colorSpace) {
            C.COLOR_SPACE_BT709 -> "BT.709"
            C.COLOR_SPACE_BT2020 -> "BT.2020"
            C.COLOR_SPACE_BT601 -> "BT.601"
            else -> "unknown"
        }
        return "Enhancement protected: $codec, transfer $transfer, colour $colourSpace"
    }

    @OptIn(UnstableApi::class)
    fun decide(format: Format): PictureHdrPolicy.Decision {
        val colorInfo = format.colorInfo
        // Missing transfer alone is not proof of SDR. However, an 8-bit AVC
        // stream explicitly marked BT.709 with no HDR static metadata is an
        // SDR-compatible case we can handle without guessing from resolution.
        val knownSdrAvc = format.sampleMimeType == MimeTypes.VIDEO_H264 &&
            colorInfo?.colorSpace == C.COLOR_SPACE_BT709 &&
            colorInfo.hdrStaticInfo == null &&
            (format.codecs == null || !format.codecs!!.contains("hvc", ignoreCase = true))
        val transfer = when (colorInfo?.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> PictureHdrPolicy.Transfer.PQ
            C.COLOR_TRANSFER_HLG -> PictureHdrPolicy.Transfer.HLG
            C.COLOR_TRANSFER_SDR -> PictureHdrPolicy.Transfer.SDR
            else -> if (knownSdrAvc) PictureHdrPolicy.Transfer.SDR else PictureHdrPolicy.Transfer.UNKNOWN
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
