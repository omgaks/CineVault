package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeVideoPlaybackReadinessTest {

    @Test
    fun fullySupportedFormatIsReady() {
        val report = VideoDecoderCapabilityReport(
            mimeType = "video/hevc",
            status = VideoDecoderCapabilityStatus.SUPPORTED,
            decoders = emptyList(),
        )

        assertEquals(
            NativeVideoPlaybackReadiness.READY,
            decideNativeVideoPlaybackReadiness(report),
        )
    }

    @Test
    fun functionalOnlyFormatIsMarginal() {
        val report = VideoDecoderCapabilityReport(
            mimeType = "video/hevc",
            status = VideoDecoderCapabilityStatus.FUNCTIONAL_ONLY,
            decoders = emptyList(),
        )

        assertEquals(
            NativeVideoPlaybackReadiness.MARGINAL,
            decideNativeVideoPlaybackReadiness(report),
        )
    }

    @Test
    fun noCompatibleDecoderNeedsSoftwareFallback() {
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
