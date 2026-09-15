package com.sole.cinevault

/**
 * Records the second renderer-level audio failure after CineVault has already
 * consumed its one FFmpeg-first rescue. The diagnostic is deliberately
 * terminal so the verified rescue outcome becomes FAILED and the event cannot
 * masquerade as a pending/confirmed rescue.
 */
fun PlayerPlaybackRecoveryState.recordTerminalAudioFailureAfterFfmpegRescue(
    attribution: PlaybackFailureAttribution,
    errorCode: Int,
) {
    recordFailureDiagnostic(
        PlaybackFailureDiagnostic(
            streamKind = PlaybackFailureStreamKind.AUDIO,
            severity = PlaybackFailureSeverity.TERMINAL,
            rendererFailure = attribution.rendererFailure,
            rendererTrackType = attribution.rendererTrackType,
            errorCode = errorCode,
        )
    )
}
