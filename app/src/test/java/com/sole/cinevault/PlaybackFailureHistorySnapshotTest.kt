package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFailureHistorySnapshotTest {
    @Test
    fun diagnosticsSnapshotCarriesFailureHistoryFromRecoveryState() {
        val state = PlayerPlaybackRecoveryState()
        state.recordFailureDiagnostic(
            PlaybackFailureDiagnostic(
                streamKind = PlaybackFailureStreamKind.VIDEO,
                severity = PlaybackFailureSeverity.RECOVERABLE,
                errorCode = 4001,
                recoveryAction = PlaybackRecoveryAction.RETRY_CURRENT,
                rendererFailure = true,
            )
        )
        state.recordFailureDiagnostic(
            PlaybackFailureDiagnostic(
                streamKind = PlaybackFailureStreamKind.AUDIO,
                severity = PlaybackFailureSeverity.TERMINAL,
                errorCode = 4003,
                recoveryAction = PlaybackRecoveryAction.FAIL,
                rendererFailure = true,
            )
        )

        val snapshot = state.playbackDiagnosticsSnapshot

        assertEquals(2, snapshot.failureHistory.size)
        assertEquals(
            PlaybackFailureHistoryOutcome.RETRYING,
            snapshot.failureHistory.first().outcome,
        )
        assertEquals(
            PlaybackFailureHistoryOutcome.TERMINAL,
            snapshot.failureHistory.last().outcome,
        )
    }

    @Test
    fun newVideoProducesSnapshotWithEmptyFailureHistory() {
        val state = PlayerPlaybackRecoveryState()
        state.recordFailureDiagnostic(
            PlaybackFailureDiagnostic(
                streamKind = PlaybackFailureStreamKind.AUDIO,
                severity = PlaybackFailureSeverity.TERMINAL,
                errorCode = 4003,
                recoveryAction = PlaybackRecoveryAction.FAIL,
                rendererFailure = true,
            )
        )

        state.resetForNewVideo()

        assertTrue(state.playbackDiagnosticsSnapshot.failureHistory.isEmpty())
    }
}
