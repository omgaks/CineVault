package com.sole.cinevault

enum class AudioPlaybackRecoveryAction {
    NONE,
    SWITCH_TO_FFMPEG,
    FAIL,
}

data class AudioPlaybackRecoveryDecision(
    val action: AudioPlaybackRecoveryAction,
    val codecFamily: AudioCodecFamily? = null,
    val reason: String,
)

/**
 * Decides whether an AUDIO renderer failure is eligible for CineVault's
 * FFmpeg audio rescue lane.
 *
 * This policy is intentionally conservative:
 * - only attributed audio renderer failures enter this lane;
 * - only codecs already classified as FFmpeg rescue candidates may switch;
 * - an active/previous FFmpeg attempt is never attempted again;
 * - unrelated stream failures are left to their existing recovery policy.
 *
 * This slice defines the decision boundary only. It does not yet recreate or
 * reorder Media3 renderers.
 */
fun decideAudioPlaybackRecovery(
    attribution: PlaybackFailureAttribution,
    selectedAudio: PlaybackStreamDescriptor?,
    activeDecoder: ActiveAudioDecoderStatus,
    ffmpegRescueAlreadyAttempted: Boolean,
): AudioPlaybackRecoveryDecision {
    if (!attribution.isAudioRendererFailure) {
        return AudioPlaybackRecoveryDecision(
            action = AudioPlaybackRecoveryAction.NONE,
            reason = "Failure is not from the audio renderer",
        )
    }

    val assessment = assessAudioPlaybackResilience(
        selectedAudio = selectedAudio,
        activeDecoder = activeDecoder,
    )

    if (
        activeDecoder.kind == ActiveAudioDecoderKind.FFMPEG ||
        ffmpegRescueAlreadyAttempted
    ) {
        return AudioPlaybackRecoveryDecision(
            action = AudioPlaybackRecoveryAction.FAIL,
            codecFamily = assessment.codecFamily,
            reason = "FFmpeg audio rescue already active or attempted",
        )
    }

    if (!assessment.rescueCandidate) {
        return AudioPlaybackRecoveryDecision(
            action = AudioPlaybackRecoveryAction.FAIL,
            codecFamily = assessment.codecFamily,
            reason = "Selected audio is not an FFmpeg rescue candidate",
        )
    }

    return AudioPlaybackRecoveryDecision(
        action = AudioPlaybackRecoveryAction.SWITCH_TO_FFMPEG,
        codecFamily = assessment.codecFamily,
        reason = "Audio renderer failed and FFmpeg rescue is eligible",
    )
}
