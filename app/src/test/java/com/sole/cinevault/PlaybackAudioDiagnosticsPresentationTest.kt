package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackAudioDiagnosticsPresentationTest {

    @Test
    fun ffmpegDtsHdRuntimeIsShownAsActualAudioDecoder() {
        val presentation = presentPlaybackDiagnostics(
            baseSnapshot(
                audioMimeType = "audio/vnd.dts.hd",
                audioCodecString = "dtsh",
                audioLanguage = "eng",
                audioDecoderName = "ffmpegAudioDecoder",
                activeAudioDecoderKind = ActiveAudioDecoderKind.FFMPEG,
                audioRoute =
                    PlaybackStreamRoute.AUDIO_FFMPEG_RESCUE_CANDIDATE,
                mixedPipeline = true,
            )
        )

        assertEquals("DTS-HD · ENG", presentation.audioSummary)
        assertEquals(
            "FFmpeg · ffmpegAudioDecoder",
            presentation.audioDecoderSummary,
        )
        assertEquals(
            "Mixed pipeline · audio rescue candidate",
            presentation.pipelineSummary,
        )
    }

    @Test
    fun platformEac3RuntimeIsShownWithoutPretendingFfmpeg() {
        val presentation = presentPlaybackDiagnostics(
            baseSnapshot(
                audioMimeType = "audio/eac3",
                audioCodecString = "ec-3",
                audioLanguage = "en",
                audioDecoderName = "c2.android.eac3.decoder",
                activeAudioDecoderKind = ActiveAudioDecoderKind.PLATFORM,
                audioRoute =
                    PlaybackStreamRoute.AUDIO_PLATFORM_OR_FFMPEG_EXTENSION,
                mixedPipeline = true,
            )
        )

        assertEquals("E-AC-3 · EN", presentation.audioSummary)
        assertEquals(
            "Platform · c2.android.eac3.decoder",
            presentation.audioDecoderSummary,
        )
        assertEquals(
            "Mixed pipeline · independent audio lane",
            presentation.pipelineSummary,
        )
    }

    @Test
    fun noSelectedAudioKeepsAudioDiagnosticsHidden() {
        val presentation = presentPlaybackDiagnostics(
            baseSnapshot()
        )

        assertNull(presentation.audioSummary)
        assertNull(presentation.audioDecoderSummary)
        assertNull(presentation.pipelineSummary)
    }

    @Test
    fun predictedFfmpegCandidateWithoutRuntimeDecoderStaysUnclaimed() {
        val presentation = presentPlaybackDiagnostics(
            baseSnapshot(
                audioMimeType = "audio/vnd.dts",
                audioCodecString = "dtsc",
                audioDecoderName = null,
                activeAudioDecoderKind = ActiveAudioDecoderKind.UNKNOWN,
                audioRoute =
                    PlaybackStreamRoute.AUDIO_FFMPEG_RESCUE_CANDIDATE,
                mixedPipeline = false,
            )
        )

        assertEquals("DTS", presentation.audioSummary)
        assertNull(presentation.audioDecoderSummary)
        assertEquals(
            "Audio rescue candidate",
            presentation.pipelineSummary,
        )
    }

    private fun baseSnapshot(
        audioMimeType: String? = null,
        audioCodecString: String? = null,
        audioLanguage: String? = null,
        audioDecoderName: String? = null,
        activeAudioDecoderKind: ActiveAudioDecoderKind =
            ActiveAudioDecoderKind.UNKNOWN,
        audioRoute: PlaybackStreamRoute? = null,
        mixedPipeline: Boolean = false,
    ): PlaybackDiagnosticsSnapshot =
        PlaybackDiagnosticsSnapshot(
            mimeType = "video/hevc",
            codecString = "hvc1.2.4.L153.B0",
            resolution = "4K",
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
            audioMimeType = audioMimeType,
            audioCodecString = audioCodecString,
            audioLanguage = audioLanguage,
            audioDecoderName = audioDecoderName,
            activeAudioDecoderKind = activeAudioDecoderKind,
            audioRoute = audioRoute,
            mixedPipeline = mixedPipeline,
        )
}
