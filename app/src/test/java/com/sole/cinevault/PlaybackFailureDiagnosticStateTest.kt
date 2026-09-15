package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackFailureDiagnosticStateTest {
    @Test fun failureDiagnosticIsRecordedInSnapshot() {
        val state = PlayerPlaybackRecoveryState()
        val diagnostic = PlaybackFailureDiagnostic(
            streamKind = PlaybackFailureStreamKind.AUDIO,
            severity = PlaybackFailureSeverity.TERMINAL,
            errorCode = 4003,
            recoveryAction = PlaybackRecoveryAction.FAIL,
            rendererFailure = true,
        )
        state.recordFailureDiagnostic(diagnostic)
        assertEquals(diagnostic, state.playbackDiagnosticsSnapshot.lastFailureDiagnostic)
    }

    @Test fun newVideoClearsPreviousFailureDiagnostic() {
        val state = PlayerPlaybackRecoveryState()
        state.recordFailureDiagnostic(
            PlaybackFailureDiagnostic(
                PlaybackFailureStreamKind.VIDEO,
                PlaybackFailureSeverity.RECOVERABLE,
                4003,
                PlaybackRecoveryAction.SWITCH_TO_SOFTWARE,
                true,
            )
        )
        state.resetForNewVideo()
        assertNull(state.lastFailureDiagnostic)
        assertNull(state.playbackDiagnosticsSnapshot.lastFailureDiagnostic)
    }
}
