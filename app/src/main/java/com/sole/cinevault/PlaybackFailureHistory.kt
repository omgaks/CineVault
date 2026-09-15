package com.sole.cinevault

enum class PlaybackFailureHistoryOutcome {
    RETRYING,
    SOFTWARE_RESCUE,
    TERMINAL,
}

data class PlaybackFailureHistoryEntry(
    val streamKind: PlaybackFailureStreamKind,
    val errorCode: Int,
    val outcome: PlaybackFailureHistoryOutcome,
    val rendererFailure: Boolean,
)

fun playbackFailureHistoryEntry(
    diagnostic: PlaybackFailureDiagnostic,
): PlaybackFailureHistoryEntry =
    PlaybackFailureHistoryEntry(
        streamKind = diagnostic.streamKind,
        errorCode = diagnostic.errorCode,
        outcome = when {
            diagnostic.severity == PlaybackFailureSeverity.TERMINAL ->
                PlaybackFailureHistoryOutcome.TERMINAL
            diagnostic.recoveryAction == PlaybackRecoveryAction.SWITCH_TO_SOFTWARE ->
                PlaybackFailureHistoryOutcome.SOFTWARE_RESCUE
            else ->
                PlaybackFailureHistoryOutcome.RETRYING
        },
        rendererFailure = diagnostic.rendererFailure,
    )

fun appendPlaybackFailureHistory(
    history: List<PlaybackFailureHistoryEntry>,
    diagnostic: PlaybackFailureDiagnostic,
    maxEntries: Int = 8,
): List<PlaybackFailureHistoryEntry> {
    require(maxEntries > 0) { "maxEntries must be greater than zero" }

    val candidate = playbackFailureHistoryEntry(diagnostic)
    if (history.lastOrNull() == candidate) return history

    return (history + candidate).takeLast(maxEntries)
}

fun playbackFailureHistorySummary(
    history: List<PlaybackFailureHistoryEntry>,
): String? {
    if (history.isEmpty()) return null

    val terminal = history.count {
        it.outcome == PlaybackFailureHistoryOutcome.TERMINAL
    }
    val rescues = history.count {
        it.outcome == PlaybackFailureHistoryOutcome.SOFTWARE_RESCUE
    }
    val retries = history.count {
        it.outcome == PlaybackFailureHistoryOutcome.RETRYING
    }

    return buildList {
        if (terminal > 0) add("$terminal terminal")
        if (rescues > 0) add("$rescues rescue")
        if (retries > 0) add("$retries retry")
    }.joinToString(" · ")
}
