package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackFailureRecoveryRoutingTest {

    @Test
    fun videoRendererFailureCanUseVideoSoftwareFallback() {
        val routed = routeRecoveryForFailure(
            attribution = attribution(PlaybackFailureStreamKind.VIDEO),
            recovery = switchToSoftware(),
        )

        assertEquals(
            PlaybackRecoveryAction.SWITCH_TO_SOFTWARE,
            routed.action,
        )
    }

    @Test
    fun unknownFailureKeepsExistingVideoRecoveryBehavior() {
        val routed = routeRecoveryForFailure(
            attribution = attribution(PlaybackFailureStreamKind.UNKNOWN),
            recovery = switchToSoftware(),
        )

        assertEquals(
            PlaybackRecoveryAction.SWITCH_TO_SOFTWARE,
            routed.action,
        )
    }

    @Test
    fun audioRendererFailureCannotForceVideoSoftwareFallback() {
        val routed = routeRecoveryForFailure(
            attribution = attribution(PlaybackFailureStreamKind.AUDIO),
            recovery = switchToSoftware(),
        )

        assertEquals(PlaybackRecoveryAction.FAIL, routed.action)
    }

    @Test
    fun subtitleRendererFailureCannotForceVideoSoftwareFallback() {
        val routed = routeRecoveryForFailure(
            attribution = attribution(PlaybackFailureStreamKind.TEXT),
            recovery = switchToSoftware(),
        )

        assertEquals(PlaybackRecoveryAction.FAIL, routed.action)
    }

    @Test
    fun auxiliaryRendererFailureCannotForceVideoSoftwareFallback() {
        val routed = routeRecoveryForFailure(
            attribution = attribution(PlaybackFailureStreamKind.OTHER),
            recovery = switchToSoftware(),
        )

        assertEquals(PlaybackRecoveryAction.FAIL, routed.action)
    }

    @Test
    fun retryDecisionIsPreservedForAudioFailure() {
        val recovery = PlaybackRecoveryDecision(
            action = PlaybackRecoveryAction.RETRY_CURRENT,
            nextRetryCount = 1,
        )

        val routed = routeRecoveryForFailure(
            attribution = attribution(PlaybackFailureStreamKind.AUDIO),
            recovery = recovery,
        )

        assertEquals(PlaybackRecoveryAction.RETRY_CURRENT, routed.action)
        assertEquals(1, routed.nextRetryCount)
    }

    private fun attribution(
        kind: PlaybackFailureStreamKind,
    ): PlaybackFailureAttribution =
        PlaybackFailureAttribution(
            streamKind = kind,
            rendererFailure = kind != PlaybackFailureStreamKind.UNKNOWN,
            rendererTrackType = null,
            errorCode = 4003,
        )

    private fun switchToSoftware(): PlaybackRecoveryDecision =
        PlaybackRecoveryDecision(
            action = PlaybackRecoveryAction.SWITCH_TO_SOFTWARE,
            nextRetryCount = 0,
        )
}
