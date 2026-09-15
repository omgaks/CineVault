package com.sole.cinevault

enum class PlaybackFailureSeverity { RECOVERABLE, TERMINAL }

data class PlaybackFailureDiagnostic(
    val streamKind: PlaybackFailureStreamKind,
    val severity: PlaybackFailureSeverity,
    val errorCode: Int,
    val recoveryAction: PlaybackRecoveryAction,
    val rendererFailure: Boolean,
) {
    val affectsVideoLane: Boolean get() = streamKind == PlaybackFailureStreamKind.VIDEO
    val affectsAudioLane: Boolean get() = streamKind == PlaybackFailureStreamKind.AUDIO
}

fun buildPlaybackFailureDiagnostic(
    attribution: PlaybackFailureAttribution,
    recovery: PlaybackRecoveryDecision,
): PlaybackFailureDiagnostic =
    PlaybackFailureDiagnostic(
        streamKind = attribution.streamKind,
        severity = when (recovery.action) {
            PlaybackRecoveryAction.RETRY_CURRENT,
            PlaybackRecoveryAction.SWITCH_TO_SOFTWARE -> PlaybackFailureSeverity.RECOVERABLE
            PlaybackRecoveryAction.FAIL -> PlaybackFailureSeverity.TERMINAL
        },
        errorCode = attribution.errorCode,
        recoveryAction = recovery.action,
        rendererFailure = attribution.rendererFailure,
    )

fun playbackFailureDiagnosticSummary(
    diagnostic: PlaybackFailureDiagnostic,
): String {
    val lane = when (diagnostic.streamKind) {
        PlaybackFailureStreamKind.VIDEO -> "Video"
        PlaybackFailureStreamKind.AUDIO -> "Audio"
        PlaybackFailureStreamKind.TEXT -> "Subtitle"
        PlaybackFailureStreamKind.OTHER -> "Auxiliary"
        PlaybackFailureStreamKind.UNKNOWN -> "Playback"
    }
    val outcome = when (diagnostic.recoveryAction) {
        PlaybackRecoveryAction.RETRY_CURRENT -> "retrying"
        PlaybackRecoveryAction.SWITCH_TO_SOFTWARE -> "software rescue"
        PlaybackRecoveryAction.FAIL -> "failed"
    }
    return "$lane · $outcome · error ${diagnostic.errorCode}"
}
