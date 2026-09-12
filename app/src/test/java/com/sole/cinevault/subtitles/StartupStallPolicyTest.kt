package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupStallPolicyTest {

    @Test
    fun shortStartupBufferDoesNotFallback() {
        assertFalse(
            shouldFallbackForStartupStall(
                isBuffering = true,
                startupPlaybackConfirmed = false,
                elapsedMs = 8_000L,
                playbackProgressMs = 0L,
                engineMode = PlaybackEngineMode.HARDWARE,
                softwareFallbackAvailable = true,
                fallbackOccurred = false,
            ),
        )
    }

    @Test
    fun sustainedStartupStallCanFallback() {
        assertTrue(
            shouldFallbackForStartupStall(
                isBuffering = true,
                startupPlaybackConfirmed = false,
                elapsedMs = 12_500L,
                playbackProgressMs = 100L,
                engineMode = PlaybackEngineMode.HARDWARE,
                softwareFallbackAvailable = true,
                fallbackOccurred = false,
            ),
        )
    }

    @Test
    fun meaningfulPlaybackProgressPreventsFalseFallback() {
        assertFalse(
            shouldFallbackForStartupStall(
                isBuffering = true,
                startupPlaybackConfirmed = false,
                elapsedMs = 15_000L,
                playbackProgressMs = 1_500L,
                engineMode = PlaybackEngineMode.HARDWARE,
                softwareFallbackAvailable = true,
                fallbackOccurred = false,
            ),
        )
    }

    @Test
    fun confirmedPlaybackMeansLaterBufferingIsNotStartupStall() {
        assertFalse(
            shouldFallbackForStartupStall(
                isBuffering = true,
                startupPlaybackConfirmed = true,
                elapsedMs = 20_000L,
                playbackProgressMs = 0L,
                engineMode = PlaybackEngineMode.HARDWARE,
                softwareFallbackAvailable = true,
                fallbackOccurred = false,
            ),
        )
    }

    @Test
    fun softwareModeNeverFallsBackAgain() {
        assertFalse(
            shouldFallbackForStartupStall(
                isBuffering = true,
                startupPlaybackConfirmed = false,
                elapsedMs = 20_000L,
                playbackProgressMs = 0L,
                engineMode = PlaybackEngineMode.SOFTWARE,
                softwareFallbackAvailable = true,
                fallbackOccurred = true,
            ),
        )
    }

    @Test
    fun missingSoftwareDecoderPreventsFallback() {
        assertFalse(
            shouldFallbackForStartupStall(
                isBuffering = true,
                startupPlaybackConfirmed = false,
                elapsedMs = 20_000L,
                playbackProgressMs = 0L,
                engineMode = PlaybackEngineMode.HARDWARE,
                softwareFallbackAvailable = false,
                fallbackOccurred = false,
            ),
        )
    }
}
