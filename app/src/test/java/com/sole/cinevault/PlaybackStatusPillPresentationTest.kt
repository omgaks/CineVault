package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStatusPillPresentationTest {

    @Test
    fun hardwarePlaybackStartsWithHwVideoAndRotatesCodecDetails() {
        val presentation = buildPlaybackStatusPillPresentation(
            snapshot(
                videoKind = ActiveVideoDecoderKind.HARDWARE,
                audioKind = ActiveAudioDecoderKind.PLATFORM,
            )
        )

        assertEquals("HW VIDEO", presentation.primaryLabel)
        assertFalse(presentation.emphasized)
        assertTrue(presentation.rotatingLabels.contains("HW VIDEO"))
        assertTrue(
            presentation.rotatingLabels.any {
                it.contains("HEVC") && it.contains("10-BIT")
            }
        )
    }

    @Test
    fun ffmpegAudioGetsDedicatedRescuePill() {
        val presentation = buildPlaybackStatusPillPresentation(
            snapshot(
                videoKind = ActiveVideoDecoderKind.HARDWARE,
                audioKind = ActiveAudioDecoderKind.FFMPEG,
                rescueOutcome = AudioFfmpegRescueOutcome.CONFIRMED,
            )
        )

        assertEquals("FFMPEG AUDIO", presentation.primaryLabel)
        assertTrue(presentation.emphasized)
        assertTrue(
            presentation.rotatingLabels.any {
                it.contains("DTS-HD") && it.contains("FFMPEG")
            }
        )
    }

    @Test
    fun softwareVideoAndFfmpegAudioBecomesMixedRescue() {
        val presentation = buildPlaybackStatusPillPresentation(
            snapshot(
                videoKind = ActiveVideoDecoderKind.SOFTWARE,
                audioKind = ActiveAudioDecoderKind.FFMPEG,
                decoderMode = PlaybackEngineMode.SOFTWARE,
                rescueOutcome = AudioFfmpegRescueOutcome.CONFIRMED,
            )
        )

        assertEquals("MIXED RESCUE", presentation.primaryLabel)
        assertTrue(presentation.emphasized)
    }

    @Test
    fun softwareVideoWithoutFfmpegAudioShowsSwVideo() {
        val presentation = buildPlaybackStatusPillPresentation(
            snapshot(
                videoKind = ActiveVideoDecoderKind.SOFTWARE,
                audioKind = ActiveAudioDecoderKind.PLATFORM,
                decoderMode = PlaybackEngineMode.SOFTWARE,
            )
        )

        assertEquals("SW VIDEO", presentation.primaryLabel)
        assertTrue(presentation.emphasized)
    }

    private fun snapshot(
        videoKind: ActiveVideoDecoderKind,
        audioKind: ActiveAudioDecoderKind,
        decoderMode: PlaybackEngineMode = PlaybackEngineMode.HARDWARE,
        rescueOutcome: AudioFfmpegRescueOutcome =
            AudioFfmpegRescueOutcome.NOT_ATTEMPTED,
    ): PlaybackDiagnosticsSnapshot =
        PlaybackDiagnosticsSnapshot(
            mimeType = "video/hevc",
            codecString = "hvc1.2.4.L153.B0",
            resolution = "3840×2160",
            frameRate = 23.976f,
            dynamicRange = VideoDynamicRange.HDR10_OR_PQ,
            decoderName = "video.decoder",
            decoderMode = decoderMode,
            activeDecoderKind = videoKind,
            compatibilityRisk = VideoCompatibilityRisk.LOW,
            decoderRecommendation =
                VideoDecoderRecommendation.PREFER_HARDWARE,
            fallbackOccurred = false,
            fallbackReason = null,
            audioMimeType = "audio/vnd.dts.hd",
            audioCodecString = "dtsh",
            audioLanguage = "eng",
            audioDecoderName = when (audioKind) {
                ActiveAudioDecoderKind.FFMPEG ->
                    "ffmpegAudioDecoder"
                ActiveAudioDecoderKind.PLATFORM ->
                    "c2.android.audio.decoder"
                ActiveAudioDecoderKind.UNKNOWN ->
                    null
            },
            activeAudioDecoderKind = audioKind,
            audioRoute = when (audioKind) {
                ActiveAudioDecoderKind.FFMPEG ->
                    PlaybackStreamRoute.AUDIO_FFMPEG_RESCUE_CANDIDATE
                ActiveAudioDecoderKind.PLATFORM ->
                    PlaybackStreamRoute.AUDIO_PLATFORM_OR_FFMPEG_EXTENSION
                ActiveAudioDecoderKind.UNKNOWN ->
                    null
            },
            mixedPipeline =
                videoKind == ActiveVideoDecoderKind.SOFTWARE &&
                    audioKind == ActiveAudioDecoderKind.FFMPEG,
            audioFfmpegRescueOutcome = rescueOutcome,
        )
}
