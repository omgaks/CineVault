package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareVideoFallbackAvailabilityTest {

    @Test
    fun supportedSoftwareDecoderMakesFallbackAvailable() {
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
                )
            ),
        )

        assertTrue(isPlatformSoftwareVideoFallbackAvailable(report))
    }

    @Test
    fun hardwareOnlySupportDoesNotPretendSoftwareFallbackExists() {
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
                )
            ),
        )

        assertFalse(isPlatformSoftwareVideoFallbackAvailable(report))
    }

    @Test
    fun unsupportedSoftwareDecoderIsNotEnough() {
        val report = VideoDecoderCapabilityReport(
            mimeType = "video/av01",
            status = VideoDecoderCapabilityStatus.NO_COMPATIBLE_DECODER,
            decoders = listOf(
                VideoDecoderCandidate(
                    name = "c2.android.av1.decoder",
                    hardwareAccelerated = false,
                    softwareOnly = true,
                    formatSupported = false,
                    functionallySupported = false,
                )
            ),
        )

        assertFalse(isPlatformSoftwareVideoFallbackAvailable(report))
    }

    @Test
    fun missingReportMeansFallbackUnavailable() {
        assertFalse(isPlatformSoftwareVideoFallbackAvailable(null))
    }
}
