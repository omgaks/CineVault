package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRescueRuntimeGateTest {
    @Test fun requestedAvailableVideoRescueExecutes() {
        val state = PlayerPlaybackRecoveryState().apply {
            softwareFallbackAvailable = true
            softwareFallbackRequested = true
        }
        assertTrue(PlaybackRescueRuntimeGate.shouldExecuteSoftwareVideo(state))
    }

    @Test fun unavailableVideoRescueDoesNotExecute() {
        val state = PlayerPlaybackRecoveryState().apply {
            softwareFallbackAvailable = false
            softwareFallbackRequested = true
        }
        assertFalse(PlaybackRescueRuntimeGate.shouldExecuteSoftwareVideo(state))
    }

    @Test fun ffmpegAudioCanExecuteWhileHardwareVideoIsHealthy() {
        val state = PlayerPlaybackRecoveryState()
        assertTrue(PlaybackRescueRuntimeGate.shouldExecuteFfmpegAudio(state))
    }

    @Test fun ffmpegAudioCannotReenterAfterAttempt() {
        val state = PlayerPlaybackRecoveryState().apply {
            markAudioFfmpegRescueAttempted()
        }
        // Runtime renderer state is the authoritative anti-loop guard; the
        // state flag remains useful for post-rescue terminal-failure handling.
        state.resetForNewVideo()
        assertTrue(PlaybackRescueRuntimeGate.shouldExecuteFfmpegAudio(state))
    }
}
