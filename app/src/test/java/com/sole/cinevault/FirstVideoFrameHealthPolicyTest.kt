package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstVideoFrameHealthPolicyTest {

    @Test
    fun normalFirstFramePreventsFallback() {
        assertFalse(
            shouldFallbackForMissingFirstVideoFrame(
                isPlaying = true,
                hasSelectedVideoTrack = true,
                firstVideoFrameRendered = true,
                elapsedMs = 10_000L,
                playbackProgressMs = 8_000L,
                engineMode = PlaybackEngineMode.HARDWARE,
                softwareFallbackAvailable = true,
                fallbackOccurred = false,
            ),
        )
    }

    @Test
    fun movingPlaybackWithoutFirstFrameCanFallback() {
        assertTrue(
            shouldFallbackForMissingFirstVideoFrame(
                isPlaying = true,
                hasSelectedVideoTrack = true,
                firstVideoFrameRendered = false,
                elapsedMs = 8_500L,
                playbackProgressMs = 6_000L,
                engineMode = PlaybackEngineMode.HARDWARE,
                softwareFallbackAvailable = true,
                fallbackOccurred = false,
            ),
        )
    }

    @Test
    fun bufferingWithoutProgressIsNotMisclassifiedAsBlackVideo() {
        assertFalse(
            shouldFallbackForMissingFirstVideoFrame(
                isPlaying = false,
                hasSelectedVideoTrack = true,
                firstVideoFrameRendered = false,
                elapsedMs = 12_000L,
                playbackProgressMs = 0L,
                engineMode = PlaybackEngineMode.HARDWARE,
                softwareFallbackAvailable = true,
                fallbackOccurred = false,
            ),
        )
    }

    @Test
    fun audioOnlyPlaybackNeverTriggersVideoFallback() {
        assertFalse(
            shouldFallbackForMissingFirstVideoFrame(
                isPlaying = true,
                hasSelectedVideoTrack = false,
                firstVideoFrameRendered = false,
                elapsedMs = 12_000L,
                playbackProgressMs = 10_000L,
                engineMode = PlaybackEngineMode.HARDWARE,
                softwareFallbackAvailable = true,
                fallbackOccurred = false,
            ),
        )
    }

    @Test
    fun softwareModeDoesNotLoopBackIntoFallback() {
        assertFalse(
            shouldFallbackForMissingFirstVideoFrame(
                isPlaying = true,
                hasSelectedVideoTrack = true,
                firstVideoFrameRendered = false,
                elapsedMs = 12_000L,
                playbackProgressMs = 10_000L,
                engineMode = PlaybackEngineMode.SOFTWARE,
                softwareFallbackAvailable = true,
                fallbackOccurred = true,
            ),
        )
    }

    @Test
    fun unavailableSoftwareDecoderPreventsFallback() {
        assertFalse(
            shouldFallbackForMissingFirstVideoFrame(
                isPlaying = true,
                hasSelectedVideoTrack = true,
                firstVideoFrameRendered = false,
                elapsedMs = 12_000L,
                playbackProgressMs = 10_000L,
                engineMode = PlaybackEngineMode.HARDWARE,
                softwareFallbackAvailable = false,
                fallbackOccurred = false,
            ),
        )
    }
}
