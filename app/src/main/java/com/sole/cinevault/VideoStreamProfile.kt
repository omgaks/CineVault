package com.sole.cinevault

import androidx.media3.common.C
import androidx.media3.common.Format

enum class VideoDynamicRange {
    SDR,
    HDR10_OR_PQ,
    HLG,
    UNKNOWN,
}

data class VideoStreamProfile(
    val mimeType: String?,
    val codecString: String?,
    val width: Int?,
    val height: Int?,
    val frameRate: Float?,
    val dynamicRange: VideoDynamicRange,
) {
    val pixelCount: Long?
        get() = if (width != null && height != null) {
            width.toLong() * height.toLong()
        } else {
            null
        }

    val resolutionLabel: String
        get() = when {
            width == null || height == null -> "Unknown"
            width >= 7680 || height >= 4320 -> "8K"
            width >= 3840 || height >= 2160 -> "4K"
            width >= 2560 || height >= 1440 -> "1440p"
            width >= 1920 || height >= 1080 -> "1080p"
            width >= 1280 || height >= 720 -> "720p"
            else -> "${width}x${height}"
        }
}

fun classifyVideoDynamicRange(
    colorTransfer: Int?,
): VideoDynamicRange {
    return when (colorTransfer) {
        C.COLOR_TRANSFER_ST2084 -> VideoDynamicRange.HDR10_OR_PQ
        C.COLOR_TRANSFER_HLG -> VideoDynamicRange.HLG
        C.COLOR_TRANSFER_SDR,
        C.COLOR_TRANSFER_LINEAR -> VideoDynamicRange.SDR
        null -> VideoDynamicRange.UNKNOWN
        else -> VideoDynamicRange.UNKNOWN
    }
}

fun buildVideoStreamProfile(
    format: Format,
): VideoStreamProfile {
    val width = format.width.takeIf { it > 0 }
    val height = format.height.takeIf { it > 0 }
    val frameRate = format.frameRate.takeIf { it > 0f }

    return VideoStreamProfile(
        mimeType = format.sampleMimeType,
        codecString = format.codecs,
        width = width,
        height = height,
        frameRate = frameRate,
        dynamicRange = classifyVideoDynamicRange(
            format.colorInfo?.colorTransfer,
        ),
    )
}
