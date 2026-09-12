package com.sole.cinevault

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

enum class VideoDecoderCapabilityStatus {
    SUPPORTED,
    FUNCTIONAL_ONLY,
    NO_COMPATIBLE_DECODER,
    UNKNOWN,
}

data class VideoDecoderCandidate(
    val name: String,
    val hardwareAccelerated: Boolean,
    val softwareOnly: Boolean,
    val formatSupported: Boolean,
    val functionallySupported: Boolean,
)

data class VideoDecoderCapabilityReport(
    val mimeType: String?,
    val status: VideoDecoderCapabilityStatus,
    val decoders: List<VideoDecoderCandidate>,
    val queryError: String? = null,
    val streamProfile: VideoStreamProfile? = null,
) {
    val hasHardwareDecoder: Boolean
        get() = decoders.any { it.hardwareAccelerated && it.formatSupported }

    val hasPlatformSoftwareDecoder: Boolean
        get() = decoders.any { it.softwareOnly && it.formatSupported }
}

/**
 * Pure summary step kept separate so our capability policy is unit-testable
 * without depending on an Android device codec list.
 */
fun summarizeVideoDecoderCapability(
    candidates: List<VideoDecoderCandidate>,
): VideoDecoderCapabilityStatus {
    if (candidates.isEmpty()) {
        return VideoDecoderCapabilityStatus.NO_COMPATIBLE_DECODER
    }

    if (candidates.any { it.formatSupported }) {
        return VideoDecoderCapabilityStatus.SUPPORTED
    }

    if (candidates.any { it.functionallySupported }) {
        return VideoDecoderCapabilityStatus.FUNCTIONAL_ONLY
    }

    return VideoDecoderCapabilityStatus.NO_COMPATIBLE_DECODER
}

/**
 * Playback Resilience Phase 1 capability probe.
 *
 * This asks Android's MediaCodec layer what it can actually decode for the
 * selected video Format instead of assuming support from the file extension.
 *
 * FUNCTIONAL_ONLY means a decoder may understand the stream but Media3 does
 * not consider the full format performantly supported (for example the exact
 * profile/level/resolution/frame-rate combination). We still allow the native
 * tier to try it, but this becomes useful evidence for choosing software
 * fallback later.
 */
@OptIn(UnstableApi::class)
fun inspectVideoDecoderCapability(
    format: Format,
): VideoDecoderCapabilityReport {
    val streamProfile = buildVideoStreamProfile(format)
    val mimeType = format.sampleMimeType
        ?: return VideoDecoderCapabilityReport(
            mimeType = null,
            status = VideoDecoderCapabilityStatus.UNKNOWN,
            decoders = emptyList(),
            streamProfile = streamProfile,
        )

    return try {
        val decoderInfos = MediaCodecSelector.DEFAULT.getDecoderInfos(
            mimeType,
            /* requiresSecureDecoder = */ false,
            /* requiresTunnelingDecoder = */ false,
        )

        val candidates = decoderInfos.map { info ->
            VideoDecoderCandidate(
                name = info.name,
                hardwareAccelerated = info.hardwareAccelerated,
                softwareOnly = info.softwareOnly,
                formatSupported = info.isFormatSupported(format),
                functionallySupported = info.isFormatFunctionallySupported(format),
            )
        }

        VideoDecoderCapabilityReport(
            mimeType = mimeType,
            status = summarizeVideoDecoderCapability(candidates),
            decoders = candidates,
            streamProfile = streamProfile,
        )
    } catch (error: MediaCodecUtil.DecoderQueryException) {
        VideoDecoderCapabilityReport(
            mimeType = mimeType,
            status = VideoDecoderCapabilityStatus.UNKNOWN,
            decoders = emptyList(),
            queryError = error.message ?: error.javaClass.simpleName,
            streamProfile = streamProfile,
        )
    }
}
