package com.sole.cinevault.playback.rescue.video

import com.sole.cinevault.PlaybackEngineMode
import com.sole.cinevault.PlayerPlaybackRecoveryState
import com.sole.cinevault.VideoRescueAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegVideoBackendCapabilityTest {

    @Test
    fun defaultCapabilityIsUnavailable() {
        val snapshot = UnavailableFfmpegVideoBackendCapability.snapshot()

        assertFalse(snapshot.backendPresent)
        assertFalse(snapshot.decoderReady)
        assertFalse(snapshot.available)
    }

    @Test
    fun backendPresenceAloneDoesNotAdvertiseVideoRescue() {
        val snapshot = FfmpegVideoBackendCapabilitySnapshot(
            backendPresent = true,
            decoderReady = false,
        )

        assertFalse(snapshot.available)
    }

    @Test
    fun decoderReadyWithoutBackendDoesNotAdvertiseVideoRescue() {
        val snapshot = FfmpegVideoBackendCapabilitySnapshot(
            backendPresent = false,
            decoderReady = true,
        )

        assertFalse(snapshot.available)
    }

    @Test
    fun capabilityIsAvailableOnlyWhenBackendAndDecoderAreReady() {
        val snapshot = FfmpegVideoBackendCapabilitySnapshot(
            backendPresent = true,
            decoderReady = true,
        )

        assertTrue(snapshot.available)
    }

    @Test
    fun unavailableContractCannotForceFfmpegRescue() {
        val state = PlayerPlaybackRecoveryState().apply {
            engineMode = PlaybackEngineMode.SOFTWARE
            softwareFallbackAvailable = true
        }

        assertEquals(
            VideoRescueAction.FAIL,
            state.decideNextVideoRescueAction(
                UnavailableFfmpegVideoBackendCapability,
            ),
        )
    }

    @Test
    fun verifiedCapabilityCanExposeFfmpegAsNextRescueLane() {
        val state = PlayerPlaybackRecoveryState().apply {
            engineMode = PlaybackEngineMode.SOFTWARE
            softwareFallbackAvailable = true
        }
        val capability = object : FfmpegVideoBackendCapability {
            override fun snapshot() = FfmpegVideoBackendCapabilitySnapshot(
                backendPresent = true,
                decoderReady = true,
                detail = "test backend ready",
            )
        }

        assertEquals(
            VideoRescueAction.SWITCH_TO_FFMPEG,
            state.decideNextVideoRescueAction(capability),
        )
    }
}
