package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFailureDiagnosticStateTest {
    @Test
    fun failureDiagnosticIsRecordedInSnapshot() {
        val state = PlayerPlaybackRecoveryState()
        val diagnostic = diagnostic(
            PlaybackFailureStreamKind.AUDIO,
            PlaybackFailureSeverity.TERMINAL,
            4003,
            PlaybackRecoveryAction.FAIL,
        )

        state.recordFailureDiagnostic(diagnostic)

        assertEquals(
            diagnostic,
            state.playbackDiagnosticsSnapshot.lastFailureDiagnostic,
        )
    }

    @Test
    fun recordingDiagnosticAlsoAddsBoundedFailureHistory() {
        val state = PlayerPlaybackRecoveryState()

        state.recordFailureDiagnostic(
            diagnostic(
                PlaybackFailureStreamKind.VIDEO,
                PlaybackFailureSeverity.RECOVERABLE,
                4001,
                PlaybackRecoveryAction.RETRY_CURRENT,
            )
        )
        state.recordFailureDiagnostic(
            diagnostic(
                PlaybackFailureStreamKind.AUDIO,
                PlaybackFailureSeverity.TERMINAL,
                4003,
                PlaybackRecoveryAction.FAIL,
            )
        )

        assertEquals(2, state.failureHistory.size)
        assertEquals(
            PlaybackFailureHistoryOutcome.RETRYING,
            state.failureHistory[0].outcome,
        )
        assertEquals(
            PlaybackFailureHistoryOutcome.TERMINAL,
            state.failureHistory[1].outcome,
        )
    }

    @Test
    fun repeatedIdenticalDiagnosticDoesNotFloodStateHistory() {
        val state = PlayerPlaybackRecoveryState()
        val failure = diagnostic(
            PlaybackFailureStreamKind.AUDIO,
            PlaybackFailureSeverity.TERMINAL,
            4003,
            PlaybackRecoveryAction.FAIL,
        )

        repeat(5) {
            state.recordFailureDiagnostic(failure)
        }

        assertEquals(1, state.failureHistory.size)
    }

    @Test
    fun stateHistoryUsesDefaultEightEventBound() {
        val state = PlayerPlaybackRecoveryState()

        repeat(12) { index ->
            state.recordFailureDiagnostic(
                diagnostic(
                    PlaybackFailureStreamKind.VIDEO,
                    PlaybackFailureSeverity.RECOVERABLE,
                    4000 + index,
                    PlaybackRecoveryAction.RETRY_CURRENT,
                )
            )
        }

        assertEquals(8, state.failureHistory.size)
        assertEquals(4004, state.failureHistory.first().errorCode)
        assertEquals(4011, state.failureHistory.last().errorCode)
    }

    @Test
    fun newVideoClearsPreviousFailureDiagnosticAndHistory() {
        val state = PlayerPlaybackRecoveryState()
        state.recordFailureDiagnostic(
            diagnostic(
                PlaybackFailureStreamKind.VIDEO,
                PlaybackFailureSeverity.RECOVERABLE,
                4003,
                PlaybackRecoveryAction.SWITCH_TO_SOFTWARE,
            )
        )

        state.resetForNewVideo()

        assertNull(state.lastFailureDiagnostic)
        assertNull(state.playbackDiagnosticsSnapshot.lastFailureDiagnostic)
        assertTrue(state.failureHistory.isEmpty())
    }

    private fun diagnostic(
        stream: PlaybackFailureStreamKind,
        severity: PlaybackFailureSeverity,
        errorCode: Int,
        action: PlaybackRecoveryAction,
    ) = PlaybackFailureDiagnostic(
        streamKind = stream,
        severity = severity,
        errorCode = errorCode,
        recoveryAction = action,
        rendererFailure = true,
    )
}
