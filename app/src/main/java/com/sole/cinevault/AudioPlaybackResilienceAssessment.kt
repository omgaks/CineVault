package com.sole.cinevault

enum class AudioPlaybackReadiness {
    NONE,
    PLATFORM_EXPECTED,
    PLATFORM_ACTIVE,
    FFMPEG_RESCUE_EXPECTED,
    FFMPEG_RESCUED,
    UNKNOWN,
}

data class AudioPlaybackResilienceAssessment(
    val readiness: AudioPlaybackReadiness,
    val codecFamily: AudioCodecFamily?,
    val codecLabel: String?,
    val decoderPreference: AudioDecoderPreference?,
    val actualDecoderKind: ActiveAudioDecoderKind,
    val actualDecoderName: String?,
) {
    val rescueCandidate: Boolean
        get() =
            decoderPreference ==
                AudioDecoderPreference.FFMPEG_RESCUE_CANDIDATE

    val rescuedByFfmpeg: Boolean
        get() = readiness == AudioPlaybackReadiness.FFMPEG_RESCUED
}

/**
 * Combines the selected audio stream's predicted route with the decoder that
 * Media3 actually initialized.
 *
 * Prediction never overrides runtime truth:
 * - DTS/DTS-HD/TrueHD with no initialized decoder yet -> FFmpeg rescue expected.
 * - The same stream with a platform decoder actually active -> platform active.
 * - Any stream with an FFmpeg/libav decoder actually active -> FFmpeg rescued.
 */
fun assessAudioPlaybackResilience(
    selectedAudio: PlaybackStreamDescriptor?,
    activeDecoder: ActiveAudioDecoderStatus,
): AudioPlaybackResilienceAssessment {
    if (
        selectedAudio == null ||
        selectedAudio.kind != PlaybackStreamKind.AUDIO
    ) {
        return AudioPlaybackResilienceAssessment(
            readiness = AudioPlaybackReadiness.NONE,
            codecFamily = null,
            codecLabel = null,
            decoderPreference = null,
            actualDecoderKind = ActiveAudioDecoderKind.UNKNOWN,
            actualDecoderName = null,
        )
    }

    val capability = assessAudioStreamCapability(selectedAudio)

    val readiness = when (activeDecoder.kind) {
        ActiveAudioDecoderKind.FFMPEG ->
            AudioPlaybackReadiness.FFMPEG_RESCUED

        ActiveAudioDecoderKind.PLATFORM ->
            AudioPlaybackReadiness.PLATFORM_ACTIVE

        ActiveAudioDecoderKind.UNKNOWN -> when (
            capability?.decoderPreference
        ) {
            AudioDecoderPreference.FFMPEG_RESCUE_CANDIDATE ->
                AudioPlaybackReadiness.FFMPEG_RESCUE_EXPECTED

            AudioDecoderPreference.PLATFORM_PREFERRED ->
                AudioPlaybackReadiness.PLATFORM_EXPECTED

            AudioDecoderPreference.UNKNOWN,
            null ->
                AudioPlaybackReadiness.UNKNOWN
        }
    }

    return AudioPlaybackResilienceAssessment(
        readiness = readiness,
        codecFamily = capability?.codecFamily,
        codecLabel = capability?.codecLabel,
        decoderPreference = capability?.decoderPreference,
        actualDecoderKind = activeDecoder.kind,
        actualDecoderName = activeDecoder.decoderName,
    )
}

fun audioPlaybackReadinessLabel(
    readiness: AudioPlaybackReadiness,
): String = when (readiness) {
    AudioPlaybackReadiness.NONE ->
        "No selected audio"

    AudioPlaybackReadiness.PLATFORM_EXPECTED ->
        "Platform decoder expected"

    AudioPlaybackReadiness.PLATFORM_ACTIVE ->
        "Platform audio active"

    AudioPlaybackReadiness.FFMPEG_RESCUE_EXPECTED ->
        "FFmpeg rescue expected"

    AudioPlaybackReadiness.FFMPEG_RESCUED ->
        "FFmpeg audio rescued"

    AudioPlaybackReadiness.UNKNOWN ->
        "Audio decoder unresolved"
}
