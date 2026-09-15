package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackFailureHistoryTest {

    @Test
    fun terminalAudioFailureBecomesTerminalHistoryEntry() {
        val entry = playbackFailureHistoryEntry(
            diagnostic(
                stream = PlaybackFailureStreamKind.AUDIO,
                severity = PlaybackFailureSeverity.TERMINAL,
                action = PlaybackRecoveryAction.FAIL,
                errorCode = 4003,
            )
        )

        assertEquals(PlaybackFailureStreamKind.AUDIO, entry.streamKind)
        assertEquals(4003, entry.errorCode)
        assertEquals(PlaybackFailureHistoryOutcome.TERMINAL, entry.outcome)
    }

    @Test
    fun softwareSwitchIsRecordedAsRescueNotTerminalFailure() {
        val entry = playbackFailureHistoryEntry(
            diagnostic(
                stream = PlaybackFailureStreamKind.VIDEO,
                severity = PlaybackFailureSeverity.RECOVERABLE,
                action = PlaybackRecoveryAction.SWITCH_TO_SOFTWARE,
                errorCode = 4003,
            )
        )

        assertEquals(
            PlaybackFailureHistoryOutcome.SOFTWARE_RESCUE,
            entry.outcome,
        )
    }

    @Test
    fun exactConsecutiveDuplicateDoesNotGrowHistory() {
        val diagnostic = diagnostic(
            stream = PlaybackFailureStreamKind.VIDEO,
            severity = PlaybackFailureSeverity.RECOVERABLE,
            action = PlaybackRecoveryAction.RETRY_CURRENT,
            errorCode = 4001,
        )
        val first = appendPlaybackFailureHistory(emptyList(), diagnostic)
        val second = appendPlaybackFailureHistory(first, diagnostic)

        assertSame(first, second)
    }

    @Test
    fun historyIsBoundedToLatestEntries() {
        var history = emptyList<PlaybackFailureHistoryEntry>()

        repeat(5) { index ->
            history = appendPlaybackFailureHistory(
                history = history,
                diagnostic = diagnostic(
                    stream = PlaybackFailureStreamKind.VIDEO,
                    severity = PlaybackFailureSeverity.RECOVERABLE,
                    action = PlaybackRecoveryAction.RETRY_CURRENT,
                    errorCode = 4000 + index,
                ),
                maxEntries = 3,
            )
        }

        assertEquals(listOf(4002, 4003, 4004), history.map { it.errorCode })
    }

    @Test
    fun summarySeparatesTerminalRescueAndRetryEvents() {
        val history = listOf(
            PlaybackFailureHistoryEntry(
                PlaybackFailureStreamKind.AUDIO,
                4003,
                PlaybackFailureHistoryOutcome.TERMINAL,
                true,
            ),
            PlaybackFailureHistoryEntry(
                PlaybackFailureStreamKind.VIDEO,
                4002,
                PlaybackFailureHistoryOutcome.SOFTWARE_RESCUE,
                true,
            ),
            PlaybackFailureHistoryEntry(
                PlaybackFailureStreamKind.VIDEO,
                4001,
                PlaybackFailureHistoryOutcome.RETRYING,
                true,
            ),
        )

        assertEquals(
            "1 terminal · 1 rescue · 1 retry",
            playbackFailureHistorySummary(history),
        )
    }

    private fun diagnostic(
        stream: PlaybackFailureStreamKind,
        severity: PlaybackFailureSeverity,
        action: PlaybackRecoveryAction,
        errorCode: Int,
    ) = PlaybackFailureDiagnostic(
        streamKind = stream,
        severity = severity,
        errorCode = errorCode,
        recoveryAction = action,
        rendererFailure = true,
    )
}
