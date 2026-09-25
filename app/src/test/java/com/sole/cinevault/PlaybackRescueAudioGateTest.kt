package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRescueAudioGateTest {
    @Test fun healthyHardwareSessionAdmitsFfmpegAudioRescue() {
        val state = PlayerPlaybackRecoveryState()

        assertTrue(
            PlaybackRescueRuntimeGate.shouldExecuteFfmpegAudio(state)
        )
    }

    @Test fun softwareVideoSessionStillAdmitsIndependentAudioRescue() {
        val state = PlayerPlaybackRecoveryState().apply {
            engineMode = PlaybackEngineMode.SOFTWARE
        }

        assertTrue(
            PlaybackRescueRuntimeGate.shouldExecuteFfmpegAudio(state)
        )
    }

    @Test fun softwareVideoRequestAndAudioRequestProduceMixedExecutionPlan() {
        val state = PlayerPlaybackRecoveryState().apply {
            softwareFallbackAvailable = true
            softwareFallbackRequested = true
        }

        val plan = PlaybackRescueRuntimeGate.plan(
            recoveryState = state,
            ffmpegAudioRequested = true,
        )

        assertTrue(plan.executeSoftwareVideo)
        assertTrue(plan.executeFfmpegAudio)
    }

    @Test fun unavailableSoftwareVideoDoesNotBlockAudioRescue() {
        val state = PlayerPlaybackRecoveryState().apply {
            softwareFallbackAvailable = false
            softwareFallbackRequested = true
        }

        val plan = PlaybackRescueRuntimeGate.plan(
            recoveryState = state,
            ffmpegAudioRequested = true,
        )

        assertFalse(plan.executeSoftwareVideo)
        assertTrue(plan.executeFfmpegAudio)
    }
}
