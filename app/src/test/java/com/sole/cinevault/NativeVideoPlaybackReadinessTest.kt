package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeVideoPlaybackReadinessTest {

    @Test
    fun fullySupportedHardwareDecoderIsReady() {
        val report = VideoDecoderCapabilityReport(
            mimeType = "video/hevc",
            status = VideoDecoderCapabilityStatus.SUPPORTED,
            decoders = listOf(
                VideoDecoderCandidate(
                    name = "c2.vendor.hevc.decoder",
                    hardwareAccelerated = true,
                    softwareOnly = false,
                    formatSupported = true,
                    functionallySupported = true,
                ),
            ),
        )

        assertEquals(
            NativeVideoPlaybackReadiness.READY,
            decideNativeVideoPlaybackReadiness(report),
        )
    }

    @Test
    fun softwareOnlyFullSupportNeedsSoftwareFallback() {
        val report = VideoDecoderCapabilityReport(
            mimeType = "video/hevc",
            status = VideoDecoderCapabilityStatus.SUPPORTED,
            decoders = listOf(
                VideoDecoderCandidate(
                    name = "c2.android.hevc.decoder",
                    hardwareAccelerated = false,
                    softwareOnly = true,
                    formatSupported = true,
                    functionallySupported = true,
                ),
            ),
        )

        assertEquals(
            NativeVideoPlaybackReadiness.SOFTWARE_FALLBACK_NEEDED,
            decideNativeVideoPlaybackReadiness(report),
        )
    }

    @Test
    fun functionalOnlyDecoderIsMarginal() {
        val report = VideoDecoderCapabilityReport(
            mimeType = "video/hevc",
            status = VideoDecoderCapabilityStatus.FUNCTIONAL_ONLY,
            decoders = listOf(
                VideoDecoderCandidate(
                    name = "c2.vendor.hevc.decoder",
                    hardwareAccelerated = true,
                    softwareOnly = false,
                    formatSupported = false,
                    functionallySupported = true,
                ),
            ),
        )

        assertEquals(
            NativeVideoPlaybackReadiness.MARGINAL,
            decideNativeVideoPlaybackReadiness(report),
        )
    }

    @Test
    fun noCompatibleDecoderNeedsFallbackPath() {
        val report = VideoDecoderCapabilityReport(
            mimeType = "video/av01",
            status = VideoDecoderCapabilityStatus.NO_COMPATIBLE_DECODER,
            decoders = emptyList(),
        )

        assertEquals(
            NativeVideoPlaybackReadiness.SOFTWARE_FALLBACK_NEEDED,
            decideNativeVideoPlaybackReadiness(report),
        )
    }

    @Test
    fun unknownReportStaysUnknown() {
        assertEquals(
            NativeVideoPlaybackReadiness.UNKNOWN,
            decideNativeVideoPlaybackReadiness(null),
        )
    }
}
