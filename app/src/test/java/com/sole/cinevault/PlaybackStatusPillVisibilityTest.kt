package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStatusPillVisibilityTest {

    private fun snapshot(
        decoderMode: PlaybackEngineMode = PlaybackEngineMode.HARDWARE,
        activeDecoderKind: ActiveVideoDecoderKind = ActiveVideoDecoderKind.HARDWARE,
        fallbackOccurred: Boolean = false,
        activeAudioDecoderKind: ActiveAudioDecoderKind = ActiveAudioDecoderKind.UNKNOWN,
        audioFfmpegRescueOutcome: AudioFfmpegRescueOutcome =
            AudioFfmpegRescueOutcome.NOT_ATTEMPTED,
    ) = PlaybackDiagnosticsSnapshot(
        mimeType = null,
        codecString = null,
        resolution = "Unknown",
        frameRate = null,
        dynamicRange = VideoDynamicRange.UNKNOWN,
        decoderName = null,
        decoderMode = decoderMode,
        activeDecoderKind = activeDecoderKind,
        compatibilityRisk = VideoCompatibilityRisk.LOW,
        decoderRecommendation = VideoDecoderRecommendation.HARDWARE,
        fallbackOccurred = fallbackOccurred,
        fallbackReason = null,
        activeAudioDecoderKind = activeAudioDecoderKind,
        audioFfmpegRescueOutcome = audioFfmpegRescueOutcome,
    )

    @Test
    fun nativeHardwarePlaybackIsNotAVisibleStatusPillState() {
        assertFalse(shouldShowPlaybackStatusPill(snapshot()))
    }

    @Test
    fun softwareVideoRescueShowsStatusPill() {
        assertTrue(
            shouldShowPlaybackStatusPill(
                snapshot(
                    decoderMode = PlaybackEngineMode.SOFTWARE,
                    activeDecoderKind = ActiveVideoDecoderKind.SOFTWARE,
                    fallbackOccurred = true,
                )
            )
        )
    }

    @Test
    fun confirmedFfmpegAudioShowsStatusPill() {
        assertTrue(
            shouldShowPlaybackStatusPill(
                snapshot(
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
                snapshot(
                    decoderMode = PlaybackEngineMode.SOFTWARE,
                    activeDecoderKind = ActiveVideoDecoderKind.SOFTWARE,
                    fallbackOccurred = true,
                    activeAudioDecoderKind = ActiveAudioDecoderKind.FFMPEG,
                    audioFfmpegRescueOutcome = AudioFfmpegRescueOutcome.CONFIRMED,
                )
            )
        )
    }
}
