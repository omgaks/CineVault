package com.sole.cinevault

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRecoveryPolicyTest {

    @Test
    fun transientFailureRetriesCurrentEngineWithinLimit() {
        val decision = decidePlaybackRecovery(
            errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            currentRetryCount = 0,
            engineMode = PlaybackEngineMode.HARDWARE,
            softwareFallbackAvailable = true,
        )

        assertEquals(PlaybackRecoveryAction.RETRY_CURRENT, decision.action)
        assertEquals(1, decision.nextRetryCount)
    }

    @Test
    fun transientFailureStopsAfterRetryLimit() {
        val decision = decidePlaybackRecovery(
            errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            currentRetryCount = 2,
            engineMode = PlaybackEngineMode.HARDWARE,
            softwareFallbackAvailable = true,
        )

        assertEquals(PlaybackRecoveryAction.FAIL, decision.action)
        assertEquals(2, decision.nextRetryCount)
    }

    @Test
    fun decoderFailureOnHardwareRequestsSoftwareFallback() {
        val decision = decidePlaybackRecovery(
            errorCode = PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            currentRetryCount = 0,
            engineMode = PlaybackEngineMode.HARDWARE,
            softwareFallbackAvailable = true,
        )

        assertEquals(PlaybackRecoveryAction.SWITCH_TO_SOFTWARE, decision.action)
        assertEquals(0, decision.nextRetryCount)
    }

    @Test
    fun decoderFailureDoesNotLoopWhenAlreadyUsingSoftware() {
        val decision = decidePlaybackRecovery(
            errorCode = PlaybackException.ERROR_CODE_DECODING_FAILED,
            currentRetryCount = 0,
            engineMode = PlaybackEngineMode.SOFTWARE,
            softwareFallbackAvailable = true,
        )

        assertEquals(PlaybackRecoveryAction.FAIL, decision.action)
    }

    @Test
    fun decoderFailureFailsWhenNoSoftwareFallbackExists() {
        val decision = decidePlaybackRecovery(
            errorCode = PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            currentRetryCount = 0,
            engineMode = PlaybackEngineMode.HARDWARE,
            softwareFallbackAvailable = false,
        )

        assertEquals(PlaybackRecoveryAction.FAIL, decision.action)
    }

    @Test
    fun unrelatedPermanentFailureDoesNotTriggerSoftwareFallback() {
        val decision = decidePlaybackRecovery(
            errorCode = PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            currentRetryCount = 0,
            engineMode = PlaybackEngineMode.HARDWARE,
            softwareFallbackAvailable = true,
        )

        assertEquals(PlaybackRecoveryAction.FAIL, decision.action)
    }
}
