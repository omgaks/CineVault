package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackFailureDiagnosticCallbackTest {
    @Test fun routedAudioFailureProducesTerminalAudioDiagnostic() {
        val attribution = PlaybackFailureAttribution(
            streamKind = PlaybackFailureStreamKind.AUDIO,
            rendererFailure = true,
            rendererTrackType = null,
            errorCode = 4003,
        )
        val routed = routeRecoveryForFailure(
            attribution,
            PlaybackRecoveryDecision(
                PlaybackRecoveryAction.SWITCH_TO_SOFTWARE, 0,
            ),
        )
        val diagnostic = buildPlaybackFailureDiagnostic(attribution, routed)

        assertEquals(PlaybackFailureStreamKind.AUDIO, diagnostic.streamKind)
        assertEquals(PlaybackRecoveryAction.FAIL, diagnostic.recoveryAction)
        assertEquals(PlaybackFailureSeverity.TERMINAL, diagnostic.severity)
    }

    @Test fun routedVideoFailurePreservesSoftwareRescueDiagnostic() {
        val attribution = PlaybackFailureAttribution(
            streamKind = PlaybackFailureStreamKind.VIDEO,
            rendererFailure = true,
            rendererTrackType = null,
            errorCode = 4003,
        )
        val routed = routeRecoveryForFailure(
            attribution,
            PlaybackRecoveryDecision(
                PlaybackRecoveryAction.SWITCH_TO_SOFTWARE, 0,
            ),
        )
        val diagnostic = buildPlaybackFailureDiagnostic(attribution, routed)

        assertEquals(PlaybackFailureStreamKind.VIDEO, diagnostic.streamKind)
        assertEquals(
            PlaybackRecoveryAction.SWITCH_TO_SOFTWARE,
            diagnostic.recoveryAction,
        )
        assertEquals(PlaybackFailureSeverity.RECOVERABLE, diagnostic.severity)
    }
}
