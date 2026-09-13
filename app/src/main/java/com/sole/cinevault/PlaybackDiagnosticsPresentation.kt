package com.sole.cinevault

data class PlaybackDiagnosticsPresentation(
    val videoSummary: String,
    val decoderSummary: String,
    val compatibilitySummary: String,
    val fallbackSummary: String?,
)

fun presentPlaybackDiagnostics(
    snapshot: PlaybackDiagnosticsSnapshot,
): PlaybackDiagnosticsPresentation {
    val videoParts = buildList {
        friendlyVideoCodecLabel(snapshot.mimeType, snapshot.codecString)
            ?.let(::add)

        if (snapshot.resolution != "Unknown") {
            add(snapshot.resolution)
        }

        friendlyDynamicRangeLabel(snapshot.dynamicRange)
            ?.let(::add)

        snapshot.frameRate
            ?.let(::formatFrameRate)
            ?.let(::add)
    }

    val decoderMode = when (snapshot.activeDecoderKind) {
        ActiveVideoDecoderKind.HARDWARE -> "HW"
        ActiveVideoDecoderKind.SOFTWARE -> "SW"
        ActiveVideoDecoderKind.UNKNOWN -> when (snapshot.decoderMode) {
            PlaybackEngineMode.HARDWARE -> "HW"
            PlaybackEngineMode.SOFTWARE -> "SW"
        }
    }

    val decoderSummary = listOfNotNull(
        decoderMode,
        snapshot.decoderName?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    val compatibilitySummary = when (snapshot.compatibilityRisk) {
        VideoCompatibilityRisk.LOW -> "Compatibility: Low risk"
        VideoCompatibilityRisk.ELEVATED -> "Compatibility: Elevated risk"
        VideoCompatibilityRisk.HIGH -> "Compatibility: High risk"
        VideoCompatibilityRisk.UNKNOWN -> "Compatibility: Unknown"
    }

    val fallbackSummary = when {
        !snapshot.fallbackOccurred -> null
        snapshot.fallbackReason != null ->
            "Fallback: ${playbackFallbackReasonLabel(snapshot.fallbackReason)}"
        else -> "Fallback: Software decoding"
    }

    return PlaybackDiagnosticsPresentation(
        videoSummary = videoParts
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
            ?: "Video stream unknown",
        decoderSummary = decoderSummary,
        compatibilitySummary = compatibilitySummary,
        fallbackSummary = fallbackSummary,
    )
}

fun friendlyVideoCodecLabel(
    mimeType: String?,
    codecString: String?,
): String? {
    val normalizedMime = mimeType?.lowercase()
    val normalizedCodec = codecString?.lowercase()

    return when {
        normalizedMime == "video/hevc" ||
            normalizedMime == "video/h265" ||
            normalizedCodec?.startsWith("hvc1") == true ||
            normalizedCodec?.startsWith("hev1") == true -> "HEVC"

        normalizedMime == "video/avc" ||
            normalizedMime == "video/h264" ||
            normalizedCodec?.startsWith("avc1") == true ||
            normalizedCodec?.startsWith("avc3") == true -> "H.264"

        normalizedMime == "video/av01" ||
            normalizedCodec?.startsWith("av01") == true -> "AV1"

        normalizedMime == "video/x-vnd.on2.vp9" ||
            normalizedMime == "video/vp9" ||
            normalizedCodec?.startsWith("vp09") == true -> "VP9"

        normalizedMime == "video/x-vnd.on2.vp8" ||
            normalizedMime == "video/vp8" ||
            normalizedCodec?.startsWith("vp08") == true -> "VP8"

        !mimeType.isNullOrBlank() ->
            mimeType.substringAfter('/').uppercase()

        !codecString.isNullOrBlank() -> codecString
        else -> null
    }
}

fun friendlyDynamicRangeLabel(
    dynamicRange: VideoDynamicRange,
): String? = when (dynamicRange) {
    VideoDynamicRange.SDR -> "SDR"
    VideoDynamicRange.HDR10_OR_PQ -> "HDR10/PQ"
    VideoDynamicRange.HLG -> "HLG"
    VideoDynamicRange.UNKNOWN -> null
}

fun formatFrameRate(frameRate: Float): String {
    val rounded = kotlin.math.round(frameRate)
    val text = if (kotlin.math.abs(frameRate - rounded) < 0.005f) {
        rounded.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", frameRate)
            .trimEnd('0')
            .trimEnd('.')
    }
    return "$text fps"
}
