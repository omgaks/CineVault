package com.sole.cinevault

/**
 * Protects CineVault from sending a non-video renderer failure into the
 * video software-fallback lane.
 *
 * Media3 can already try the registered FFmpeg audio renderer internally.
 * If an AUDIO/TEXT/OTHER renderer failure still reaches Player.onPlayerError,
 * switching CineVault's VIDEO decoder selector to software will not repair
 * that failed lane.
 */
fun routeRecoveryForFailure(
    attribution: PlaybackFailureAttribution,
    recovery: PlaybackRecoveryDecision,
): PlaybackRecoveryDecision {
    if (recovery.action != PlaybackRecoveryAction.SWITCH_TO_SOFTWARE) {
        return recovery
    }

    return when (attribution.streamKind) {
        PlaybackFailureStreamKind.VIDEO,
        PlaybackFailureStreamKind.UNKNOWN ->
            recovery

        PlaybackFailureStreamKind.AUDIO,
        PlaybackFailureStreamKind.TEXT,
        PlaybackFailureStreamKind.OTHER ->
            PlaybackRecoveryDecision(
                action = PlaybackRecoveryAction.FAIL,
                nextRetryCount = recovery.nextRetryCount,
            )
    }
}
