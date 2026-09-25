package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRescueVideoGateTest {
    @Test fun requestedAvailableHardwareVideoRescueIsAdmitted() {
        val state = PlayerPlaybackRecoveryState().apply {
            softwareFallbackAvailable = true
            softwareFallbackRequested = true
        }
        assertTrue(PlaybackRescueRuntimeGate.shouldExecuteSoftwareVideo(state))
    }

    @Test fun unavailableSoftwareDecoderBlocksVideoExecution() {
        val state = PlayerPlaybackRecoveryState().apply {
            softwareFallbackAvailable = false
            softwareFallbackRequested = true
        }
        assertFalse(PlaybackRescueRuntimeGate.shouldExecuteSoftwareVideo(state))
    }

    @Test fun alreadySoftwareVideoCannotReenterVideoRescue() {
        val state = PlayerPlaybackRecoveryState().apply {
            softwareFallbackAvailable = true
            softwareFallbackRequested = true
            engineMode = PlaybackEngineMode.SOFTWARE
        }
        assertFalse(PlaybackRescueRuntimeGate.shouldExecuteSoftwareVideo(state))
    }

    @Test fun noVideoRequestDoesNotExecuteSoftwareTransition() {
        val state = PlayerPlaybackRecoveryState().apply {
            softwareFallbackAvailable = true
            softwareFallbackRequested = false
        }
        assertFalse(PlaybackRescueRuntimeGate.shouldExecuteSoftwareVideo(state))
    }
}
