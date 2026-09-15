package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFailureStatusPresentationTest {

    @Test
    fun terminalAudioFailureTakesOverStatusPill() {
        val result = buildPlaybackStatusPillPresentation(
            baseSnapshot(
                failure = PlaybackFailureDiagnostic(
                    streamKind = PlaybackFailureStreamKind.AUDIO,
                    severity = PlaybackFailureSeverity.TERMINAL,
                    errorCode = 4003,
                    recoveryAction = PlaybackRecoveryAction.FAIL,
                    rendererFailure = true,
                )
            )
        )

        assertEquals("AUDIO FAILED", result.primaryLabel)
        assertTrue(result.emphasized)
        assertTrue(result.rotatingLabels.any { it.contains("AUDIO") && it.contains("4003") })
    }

    @Test
    fun videoSoftwareRecoveryShowsRescueStatus() {
        val result = buildPlaybackStatusPillPresentation(
            baseSnapshot(
                failure = PlaybackFailureDiagnostic(
                    streamKind = PlaybackFailureStreamKind.VIDEO,
                    severity = PlaybackFailureSeverity.RECOVERABLE,
                    errorCode = 4003,
                    recoveryAction = PlaybackRecoveryAction.SWITCH_TO_SOFTWARE,
                    rendererFailure = true,
                )
            )
        )

        assertEquals("SW RESCUE", result.primaryLabel)
        assertTrue(result.emphasized)
    }

    private fun baseSnapshot(
        failure: PlaybackFailureDiagnostic?,
    ) = PlaybackDiagnosticsSnapshot(
        mimeType = "video/hevc",
        codecString = "hvc1.2.4.L153.B0",
        resolution = "3840×2160",
        frameRate = 23.976f,
        dynamicRange = VideoDynamicRange.HDR10_OR_PQ,
        decoderName = "video.decoder",
        decoderMode = PlaybackEngineMode.HARDWARE,
        activeDecoderKind = ActiveVideoDecoderKind.HARDWARE,
        compatibilityRisk = VideoCompatibilityRisk.LOW,
        decoderRecommendation = VideoDecoderRecommendation.PREFER_HARDWARE,
        fallbackOccurred = false,
        fallbackReason = null,
        lastFailureDiagnostic = failure,
    )
}
