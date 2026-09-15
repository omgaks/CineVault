package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFailureDiagnosticTest {
    @Test fun videoSoftwareFallbackIsRecoverableVideoFailure() {
        val result = buildPlaybackFailureDiagnostic(
            attribution(PlaybackFailureStreamKind.VIDEO),
            recovery(PlaybackRecoveryAction.SWITCH_TO_SOFTWARE),
        )
        assertEquals(PlaybackFailureSeverity.RECOVERABLE, result.severity)
        assertTrue(result.affectsVideoLane)
        assertFalse(result.affectsAudioLane)
        assertEquals("Video · software rescue · error 4003", playbackFailureDiagnosticSummary(result))
    }

    @Test fun terminalAudioFailureRemainsAudioSpecific() {
        val result = buildPlaybackFailureDiagnostic(
            attribution(PlaybackFailureStreamKind.AUDIO),
            recovery(PlaybackRecoveryAction.FAIL),
        )
        assertEquals(PlaybackFailureSeverity.TERMINAL, result.severity)
        assertTrue(result.affectsAudioLane)
        assertFalse(result.affectsVideoLane)
        assertEquals("Audio · failed · error 4003", playbackFailureDiagnosticSummary(result))
    }

    @Test fun audioRetryIsRecoverableWithoutBecomingVideoFailure() {
        val result = buildPlaybackFailureDiagnostic(
            attribution(PlaybackFailureStreamKind.AUDIO),
            recovery(PlaybackRecoveryAction.RETRY_CURRENT),
        )
        assertEquals(PlaybackFailureSeverity.RECOVERABLE, result.severity)
        assertTrue(result.affectsAudioLane)
        assertFalse(result.affectsVideoLane)
    }

    @Test fun subtitleTerminalFailureIsNotMisreportedAsVideo() {
        val result = buildPlaybackFailureDiagnostic(
            attribution(PlaybackFailureStreamKind.TEXT),
            recovery(PlaybackRecoveryAction.FAIL),
        )
        assertEquals(PlaybackFailureSeverity.TERMINAL, result.severity)
        assertFalse(result.affectsVideoLane)
        assertFalse(result.affectsAudioLane)
    }

    @Test fun unknownGeneralFailureKeepsNeutralLane() {
        val result = buildPlaybackFailureDiagnostic(
            PlaybackFailureAttribution(
                PlaybackFailureStreamKind.UNKNOWN, false, null, 2001,
            ),
            recovery(PlaybackRecoveryAction.FAIL),
        )
        assertEquals(PlaybackFailureSeverity.TERMINAL, result.severity)
        assertEquals("Playback · failed · error 2001", playbackFailureDiagnosticSummary(result))
    }

    private fun attribution(kind: PlaybackFailureStreamKind) =
        PlaybackFailureAttribution(kind, true, null, 4003)

    private fun recovery(action: PlaybackRecoveryAction) =
        PlaybackRecoveryDecision(
            action = action,
            nextRetryCount = if (action == PlaybackRecoveryAction.RETRY_CURRENT) 1 else 0,
        )
}
