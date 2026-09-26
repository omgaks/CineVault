package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStatusPillVisibilityTest {
    @Test
    fun nativeHardwarePlaybackIsNotAVisibleStatusPillState() {
        assertFalse(
            shouldShowPlaybackStatusPill(
                PlaybackDiagnosticsSnapshot(
                    activeDecoderKind = ActiveVideoDecoderKind.HARDWARE,
                    decoderMode = PlaybackEngineMode.HARDWARE,
                )
            )
        )
    }

    @Test
    fun softwareVideoRescueShowsStatusPill() {
        assertTrue(
            shouldShowPlaybackStatusPill(
                PlaybackDiagnosticsSnapshot(
                    activeDecoderKind = ActiveVideoDecoderKind.SOFTWARE,
                    decoderMode = PlaybackEngineMode.SOFTWARE,
                    fallbackOccurred = true,
                )
            )
        )
    }

    @Test
    fun confirmedFfmpegAudioShowsStatusPill() {
        assertTrue(
            shouldShowPlaybackStatusPill(
                PlaybackDiagnosticsSnapshot(
                    activeDecoderKind = ActiveVideoDecoderKind.HARDWARE,
                    decoderMode = PlaybackEngineMode.HARDWARE,
                    activeAudioDecoderKind = ActiveAudioDecoderKind.FFMPEG,
                    audioFfmpegRescueOutcome = AudioFfmpegRescueOutcome.CONFIRMED,
                )
            )
        )
    }

    @Test
    fun mixedRescueShowsStatusPill() {
        assertTrue(
            shouldShowPlaybackStatusPill(
                PlaybackDiagnosticsSnapshot(
                    activeDecoderKind = ActiveVideoDecoderKind.SOFTWARE,
                    decoderMode = PlaybackEngineMode.SOFTWARE,
                    fallbackOccurred = true,
                    activeAudioDecoderKind = ActiveAudioDecoderKind.FFMPEG,
                    audioFfmpegRescueOutcome = AudioFfmpegRescueOutcome.CONFIRMED,
                )
            )
        )
    }
}
