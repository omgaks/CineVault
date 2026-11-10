package com.sole.cinevault

enum class PlaybackFailureSeverity { RECOVERABLE, TERMINAL }

data class PlaybackFailureDiagnostic(
    val streamKind: PlaybackFailureStreamKind,
    val severity: PlaybackFailureSeverity,
    val errorCode: Int,
    val recoveryAction: PlaybackRecoveryAction,
    val rendererFailure: Boolean,
    /** The underlying reader/decoder message (e.g. the MKV parser's complaint), when known. */
    val detail: String? = null,
) {
    val affectsVideoLane: Boolean get() = streamKind == PlaybackFailureStreamKind.VIDEO
    val affectsAudioLane: Boolean get() = streamKind == PlaybackFailureStreamKind.AUDIO
}

fun buildPlaybackFailureDiagnostic(
    attribution: PlaybackFailureAttribution,
    recovery: PlaybackRecoveryDecision,
    detail: String? = null,
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
        detail = detail,
    )

private val WHITESPACE_RUN = Regex("\\s+")

/**
 * Walks the exception chain and returns a short "Type: message" trail (most specific
 * last), so the playback report can show *why* a file was rejected, not just the code.
 * Returns null when no cause carries any message.
 */
fun playbackFailureRootCause(error: Throwable, maxLength: Int = 240): String? {
    val parts = mutableListOf<String>()
    var cause: Throwable? = error.cause
    var depth = 0
    while (cause != null && depth < 6) {
        val message = cause.message?.trim()?.replace(WHITESPACE_RUN, " ")
        if (!message.isNullOrEmpty()) {
            val part = "${cause.javaClass.simpleName}: $message"
            if (part !in parts) parts += part
        }
        cause = cause.cause
        depth++
    }
    if (parts.isEmpty()) return null
    val joined = parts.joinToString(" ← ")
    return if (joined.length <= maxLength) joined else joined.take(maxLength - 1) + "…"
}

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

/**
 * Report-panel version: the short summary plus the underlying cause on a second line.
 * Kept separate from [playbackFailureDiagnosticSummary], which feeds the small status pill.
 */
fun playbackFailureDiagnosticDetailedSummary(diagnostic: PlaybackFailureDiagnostic): String {
    val base = playbackFailureDiagnosticSummary(diagnostic)
    val cause = diagnostic.detail?.takeIf { it.isNotBlank() } ?: return base
    return "$base\nCause: $cause"
}
