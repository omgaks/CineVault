package com.sole.cinevault

/**
 * Records the second renderer-level audio failure after CineVault has already
 * consumed its one FFmpeg-first rescue. This terminal diagnostic prevents the
 * failure from re-entering the generic retry path and makes the rescue outcome
 * resolve as failed.
 */
fun PlayerPlaybackRecoveryState.recordTerminalAudioFailureAfterFfmpegRescue(
    attribution: PlaybackFailureAttribution,
    errorCode: Int,
) {
    recordFailureDiagnostic(
        PlaybackFailureDiagnostic(
            streamKind = PlaybackFailureStreamKind.AUDIO,
            severity = PlaybackFailureSeverity.TERMINAL,
            errorCode = errorCode,
            recoveryAction = PlaybackRecoveryAction.FAIL,
            rendererFailure = attribution.rendererFailure,
        )
    )
}
