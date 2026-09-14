package com.sole.cinevault

data class PlaybackDiagnosticsPresentation(
    val videoSummary: String,
    val codecDetailsSummary: String?,
    val decoderSummary: String,
    val compatibilitySummary: String,
    val fallbackSummary: String?,
    val audioSummary: String? = null,
    val audioDecoderSummary: String? = null,
    val audioResilienceSummary: String? = null,
    val pipelineSummary: String? = null,
)

fun presentPlaybackDiagnostics(
    snapshot: PlaybackDiagnosticsSnapshot,
): PlaybackDiagnosticsPresentation {
    val codecDetails = parseVideoCodecDetails(
        mimeType = snapshot.mimeType,
        codecString = snapshot.codecString,
    )

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

    val selectedAudio = snapshot.audioMimeType?.let {
        PlaybackStreamDescriptor(
            kind = PlaybackStreamKind.AUDIO,
            mimeType = snapshot.audioMimeType,
            codecString = snapshot.audioCodecString,
            language = snapshot.audioLanguage,
            selected = true,
        )
    }

    val audioAssessment = assessAudioStreamCapability(selectedAudio)

    val audioSummary = audioAssessment?.let { assessment ->
        listOfNotNull(
            assessment.codecLabel,
            snapshot.audioLanguage
                ?.takeIf { it.isNotBlank() }
                ?.uppercase(),
        ).joinToString(" · ")
    }

    val audioDecoderSummary = when {
        snapshot.audioDecoderName.isNullOrBlank() &&
            snapshot.activeAudioDecoderKind == ActiveAudioDecoderKind.UNKNOWN ->
            null

        else -> {
            val kindLabel = when (snapshot.activeAudioDecoderKind) {
                ActiveAudioDecoderKind.PLATFORM -> "Platform"
                ActiveAudioDecoderKind.FFMPEG -> "FFmpeg"
                ActiveAudioDecoderKind.UNKNOWN -> "Unknown"
            }

            listOfNotNull(
                kindLabel,
                snapshot.audioDecoderName?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        }
    }

    val audioResilience = assessAudioPlaybackResilience(
        selectedAudio = selectedAudio,
        activeDecoder = ActiveAudioDecoderStatus(
            kind = snapshot.activeAudioDecoderKind,
            decoderName = snapshot.audioDecoderName,
        ),
    )

    val audioResilienceSummary =
        if (audioResilience.readiness == AudioPlaybackReadiness.NONE) {
            null
        } else {
            audioPlaybackReadinessLabel(audioResilience.readiness)
        }

    val pipelineSummary = when {
        snapshot.mixedPipeline ->
            buildString {
                append("Mixed pipeline")
                when (snapshot.audioRoute) {
                    PlaybackStreamRoute.AUDIO_FFMPEG_RESCUE_CANDIDATE ->
                        append(" · audio rescue candidate")
                    PlaybackStreamRoute.AUDIO_PLATFORM_OR_FFMPEG_EXTENSION ->
                        append(" · independent audio lane")
                    else -> Unit
                }
            }

        snapshot.audioRoute != null ->
            when (snapshot.audioRoute) {
                PlaybackStreamRoute.AUDIO_FFMPEG_RESCUE_CANDIDATE ->
                    "Audio rescue candidate"
                PlaybackStreamRoute.AUDIO_PLATFORM_OR_FFMPEG_EXTENSION ->
                    "Independent audio lane"
                else -> null
            }

        else -> null
    }

    return PlaybackDiagnosticsPresentation(
        videoSummary = videoParts
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
            ?: "Video stream unknown",
        codecDetailsSummary = formatVideoCodecDetails(codecDetails),
        decoderSummary = decoderSummary,
        compatibilitySummary = compatibilitySummary,
        fallbackSummary = fallbackSummary,
        audioSummary = audioSummary,
        audioDecoderSummary = audioDecoderSummary,
        audioResilienceSummary = audioResilienceSummary,
        pipelineSummary = pipelineSummary,
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
