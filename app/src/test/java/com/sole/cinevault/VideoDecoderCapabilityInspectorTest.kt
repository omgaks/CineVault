package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoDecoderCapabilityInspectorTest {

    @Test
    fun fullySupportedDecoderWins() {
        val status = summarizeVideoDecoderCapability(
            listOf(
                VideoDecoderCandidate(
                    name = "decoder.one",
                    hardwareAccelerated = true,
                    softwareOnly = false,
                    formatSupported = false,
                    functionallySupported = true,
                ),
                VideoDecoderCandidate(
                    name = "decoder.two",
                    hardwareAccelerated = false,
                    softwareOnly = true,
                    formatSupported = true,
                    functionallySupported = true,
                ),
            )
        )

        assertEquals(VideoDecoderCapabilityStatus.SUPPORTED, status)
    }

    @Test
    fun functionalOnlyIsReportedSeparately() {
        val status = summarizeVideoDecoderCapability(
            listOf(
                VideoDecoderCandidate(
                    name = "decoder.one",
                    hardwareAccelerated = true,
                    softwareOnly = false,
                    formatSupported = false,
                    functionallySupported = true,
                )
            )
        )

        assertEquals(VideoDecoderCapabilityStatus.FUNCTIONAL_ONLY, status)
    }

    @Test
    fun decoderListWithoutSupportIsRejected() {
        val status = summarizeVideoDecoderCapability(
            listOf(
                VideoDecoderCandidate(
                    name = "decoder.one",
                    hardwareAccelerated = true,
                    softwareOnly = false,
                    formatSupported = false,
                    functionallySupported = false,
                )
            )
        )

        assertEquals(VideoDecoderCapabilityStatus.NO_COMPATIBLE_DECODER, status)
    }

    @Test
    fun emptyDecoderListMeansNoCompatibleDecoder() {
        assertEquals(
            VideoDecoderCapabilityStatus.NO_COMPATIBLE_DECODER,
            summarizeVideoDecoderCapability(emptyList()),
        )
    }
}
