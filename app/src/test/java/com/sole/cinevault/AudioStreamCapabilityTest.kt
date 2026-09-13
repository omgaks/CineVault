package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioStreamCapabilityTest {

    @Test
    fun dtsHdIsIdentifiedAsFfmpegRescueCandidate() {
        val assessment = assessAudioStreamCapability(
            audio(
                mime = "audio/vnd.dts.hd",
                codecs = "dtsh",
            )
        )

        assertEquals(
            AudioCodecFamily.DTS_HD,
            assessment?.codecFamily,
        )
        assertEquals(
            AudioDecoderPreference.FFMPEG_RESCUE_CANDIDATE,
            assessment?.decoderPreference,
        )
        assertEquals("DTS-HD", assessment?.codecLabel)
    }

    @Test
    fun trueHdIsIdentifiedFromMlpaCodecString() {
        val assessment = assessAudioStreamCapability(
            audio(
                mime = null,
                codecs = "mlpa",
            )
        )

        assertEquals(
            AudioCodecFamily.TRUEHD,
            assessment?.codecFamily,
        )
        assertEquals(
            AudioDecoderPreference.FFMPEG_RESCUE_CANDIDATE,
            assessment?.decoderPreference,
        )
    }

    @Test
    fun eac3RemainsPlatformPreferred() {
        val assessment = assessAudioStreamCapability(
            audio(
                mime = "audio/eac3",
                codecs = "ec-3",
            )
        )

        assertEquals(
            AudioCodecFamily.EAC3,
            assessment?.codecFamily,
        )
        assertEquals(
            AudioDecoderPreference.PLATFORM_PREFERRED,
            assessment?.decoderPreference,
        )
    }

    @Test
    fun dtsAudioCreatesIndependentFfmpegRescueRoute() {
        val inventory = PlaybackStreamInventory(
            videoStreams = listOf(
                PlaybackStreamDescriptor(
                    kind = PlaybackStreamKind.VIDEO,
                    mimeType = "video/hevc",
                    codecString = "hvc1.2.4.L153.B0",
                    language = null,
                    selected = true,
                )
            ),
            audioStreams = listOf(
                audio(
                    mime = "audio/vnd.dts",
                    codecs = "dtsc",
                )
            ),
        )

        val plan = buildPlaybackStreamRoutingPlan(
            inventory = inventory,
            engineMode = PlaybackEngineMode.HARDWARE,
            videoCapabilityReport = null,
        )

        assertEquals(
            PlaybackStreamRoute.AUDIO_FFMPEG_RESCUE_CANDIDATE,
            plan.audioRoute,
        )
        assertEquals(
            AudioCodecFamily.DTS,
            plan.audioAssessment?.codecFamily,
        )
        assertTrue(plan.isMixedPipeline)
    }

    @Test
    fun nonAudioDescriptorIsNotAssessedAsAudio() {
        val assessment = assessAudioStreamCapability(
            PlaybackStreamDescriptor(
                kind = PlaybackStreamKind.VIDEO,
                mimeType = "video/hevc",
                codecString = "hvc1",
                language = null,
                selected = true,
            )
        )

        assertNull(assessment)
    }

    @Test
    fun unknownAudioIsPreservedWithoutInventingSupport() {
        val assessment = assessAudioStreamCapability(
            audio(
                mime = "audio/x-future-codec",
                codecs = "future",
            )
        )

        assertEquals(
            AudioCodecFamily.UNKNOWN,
            assessment?.codecFamily,
        )
        assertEquals(
            AudioDecoderPreference.UNKNOWN,
            assessment?.decoderPreference,
        )
        assertEquals("future", assessment?.codecLabel)
    }

    private fun audio(
        mime: String?,
        codecs: String?,
    ): PlaybackStreamDescriptor =
        PlaybackStreamDescriptor(
            kind = PlaybackStreamKind.AUDIO,
            mimeType = mime,
            codecString = codecs,
            language = "eng",
            selected = true,
        )
}
