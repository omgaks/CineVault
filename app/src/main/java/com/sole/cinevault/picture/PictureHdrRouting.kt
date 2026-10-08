package com.sole.cinevault.picture

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/**
 * Media3 metadata adapter for P8 HDR policy. No GL pipeline is created here.
 * Explicit unknown colour transfer is protected; absent metadata retains legacy SDR behavior.
 */
object PictureHdrRouting {
    @OptIn(UnstableApi::class)
    fun decide(format: Format): PictureHdrPolicy.Decision {
        // Store the external nullable property once; Kotlin cannot smart-cast repeated accesses.
        val colorInfo = format.colorInfo
        val transfer = if (colorInfo == null) PictureHdrPolicy.Transfer.SDR else when (colorInfo.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> PictureHdrPolicy.Transfer.PQ
            C.COLOR_TRANSFER_HLG -> PictureHdrPolicy.Transfer.HLG
            C.COLOR_TRANSFER_SDR -> PictureHdrPolicy.Transfer.SDR
            else -> PictureHdrPolicy.Transfer.UNKNOWN
        }
        val kind = if (format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION)
            PictureHdrPolicy.Format.DOLBY_VISION else PictureHdrPolicy.Format.STANDARD
        return PictureHdrPolicy.decide(PictureHdrPolicy.Input(transfer, kind))
    }
}
