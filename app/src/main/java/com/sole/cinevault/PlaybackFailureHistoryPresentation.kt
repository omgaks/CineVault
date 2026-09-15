package com.sole.cinevault

data class PlaybackFailureHistoryPresentation(
    val summary: String,
    val latestEvents: List<String>,
    val hasTerminalFailure: Boolean,
)

fun presentPlaybackFailureHistory(
    history: List<PlaybackFailureHistoryEntry>,
    maxVisibleEvents: Int = 3,
): PlaybackFailureHistoryPresentation? {
    require(maxVisibleEvents > 0) {
        "maxVisibleEvents must be greater than zero"
    }
    if (history.isEmpty()) return null

    return PlaybackFailureHistoryPresentation(
        summary = playbackFailureHistorySummary(history).orEmpty(),
        latestEvents = history
            .takeLast(maxVisibleEvents)
            .map(::playbackFailureHistoryEventSummary),
        hasTerminalFailure = history.any {
            it.outcome == PlaybackFailureHistoryOutcome.TERMINAL
        },
    )
}

fun playbackFailureHistoryEventSummary(
    entry: PlaybackFailureHistoryEntry,
): String {
    val lane = when (entry.streamKind) {
        PlaybackFailureStreamKind.VIDEO -> "Video"
        PlaybackFailureStreamKind.AUDIO -> "Audio"
        PlaybackFailureStreamKind.TEXT -> "Subtitle"
        PlaybackFailureStreamKind.OTHER -> "Auxiliary"
        PlaybackFailureStreamKind.UNKNOWN -> "Playback"
    }
    val outcome = when (entry.outcome) {
        PlaybackFailureHistoryOutcome.RETRYING -> "retry"
        PlaybackFailureHistoryOutcome.SOFTWARE_RESCUE -> "software rescue"
        PlaybackFailureHistoryOutcome.TERMINAL -> "failed"
    }
    return "$lane · $outcome · error ${entry.errorCode}"
}
