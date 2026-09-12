package com.sole.cinevault

enum class NativeVideoPlaybackReadiness {
    READY,
    MARGINAL,
    SOFTWARE_FALLBACK_NEEDED,
    UNKNOWN,
}

fun decideNativeVideoPlaybackReadiness(
    report: VideoDecoderCapabilityReport?,
): NativeVideoPlaybackReadiness {
    if (report == null || report.status == VideoDecoderCapabilityStatus.UNKNOWN) {
        return NativeVideoPlaybackReadiness.UNKNOWN
    }

    val hasSupportedHardware = report.decoders.any { decoder ->
        decoder.hardwareAccelerated && decoder.formatSupported
    }
    if (hasSupportedHardware) {
        return NativeVideoPlaybackReadiness.READY
    }

    val hasSupportedSoftware = report.decoders.any { decoder ->
        decoder.softwareOnly && decoder.formatSupported
    }
    if (hasSupportedSoftware) {
        return NativeVideoPlaybackReadiness.SOFTWARE_FALLBACK_NEEDED
    }

    val hasFunctionalDecoder = report.decoders.any { decoder ->
        decoder.functionallySupported
    }
    if (hasFunctionalDecoder || report.status == VideoDecoderCapabilityStatus.FUNCTIONAL_ONLY) {
        return NativeVideoPlaybackReadiness.MARGINAL
    }

    return NativeVideoPlaybackReadiness.SOFTWARE_FALLBACK_NEEDED
}
