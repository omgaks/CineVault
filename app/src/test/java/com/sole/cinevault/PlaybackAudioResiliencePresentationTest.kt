package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAudioResiliencePresentationTest {

    @Test
    fun expectedFfmpegRescueIsVisibleWithoutClaimingItAlreadyHappened() {
        val snapshot = snapshot(
            audioKind = ActiveAudioDecoderKind.UNKNOWN,
            audioDecoderName = null,
        )

        val diagnostics = presentPlaybackDiagnostics(snapshot)
        val pill = buildPlaybackStatusPillPresentation(snapshot)

        assertEquals(
            "FFmpeg rescue expected",
            diagnostics.audioResilienceSummary,
        )
        assertTrue(pill.rotatingLabels.contains("FFMPEG READY"))
        assertFalse(pill.rotatingLabels.contains("FFMPEG AUDIO"))
        assertTrue(pill.emphasized)
    }

    @Test
    fun actualFfmpegDecoderIsShownAsConfirmedRescue() {
        val snapshot = snapshot(
            audioKind = ActiveAudioDecoderKind.FFMPEG,
            audioDecoderName = "ffmpegAudioDecoder",
        )

        val diagnostics = presentPlaybackDiagnostics(snapshot)
        val pill = buildPlaybackStatusPillPresentation(snapshot)

        assertEquals(
            "FFmpeg audio rescued",
            diagnostics.audioResilienceSummary,
        )
        assertEquals("FFMPEG AUDIO", pill.primaryLabel)
        assertTrue(pill.emphasized)
    }

    @Test
    fun platformDecoderWinsOverFfmpegPrediction() {
        val snapshot = snapshot(
            audioKind = ActiveAudioDecoderKind.PLATFORM,
            audioDecoderName = "c2.vendor.dts.decoder",
        )

        val diagnostics = presentPlaybackDiagnostics(snapshot)
        val pill = buildPlaybackStatusPillPresentation(snapshot)

        assertEquals(
            "Platform audio active",
            diagnostics.audioResilienceSummary,
        )
        assertTrue(pill.rotatingLabels.contains("PLATFORM AUDIO"))
        assertFalse(pill.rotatingLabels.contains("FFMPEG READY"))
        assertFalse(pill.rotatingLabels.contains("FFMPEG AUDIO"))
    }

    private fun snapshot(
        audioKind: ActiveAudioDecoderKind,
        audioDecoderName: String?,
    ): PlaybackDiagnosticsSnapshot =
        PlaybackDiagnosticsSnapshot(
            mimeType = "video/hevc",
            codecString = "hvc1.2.4.L153.B0",
            resolution = "3840×2160",
            frameRate = 23.976f,
            dynamicRange = VideoDynamicRange.HDR10_OR_PQ,
            decoderName = "c2.qti.hevc.decoder",
            decoderMode = PlaybackEngineMode.HARDWARE,
            activeDecoderKind = ActiveVideoDecoderKind.HARDWARE,
            compatibilityRisk = VideoCompatibilityRisk.LOW,
            decoderRecommendation =
                VideoDecoderRecommendation.PREFER_HARDWARE,
            fallbackOccurred = false,
            fallbackReason = null,
            audioMimeType = "audio/vnd.dts.hd",
            audioCodecString = "dtsh",
            audioLanguage = "eng",
            audioDecoderName = audioDecoderName,
            activeAudioDecoderKind = audioKind,
            audioRoute =
                PlaybackStreamRoute.AUDIO_FFMPEG_RESCUE_CANDIDATE,
            mixedPipeline = false,
        )
}
