package com.sole.cinevault

enum class ActiveAudioDecoderKind {
    PLATFORM,
    FFMPEG,
    UNKNOWN,
}

data class ActiveAudioDecoderStatus(
    val kind: ActiveAudioDecoderKind = ActiveAudioDecoderKind.UNKNOWN,
    val decoderName: String? = null,
)

/**
 * Classifies the decoder name reported by Media3 analytics.
 *
 * We intentionally classify only what the runtime actually reports. The
 * selected stream's predicted rescue preference is not used to manufacture
 * an FFmpeg result.
 */
fun classifyActiveAudioDecoder(
    decoderName: String?,
): ActiveAudioDecoderKind {
    val normalized = decoderName
        ?.trim()
        ?.lowercase()
        .orEmpty()

    if (normalized.isBlank()) {
        return ActiveAudioDecoderKind.UNKNOWN
    }

    return if (
        normalized.contains("ffmpeg") ||
        normalized.contains("libav")
    ) {
        ActiveAudioDecoderKind.FFMPEG
    } else {
        ActiveAudioDecoderKind.PLATFORM
    }
}
