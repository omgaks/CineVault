package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPipelineClassificationTest {

    @Test
    fun hardwareVideoAndPlatformAudioIsNative() {
        assertEquals(
            PlaybackPipelineKind.NATIVE,
            classifyPlaybackPipeline(
                observation(
                    videoKind = ActiveVideoDecoderKind.HARDWARE,
                    outcome = PlaybackCompatibilityOutcome.NATIVE_HEALTHY,
                    audioKind = ActiveAudioDecoderKind.PLATFORM,
                )
            ),
        )
    }

    @Test
    fun hardwareVideoAndFfmpegAudioIsAudioRescue() {
        assertEquals(
            PlaybackPipelineKind.AUDIO_FFMPEG_RESCUE,
            classifyPlaybackPipeline(
                observation(
                    videoKind = ActiveVideoDecoderKind.HARDWARE,
                    outcome = PlaybackCompatibilityOutcome.NATIVE_HEALTHY,
                    audioKind = ActiveAudioDecoderKind.FFMPEG,
                )
            ),
        )
    }

    @Test
    fun softwareVideoAndFfmpegAudioIsMixedRescue() {
        assertEquals(
            PlaybackPipelineKind.MIXED_VIDEO_AUDIO_RESCUE,
            classifyPlaybackPipeline(
                observation(
                    videoKind = ActiveVideoDecoderKind.SOFTWARE,
                    outcome = PlaybackCompatibilityOutcome.SOFTWARE_RESCUED,
                    audioKind = ActiveAudioDecoderKind.FFMPEG,
                )
            ),
        )
    }

    @Test
    fun reportSummaryCountsAudioAndMixedRescueSeparately() {
        val entries = listOf(
            entry(
                observation(
                    videoKind = ActiveVideoDecoderKind.HARDWARE,
                    outcome = PlaybackCompatibilityOutcome.NATIVE_HEALTHY,
                    audioKind = ActiveAudioDecoderKind.FFMPEG,
                ),
                PlaybackCompatibilityVerdict.PASS_NATIVE,
            ),
            entry(
                observation(
                    videoKind = ActiveVideoDecoderKind.SOFTWARE,
                    outcome = PlaybackCompatibilityOutcome.SOFTWARE_RESCUED,
                    audioKind = ActiveAudioDecoderKind.FFMPEG,
                ),
                PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE,
            ),
        )

        val summary = summarizePlaybackCompatibilityReport(entries)

        assertEquals(2, summary.audioRescues)
        assertEquals(1, summary.mixedPipelines)
        assertEquals(1, summary.nativePasses)
        assertEquals(1, summary.softwareRescues)
        assertTrue(
            playbackCompatibilitySummaryLine(summary)
                .contains("2 audio rescued")
        )
    }

    private fun observation(
        videoKind: ActiveVideoDecoderKind,
        outcome: PlaybackCompatibilityOutcome,
        audioKind: ActiveAudioDecoderKind,
    ): PlaybackCompatibilityObservation =
        PlaybackCompatibilityObservation(
            key = PlaybackCompatibilityKey(
                mimeType = "video/hevc",
                codecLabel = "HEVC",
                profileLabel = "Main 10",
                levelLabel = "L5.1",
                bitDepth = 10,
                resolution = "4K",
                frameRate = 23.976f,
                dynamicRange = VideoDynamicRange.HDR10_OR_PQ,
            ),
            outcome = outcome,
            decoderName = "video.decoder",
            decoderKind = videoKind,
            compatibilityRisk = VideoCompatibilityRisk.LOW,
            recommendation = VideoDecoderRecommendation.PREFER_HARDWARE,
            nativeReadiness = NativeVideoPlaybackReadiness.READY,
            softwareFallbackAvailable = true,
            fallbackOccurred =
                outcome == PlaybackCompatibilityOutcome.SOFTWARE_RESCUED,
            fallbackReason = null,
            totalDroppedVideoFrames = 0,
            unhealthyDroppedFrameWindows = 0,
            audioMimeType = "audio/vnd.dts.hd",
            audioCodecString = "dtsh",
            audioLanguage = "eng",
            audioDecoderName = when (audioKind) {
                ActiveAudioDecoderKind.FFMPEG -> "ffmpegAudioDecoder"
                ActiveAudioDecoderKind.PLATFORM -> "c2.android.audio.decoder"
                ActiveAudioDecoderKind.UNKNOWN -> null
            },
            audioDecoderKind = audioKind,
            audioRoute = when (audioKind) {
                ActiveAudioDecoderKind.FFMPEG ->
                    PlaybackStreamRoute.AUDIO_FFMPEG_RESCUE_CANDIDATE
                ActiveAudioDecoderKind.PLATFORM ->
                    PlaybackStreamRoute.AUDIO_PLATFORM_OR_FFMPEG_EXTENSION
                ActiveAudioDecoderKind.UNKNOWN -> null
            },
            mixedPipeline =
                videoKind == ActiveVideoDecoderKind.SOFTWARE &&
                    audioKind == ActiveAudioDecoderKind.FFMPEG,
        )

    private fun entry(
        observation: PlaybackCompatibilityObservation,
        verdict: PlaybackCompatibilityVerdict,
    ): PlaybackCompatibilityMatrixEntry =
        PlaybackCompatibilityMatrixEntry(
            testCase = PlaybackCompatibilityTestCase(
                testId = "case",
            ),
            device = PlaybackCompatibilityDevice(
                manufacturer = "Test",
                model = "Device",
                sdkInt = 36,
            ),
            observation = observation,
            verdict = verdict,
        )
}
