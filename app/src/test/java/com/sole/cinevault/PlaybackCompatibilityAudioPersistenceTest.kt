package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCompatibilityAudioPersistenceTest {

    @Test
    fun audioRuntimeRouteSurvivesPersistenceRoundTrip() {
        val entry = PlaybackCompatibilityMatrixEntry(
            testCase = PlaybackCompatibilityTestCase(
                testId = "dts-hd-mixed",
                sourceLabel = "DTS-HD mixed pipeline",
            ),
            device = PlaybackCompatibilityDevice(
                manufacturer = "Test",
                model = "Device",
                sdkInt = 36,
            ),
            observation = PlaybackCompatibilityObservation(
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
                outcome = PlaybackCompatibilityOutcome.NATIVE_HEALTHY,
                decoderName = "c2.qti.hevc.decoder",
                decoderKind = ActiveVideoDecoderKind.HARDWARE,
                compatibilityRisk = VideoCompatibilityRisk.LOW,
                recommendation = VideoDecoderRecommendation.PREFER_HARDWARE,
                nativeReadiness = NativeVideoPlaybackReadiness.READY,
                softwareFallbackAvailable = true,
                fallbackOccurred = false,
                fallbackReason = null,
                totalDroppedVideoFrames = 0,
                unhealthyDroppedFrameWindows = 0,
                audioMimeType = "audio/vnd.dts.hd",
                audioCodecString = "dtsh",
                audioLanguage = "eng",
                audioDecoderName = "ffmpegAudioDecoder",
                audioDecoderKind = ActiveAudioDecoderKind.FFMPEG,
                audioRoute =
                    PlaybackStreamRoute.AUDIO_FFMPEG_RESCUE_CANDIDATE,
                mixedPipeline = true,
            ),
            verdict = PlaybackCompatibilityVerdict.PASS_NATIVE,
        )

        val decoded = decodePlaybackCompatibilityEntries(
            encodePlaybackCompatibilityEntries(listOf(entry))
        ).single()

        assertEquals(
            "ffmpegAudioDecoder",
            decoded.observation.audioDecoderName,
        )
        assertEquals(
            ActiveAudioDecoderKind.FFMPEG,
            decoded.observation.audioDecoderKind,
        )
        assertEquals(
            PlaybackStreamRoute.AUDIO_FFMPEG_RESCUE_CANDIDATE,
            decoded.observation.audioRoute,
        )
        assertTrue(decoded.observation.mixedPipeline)
    }

    @Test
    fun matrixReportIncludesAudioRuntimeColumns() {
        val header = playbackCompatibilityMatrixHeader()

        assertTrue(header.contains("audio_codec"))
        assertTrue(header.contains("audio_decoder"))
        assertTrue(header.contains("audio_decoder_kind"))
        assertTrue(header.contains("audio_route"))
        assertTrue(header.contains("mixed_pipeline"))
    }
}
