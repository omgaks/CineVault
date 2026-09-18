package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerVideoRescueBridgeTest {

    @Test
    fun hardwareStateMapsToHardwareRescueStage() {
        val state = PlayerPlaybackRecoveryState().apply {
            engineMode = PlaybackEngineMode.HARDWARE
        }

        assertEquals(
            VideoRescueStage.HARDWARE,
            state.currentVideoRescueStage(),
        )
    }

    @Test
    fun existingSoftwareModeMapsToPlatformSoftwareStage() {
        val state = PlayerPlaybackRecoveryState().apply {
            engineMode = PlaybackEngineMode.SOFTWARE
        }

        assertEquals(
            VideoRescueStage.PLATFORM_SOFTWARE,
            state.currentVideoRescueStage(),
        )
    }

    @Test
    fun hardwareUsesExistingPlatformSoftwareFallbackFirst() {
        val state = PlayerPlaybackRecoveryState().apply {
            engineMode = PlaybackEngineMode.HARDWARE
            softwareFallbackAvailable = true
        }

        assertEquals(
            VideoRescueAction.SWITCH_TO_PLATFORM_SOFTWARE,
            state.decideNextVideoRescueAction(ffmpegVideoAvailable = true),
        )
    }

    @Test
    fun hardwareCanChooseFfmpegWhenPlatformSoftwareIsUnavailable() {
        val state = PlayerPlaybackRecoveryState().apply {
            engineMode = PlaybackEngineMode.HARDWARE
            softwareFallbackAvailable = false
        }

        assertEquals(
            VideoRescueAction.SWITCH_TO_FFMPEG,
            state.decideNextVideoRescueAction(ffmpegVideoAvailable = true),
        )
    }

    @Test
    fun softwareModeEscalatesToFfmpegWhenBackendIsReallyAvailable() {
        val state = PlayerPlaybackRecoveryState().apply {
            engineMode = PlaybackEngineMode.SOFTWARE
            softwareFallbackAvailable = true
        }

        assertEquals(
            VideoRescueAction.SWITCH_TO_FFMPEG,
            state.decideNextVideoRescueAction(ffmpegVideoAvailable = true),
        )
    }

    @Test
    fun softwareModeFailsSafelyUntilFfmpegVideoBackendExists() {
        val state = PlayerPlaybackRecoveryState().apply {
            engineMode = PlaybackEngineMode.SOFTWARE
            softwareFallbackAvailable = true
        }

        assertEquals(
            VideoRescueAction.FAIL,
            state.decideNextVideoRescueAction(ffmpegVideoAvailable = false),
        )
    }

    @Test
    fun hardwareFailsSafelyWhenNeitherRescueLaneExists() {
        val state = PlayerPlaybackRecoveryState().apply {
            engineMode = PlaybackEngineMode.HARDWARE
            softwareFallbackAvailable = false
        }

        assertEquals(
            VideoRescueAction.FAIL,
            state.decideNextVideoRescueAction(ffmpegVideoAvailable = false),
        )
    }
}
