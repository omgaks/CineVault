package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackVerifiedAudioRescuePillTest {

    @Test
    fun pendingRescueShowsSwitchNotSuccess() {
        val presentation = buildPlaybackStatusPillPresentation(
            snapshot(AudioFfmpegRescueOutcome.PENDING, ActiveAudioDecoderKind.UNKNOWN)
        )
        assertEquals("FFMPEG SWITCH", presentation.primaryLabel)
        assertFalse(presentation.rotatingLabels.contains("FFMPEG AUDIO"))
        assertTrue(presentation.emphasized)
    }

    @Test
    fun confirmedFfmpegShowsFfmpegAudio() {
        val presentation = buildPlaybackStatusPillPresentation(
            snapshot(AudioFfmpegRescueOutcome.CONFIRMED, ActiveAudioDecoderKind.FFMPEG)
        )
        assertEquals("FFMPEG AUDIO", presentation.primaryLabel)
        assertTrue(presentation.emphasized)
    }

    @Test
    fun failedRescueShowsAudioFailed() {
        val presentation = buildPlaybackStatusPillPresentation(
            snapshot(AudioFfmpegRescueOutcome.FAILED, ActiveAudioDecoderKind.UNKNOWN)
        )
        assertEquals("AUDIO FAILED", presentation.primaryLabel)
        assertTrue(presentation.emphasized)
    }

    private fun snapshot(
        outcome: AudioFfmpegRescueOutcome,
        audioKind: ActiveAudioDecoderKind,
    ) = PlaybackDiagnosticsSnapshot(
        mimeType = "video/avc",
        codecString = "avc1",
        resolution = "1920×1080",
        frameRate = 24f,
        dynamicRange = VideoDynamicRange.SDR,
        decoderName = "c2.qti.avc.decoder",
        decoderMode = PlaybackEngineMode.HARDWARE,
        activeDecoderKind = ActiveVideoDecoderKind.HARDWARE,
        compatibilityRisk = VideoCompatibilityRisk.LOW,
        decoderRecommendation = VideoDecoderRecommendation.HARDWARE_PREFERRED,
        fallbackOccurred = false,
        fallbackReason = null,
        audioMimeType = "audio/vnd.dts",
        audioCodecString = "dts",
        audioDecoderName = if (audioKind == ActiveAudioDecoderKind.FFMPEG) "ffmpeg" else null,
        activeAudioDecoderKind = audioKind,
        audioFfmpegRescueOutcome = outcome,
    )
}
