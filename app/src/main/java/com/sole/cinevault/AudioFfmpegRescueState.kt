package com.sole.cinevault

fun PlayerPlaybackRecoveryState.currentAudioFfmpegRescueOutcome():
    AudioFfmpegRescueOutcome {
    val terminalAudioFailureAfterAttempt =
        audioFfmpegRescueAttempted &&
            lastFailureDiagnostic?.streamKind == PlaybackFailureStreamKind.AUDIO &&
            lastFailureDiagnostic?.severity == PlaybackFailureSeverity.TERMINAL

    return audioFfmpegRescueOutcome(
        attempted = audioFfmpegRescueAttempted,
        activeDecoder = activeAudioDecoderStatus,
        terminalAudioFailureAfterAttempt = terminalAudioFailureAfterAttempt,
    )
}
