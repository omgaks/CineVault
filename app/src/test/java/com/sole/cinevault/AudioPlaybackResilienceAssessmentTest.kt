package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlaybackResilienceAssessmentTest {

    @Test
    fun noSelectedAudioReturnsNone() {
        val result = assessAudioPlaybackResilience(
            selectedAudio = null,
            activeDecoder = ActiveAudioDecoderStatus(),
        )

        assertEquals(AudioPlaybackReadiness.NONE, result.readiness)
        assertFalse(result.rescueCandidate)
        assertFalse(result.rescuedByFfmpeg)
    }

    @Test
    fun dtsHdWithoutRuntimeDecoderExpectsFfmpegRescue() {
        val result = assessAudioPlaybackResilience(
            selectedAudio = audio(
                mimeType = "audio/vnd.dts.hd",
                codecString = "dtsh",
            ),
            activeDecoder = ActiveAudioDecoderStatus(),
        )

        assertEquals(
            AudioPlaybackReadiness.FFMPEG_RESCUE_EXPECTED,
            result.readiness,
        )
        assertEquals(AudioCodecFamily.DTS_HD, result.codecFamily)
        assertTrue(result.rescueCandidate)
        assertFalse(result.rescuedByFfmpeg)
    }

    @Test
    fun dtsHdWithPlatformDecoderDoesNotClaimFfmpegRescue() {
        val result = assessAudioPlaybackResilience(
            selectedAudio = audio(
                mimeType = "audio/vnd.dts.hd",
                codecString = "dtsh",
            ),
            activeDecoder = ActiveAudioDecoderStatus(
                kind = ActiveAudioDecoderKind.PLATFORM,
                decoderName = "c2.vendor.dts.decoder",
            ),
        )

        assertEquals(
            AudioPlaybackReadiness.PLATFORM_ACTIVE,
            result.readiness,
        )
        assertTrue(result.rescueCandidate)
        assertFalse(result.rescuedByFfmpeg)
    }

    @Test
    fun dtsHdWithFfmpegDecoderIsConfirmedRescue() {
        val result = assessAudioPlaybackResilience(
            selectedAudio = audio(
                mimeType = "audio/vnd.dts.hd",
                codecString = "dtsh",
            ),
            activeDecoder = ActiveAudioDecoderStatus(
                kind = ActiveAudioDecoderKind.FFMPEG,
                decoderName = "ffmpegAudioDecoder",
            ),
        )

        assertEquals(
            AudioPlaybackReadiness.FFMPEG_RESCUED,
            result.readiness,
        )
        assertTrue(result.rescueCandidate)
        assertTrue(result.rescuedByFfmpeg)
    }

    @Test
    fun commonAacWithoutRuntimeDecoderExpectsPlatform() {
        val result = assessAudioPlaybackResilience(
            selectedAudio = audio(
                mimeType = "audio/mp4a-latm",
                codecString = "mp4a.40.2",
            ),
            activeDecoder = ActiveAudioDecoderStatus(),
        )

        assertEquals(
            AudioPlaybackReadiness.PLATFORM_EXPECTED,
            result.readiness,
        )
        assertEquals(AudioCodecFamily.AAC, result.codecFamily)
        assertFalse(result.rescueCandidate)
    }

    @Test
    fun commonCodecCanStillReportActualFfmpegRuntimeTruth() {
        val result = assessAudioPlaybackResilience(
            selectedAudio = audio(
                mimeType = "audio/eac3",
                codecString = "ec-3",
            ),
            activeDecoder = ActiveAudioDecoderStatus(
                kind = ActiveAudioDecoderKind.FFMPEG,
                decoderName = "libavcodec.eac3",
            ),
        )

        assertEquals(
            AudioPlaybackReadiness.FFMPEG_RESCUED,
            result.readiness,
        )
        assertEquals(AudioCodecFamily.EAC3, result.codecFamily)
        assertFalse(result.rescueCandidate)
        assertTrue(result.rescuedByFfmpeg)
    }

    @Test
    fun unknownCodecWithoutDecoderStaysUnknown() {
        val result = assessAudioPlaybackResilience(
            selectedAudio = audio(
                mimeType = "audio/x-mystery",
                codecString = "mystery",
            ),
            activeDecoder = ActiveAudioDecoderStatus(),
        )

        assertEquals(
            AudioPlaybackReadiness.UNKNOWN,
            result.readiness,
        )
        assertEquals(AudioCodecFamily.UNKNOWN, result.codecFamily)
    }

    private fun audio(
        mimeType: String,
        codecString: String,
    ): PlaybackStreamDescriptor =
        PlaybackStreamDescriptor(
            kind = PlaybackStreamKind.AUDIO,
            mimeType = mimeType,
            codecString = codecString,
            language = "eng",
            selected = true,
        )
}
