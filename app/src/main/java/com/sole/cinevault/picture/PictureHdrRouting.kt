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
        // Explicit HDR evidence always wins, including contradictory SDR transfer tags.
        // Unknown HEVC/VP9/AV1 remains protected until metadata confirms SDR.
        val hdrSignalled = colorInfo?.hdrStaticInfo != null ||
            colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 ||
            colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG ||
            colorInfo?.colorSpace == C.COLOR_SPACE_BT2020
        val transfer = when {
            hdrSignalled -> PictureHdrPolicy.Transfer.PQ
            colorInfo?.colorTransfer == C.COLOR_TRANSFER_SDR ||
                colorInfo?.colorTransfer == C.COLOR_TRANSFER_LINEAR ->
                PictureHdrPolicy.Transfer.SDR
            // BT.601/BT.709 are SDR colour spaces even if transfer is omitted.
            // Never use this fallback for explicitly HDR-signalled material.
            (colorInfo?.colorSpace == C.COLOR_SPACE_BT601 ||
                colorInfo?.colorSpace == C.COLOR_SPACE_BT709) &&
                format.sampleMimeType != MimeTypes.VIDEO_DOLBY_VISION ->
                PictureHdrPolicy.Transfer.SDR
            // Common metadata-light AVC files are SDR-compatible, unless HDR is signalled.
            // This is a conservative compatibility heuristic, not proof of SDR.
            format.sampleMimeType == MimeTypes.VIDEO_H264 &&
                (format.codecs == null || !format.codecs!!.contains("hvc", ignoreCase = true)) ->
                PictureHdrPolicy.Transfer.SDR
            else -> PictureHdrPolicy.Transfer.UNKNOWN
        }
        // Some containers expose Dolby Vision as HEVC with a DV codec identifier.
        // Protect these even when the transfer is absent or nominally SDR.
        val codecId = format.codecs.orEmpty().lowercase()
        val dolbyVision = format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION ||
            codecId.split(',').any { token ->
                val id = token.trim()
                id.startsWith("dvh1") || id.startsWith("dvhe") ||
                    id.startsWith("dvc1") || id.startsWith("dvav")
            }
        val kind = if (dolbyVision)
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
