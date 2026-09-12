package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveVideoDecoderStatusTest {

    @Test
    fun matchedHardwareDecoderIsHardware() {
        val report = VideoDecoderCapabilityReport(
            mimeType = "video/avc",
            status = VideoDecoderCapabilityStatus.SUPPORTED,
            decoders = listOf(
                VideoDecoderCandidate(
                    name = "c2.qti.avc.decoder",
                    hardwareAccelerated = true,
                    softwareOnly = false,
                    formatSupported = true,
                    functionallySupported = true,
                ),
            ),
        )

        assertEquals(
            ActiveVideoDecoderKind.HARDWARE,
            classifyActiveVideoDecoder(
                decoderName = "c2.qti.avc.decoder",
                capabilityReport = report,
                engineMode = PlaybackEngineMode.HARDWARE,
            ),
        )
    }

    @Test
    fun matchedSoftwareDecoderIsSoftware() {
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
            ActiveVideoDecoderKind.SOFTWARE,
            classifyActiveVideoDecoder(
                decoderName = "c2.android.hevc.decoder",
                capabilityReport = report,
                engineMode = PlaybackEngineMode.HARDWARE,
            ),
        )
    }

    @Test
    fun softwareRecoveryModeStaysSoftwareWhenNameIsNotInReport() {
        assertEquals(
            ActiveVideoDecoderKind.SOFTWARE,
            classifyActiveVideoDecoder(
                decoderName = "unexpected.software.decoder",
                capabilityReport = null,
                engineMode = PlaybackEngineMode.SOFTWARE,
            ),
        )
    }

    @Test
    fun unknownNativeDecoderIsNotFalselyCalledHardware() {
        assertEquals(
            ActiveVideoDecoderKind.UNKNOWN,
            classifyActiveVideoDecoder(
                decoderName = "vendor.decoder.unknown",
                capabilityReport = null,
                engineMode = PlaybackEngineMode.HARDWARE,
            ),
        )
    }
}
