package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackDiagnosticsPresentationTest {

    @Test
    fun hardwareHdrStreamGetsCompactReadableLabels() {
        val snapshot = PlaybackDiagnosticsSnapshot(
            mimeType = "video/hevc",
            codecString = "hvc1.2.4.L153.B0",
            resolution = "4K",
            frameRate = 23.976f,
            dynamicRange = VideoDynamicRange.HDR10_OR_PQ,
            decoderName = "c2.qti.hevc.decoder",
            decoderMode = PlaybackEngineMode.HARDWARE,
            activeDecoderKind = ActiveVideoDecoderKind.HARDWARE,
            compatibilityRisk = VideoCompatibilityRisk.ELEVATED,
            decoderRecommendation =
                VideoDecoderRecommendation.WATCH_NATIVE_CLOSELY,
            fallbackOccurred = false,
            fallbackReason = null,
        )

        val presentation = presentPlaybackDiagnostics(snapshot)

        assertEquals(
            "HEVC · 4K · HDR10/PQ · 23.98 fps",
            presentation.videoSummary,
        )
        assertEquals(
            "HW · c2.qti.hevc.decoder",
            presentation.decoderSummary,
        )
        assertEquals(
            "Compatibility: Elevated risk",
            presentation.compatibilitySummary,
        )
        assertNull(presentation.fallbackSummary)
    }

    @Test
    fun softwareFallbackShowsModeAndReason() {
        val snapshot = PlaybackDiagnosticsSnapshot(
            mimeType = "video/avc",
            codecString = "avc1.640028",
            resolution = "1080p",
            frameRate = 24f,
            dynamicRange = VideoDynamicRange.SDR,
            decoderName = "c2.android.avc.decoder",
            decoderMode = PlaybackEngineMode.SOFTWARE,
            activeDecoderKind = ActiveVideoDecoderKind.SOFTWARE,
            compatibilityRisk = VideoCompatibilityRisk.HIGH,
            decoderRecommendation =
                VideoDecoderRecommendation.SOFTWARE_REQUIRED,
            fallbackOccurred = true,
            fallbackReason = PlaybackFallbackReason.DECODER_INIT_FAILED,
        )

        val presentation = presentPlaybackDiagnostics(snapshot)

        assertEquals(
            "H.264 · 1080p · SDR · 24 fps",
            presentation.videoSummary,
        )
        assertEquals(
            "SW · c2.android.avc.decoder",
            presentation.decoderSummary,
        )
        assertEquals(
            "Compatibility: High risk",
            presentation.compatibilitySummary,
        )
        assertEquals(
            "Fallback: Decoder init failed",
            presentation.fallbackSummary,
        )
    }

    @Test
    fun unknownStreamStaysCleanInsteadOfInventingDetails() {
        val snapshot = PlaybackDiagnosticsSnapshot(
            mimeType = null,
            codecString = null,
            resolution = "Unknown",
            frameRate = null,
            dynamicRange = VideoDynamicRange.UNKNOWN,
            decoderName = null,
            decoderMode = PlaybackEngineMode.HARDWARE,
            activeDecoderKind = ActiveVideoDecoderKind.UNKNOWN,
            compatibilityRisk = VideoCompatibilityRisk.UNKNOWN,
            decoderRecommendation = VideoDecoderRecommendation.UNKNOWN,
            fallbackOccurred = false,
            fallbackReason = null,
        )

        val presentation = presentPlaybackDiagnostics(snapshot)

        assertEquals("Video stream unknown", presentation.videoSummary)
        assertEquals("HW", presentation.decoderSummary)
        assertEquals(
            "Compatibility: Unknown",
            presentation.compatibilitySummary,
        )
        assertNull(presentation.fallbackSummary)
    }

    @Test
    fun codecLabelsRecognizeCommonMedia3VideoFormats() {
        assertEquals(
            "HEVC",
            friendlyVideoCodecLabel("video/hevc", null),
        )
        assertEquals(
            "H.264",
            friendlyVideoCodecLabel("video/avc", null),
        )
        assertEquals(
            "AV1",
            friendlyVideoCodecLabel("video/av01", null),
        )
        assertEquals(
            "VP9",
            friendlyVideoCodecLabel("video/x-vnd.on2.vp9", null),
        )
    }
}
