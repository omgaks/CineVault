package com.sole.cinevault

enum class PlaybackPipelineKind {
    NATIVE,
    VIDEO_SOFTWARE_RESCUE,
    AUDIO_FFMPEG_RESCUE,
    MIXED_VIDEO_AUDIO_RESCUE,
    UNKNOWN,
}

fun classifyPlaybackPipeline(
    observation: PlaybackCompatibilityObservation,
): PlaybackPipelineKind {
    val videoRescued =
        observation.outcome == PlaybackCompatibilityOutcome.SOFTWARE_RESCUED ||
            observation.decoderKind == ActiveVideoDecoderKind.SOFTWARE

    val audioRescued =
        observation.audioDecoderKind == ActiveAudioDecoderKind.FFMPEG

    return when {
        videoRescued && audioRescued ->
            PlaybackPipelineKind.MIXED_VIDEO_AUDIO_RESCUE

        videoRescued ->
            PlaybackPipelineKind.VIDEO_SOFTWARE_RESCUE

        audioRescued ->
            PlaybackPipelineKind.AUDIO_FFMPEG_RESCUE

        observation.decoderKind == ActiveVideoDecoderKind.HARDWARE &&
            (
                observation.audioDecoderKind ==
                    ActiveAudioDecoderKind.PLATFORM ||
                    observation.audioDecoderKind ==
                    ActiveAudioDecoderKind.UNKNOWN
            ) ->
            PlaybackPipelineKind.NATIVE

        else ->
            PlaybackPipelineKind.UNKNOWN
    }
}

fun playbackPipelineLabel(
    kind: PlaybackPipelineKind,
): String = when (kind) {
    PlaybackPipelineKind.NATIVE -> "Native"
    PlaybackPipelineKind.VIDEO_SOFTWARE_RESCUE ->
        "Video software rescue"
    PlaybackPipelineKind.AUDIO_FFMPEG_RESCUE ->
        "FFmpeg audio rescue"
    PlaybackPipelineKind.MIXED_VIDEO_AUDIO_RESCUE ->
        "Mixed video + audio rescue"
    PlaybackPipelineKind.UNKNOWN -> "Unknown"
}
