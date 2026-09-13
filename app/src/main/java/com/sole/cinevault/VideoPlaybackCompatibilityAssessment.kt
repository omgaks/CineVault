package com.sole.cinevault

enum class VideoCompatibilityRisk {
    LOW,
    ELEVATED,
    HIGH,
    UNKNOWN,
}

enum class VideoDecoderRecommendation {
    PREFER_HARDWARE,
    WATCH_NATIVE_CLOSELY,
    SOFTWARE_REQUIRED,
    UNKNOWN,
}

enum class VideoCompatibilityFactor {
    NO_FULL_HARDWARE_DECODER,
    SOFTWARE_DECODER_AVAILABLE,
    FUNCTIONAL_ONLY_DECODER,
    ULTRA_HIGH_RESOLUTION,
    HIGH_FRAME_RATE,
    HDR,
}

data class VideoPlaybackCompatibilityAssessment(
    val risk: VideoCompatibilityRisk,
    val recommendation: VideoDecoderRecommendation,
    val factors: Set<VideoCompatibilityFactor>,
)

/**
 * Pure decision layer that combines the exact MediaCodec capability report
 * with the selected stream profile.
 *
 * Important: "ELEVATED" does NOT mean "force software".
 * It means native playback is allowed, but CineVault should watch runtime
 * health signals more closely. Hardware remains the preferred path whenever
 * it fully supports the selected stream.
 */
fun assessVideoPlaybackCompatibility(
    report: VideoDecoderCapabilityReport?,
): VideoPlaybackCompatibilityAssessment {
    if (
        report == null ||
        report.status == VideoDecoderCapabilityStatus.UNKNOWN
    ) {
        return VideoPlaybackCompatibilityAssessment(
            risk = VideoCompatibilityRisk.UNKNOWN,
            recommendation = VideoDecoderRecommendation.UNKNOWN,
            factors = emptySet(),
        )
    }

    val hasFullHardwareDecoder =
        report.decoders.any {
            it.hardwareAccelerated && it.formatSupported
        }

    val hasFullSoftwareDecoder =
        report.decoders.any {
            it.softwareOnly && it.formatSupported
        }

    val hasFunctionalOnlyDecoder =
        report.decoders.any {
            !it.formatSupported && it.functionallySupported
        }

    val profile = report.streamProfile

    val ultraHighResolution =
        profile?.let {
            (it.width ?: 0) >= 3840 ||
                (it.height ?: 0) >= 2160
        } == true

    val highFrameRate =
        (profile?.frameRate ?: 0f) >= 50f

    val hdr =
        profile?.dynamicRange == VideoDynamicRange.HDR10_OR_PQ ||
            profile?.dynamicRange == VideoDynamicRange.HLG

    val factors = buildSet {
        if (!hasFullHardwareDecoder) {
            add(VideoCompatibilityFactor.NO_FULL_HARDWARE_DECODER)
        }
        if (hasFullSoftwareDecoder) {
            add(VideoCompatibilityFactor.SOFTWARE_DECODER_AVAILABLE)
        }
        if (hasFunctionalOnlyDecoder) {
            add(VideoCompatibilityFactor.FUNCTIONAL_ONLY_DECODER)
        }
        if (ultraHighResolution) {
            add(VideoCompatibilityFactor.ULTRA_HIGH_RESOLUTION)
        }
        if (highFrameRate) {
            add(VideoCompatibilityFactor.HIGH_FRAME_RATE)
        }
        if (hdr) {
            add(VideoCompatibilityFactor.HDR)
        }
    }

    if (!hasFullHardwareDecoder && hasFullSoftwareDecoder) {
        return VideoPlaybackCompatibilityAssessment(
            risk = VideoCompatibilityRisk.HIGH,
            recommendation = VideoDecoderRecommendation.SOFTWARE_REQUIRED,
            factors = factors,
        )
    }

    if (!hasFullHardwareDecoder) {
        return VideoPlaybackCompatibilityAssessment(
            risk = VideoCompatibilityRisk.HIGH,
            recommendation = VideoDecoderRecommendation.WATCH_NATIVE_CLOSELY,
            factors = factors,
        )
    }

    val demandingStream =
        ultraHighResolution ||
            highFrameRate ||
            hdr ||
            hasFunctionalOnlyDecoder

    return if (demandingStream) {
        VideoPlaybackCompatibilityAssessment(
            risk = VideoCompatibilityRisk.ELEVATED,
            recommendation = VideoDecoderRecommendation.WATCH_NATIVE_CLOSELY,
            factors = factors,
        )
    } else {
        VideoPlaybackCompatibilityAssessment(
            risk = VideoCompatibilityRisk.LOW,
            recommendation = VideoDecoderRecommendation.PREFER_HARDWARE,
            factors = factors,
        )
    }
}
