package com.sole.cinevault

enum class AudioFfmpegRescueOutcome {
    NOT_ATTEMPTED,
    PENDING,
    CONFIRMED,
    FAILED,
}

fun audioFfmpegRescueOutcome(
    attempted: Boolean,
    activeDecoder: ActiveAudioDecoderStatus,
    terminalAudioFailureAfterAttempt: Boolean,
): AudioFfmpegRescueOutcome {
    if (!attempted) return AudioFfmpegRescueOutcome.NOT_ATTEMPTED
    if (terminalAudioFailureAfterAttempt) return AudioFfmpegRescueOutcome.FAILED

    return if (activeDecoder.kind == ActiveAudioDecoderKind.FFMPEG) {
        AudioFfmpegRescueOutcome.CONFIRMED
    } else {
        AudioFfmpegRescueOutcome.PENDING
    }
}
