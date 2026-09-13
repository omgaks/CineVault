package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackCompatibilityAssessmentTest {

    @Test
    fun ordinaryFullySupportedHardwareStreamIsLowRisk() {
        val report = report(
            decoders = listOf(
                hardwareDecoder(formatSupported = true)
            ),
            profile = profile(
                width = 1920,
                height = 1080,
                frameRate = 24f,
                dynamicRange = VideoDynamicRange.SDR,
            ),
        )

        val result = assessVideoPlaybackCompatibility(report)

        assertEquals(VideoCompatibilityRisk.LOW, result.risk)
        assertEquals(
            VideoDecoderRecommendation.PREFER_HARDWARE,
            result.recommendation,
        )
    }

    @Test
    fun softwareOnlyFullSupportRequiresSoftware() {
        val report = report(
            decoders = listOf(
                softwareDecoder(formatSupported = true)
            ),
            profile = profile(),
        )

        val result = assessVideoPlaybackCompatibility(report)

        assertEquals(VideoCompatibilityRisk.HIGH, result.risk)
        assertEquals(
            VideoDecoderRecommendation.SOFTWARE_REQUIRED,
            result.recommendation,
        )
        assertTrue(
            VideoCompatibilityFactor.NO_FULL_HARDWARE_DECODER in
                result.factors
        )
    }

    @Test
    fun fourKHdrHardwareStreamStaysHardwareButIsElevatedRisk() {
        val report = report(
            decoders = listOf(
                hardwareDecoder(formatSupported = true)
            ),
            profile = profile(
                width = 3840,
                height = 2160,
                frameRate = 24f,
                dynamicRange = VideoDynamicRange.HDR10_OR_PQ,
            ),
        )

        val result = assessVideoPlaybackCompatibility(report)

        assertEquals(VideoCompatibilityRisk.ELEVATED, result.risk)
        assertEquals(
            VideoDecoderRecommendation.WATCH_NATIVE_CLOSELY,
            result.recommendation,
        )
        assertTrue(
            VideoCompatibilityFactor.ULTRA_HIGH_RESOLUTION in
                result.factors
        )
        assertTrue(VideoCompatibilityFactor.HDR in result.factors)
    }

    @Test
    fun highFrameRateHardwareStreamIsElevatedNotForcedToSoftware() {
        val report = report(
            decoders = listOf(
                hardwareDecoder(formatSupported = true)
            ),
            profile = profile(
                width = 1920,
                height = 1080,
                frameRate = 60f,
            ),
        )

        val result = assessVideoPlaybackCompatibility(report)

        assertEquals(VideoCompatibilityRisk.ELEVATED, result.risk)
        assertEquals(
            VideoDecoderRecommendation.WATCH_NATIVE_CLOSELY,
            result.recommendation,
        )
    }

    @Test
    fun functionalOnlyHardwareSupportIsHighRiskWithoutSoftwareRescue() {
        val report = report(
            status = VideoDecoderCapabilityStatus.FUNCTIONAL_ONLY,
            decoders = listOf(
                hardwareDecoder(
                    formatSupported = false,
                    functionallySupported = true,
                )
            ),
            profile = profile(),
        )

        val result = assessVideoPlaybackCompatibility(report)

        assertEquals(VideoCompatibilityRisk.HIGH, result.risk)
        assertEquals(
            VideoDecoderRecommendation.WATCH_NATIVE_CLOSELY,
            result.recommendation,
        )
        assertTrue(
            VideoCompatibilityFactor.FUNCTIONAL_ONLY_DECODER in
                result.factors
        )
    }

    @Test
    fun unknownReportStaysUnknown() {
        val result = assessVideoPlaybackCompatibility(null)

        assertEquals(VideoCompatibilityRisk.UNKNOWN, result.risk)
        assertEquals(
            VideoDecoderRecommendation.UNKNOWN,
            result.recommendation,
        )
    }

    private fun report(
        status: VideoDecoderCapabilityStatus =
            VideoDecoderCapabilityStatus.SUPPORTED,
        decoders: List<VideoDecoderCandidate>,
        profile: VideoStreamProfile,
    ) = VideoDecoderCapabilityReport(
        mimeType = profile.mimeType,
        status = status,
        decoders = decoders,
        streamProfile = profile,
    )

    private fun hardwareDecoder(
        formatSupported: Boolean,
        functionallySupported: Boolean = formatSupported,
    ) = VideoDecoderCandidate(
        name = "hardware.decoder",
        hardwareAccelerated = true,
        softwareOnly = false,
        formatSupported = formatSupported,
        functionallySupported = functionallySupported,
    )

    private fun softwareDecoder(
        formatSupported: Boolean,
        functionallySupported: Boolean = formatSupported,
    ) = VideoDecoderCandidate(
        name = "software.decoder",
        hardwareAccelerated = false,
        softwareOnly = true,
        formatSupported = formatSupported,
        functionallySupported = functionallySupported,
    )

    private fun profile(
        width: Int = 1920,
        height: Int = 1080,
        frameRate: Float = 24f,
        dynamicRange: VideoDynamicRange = VideoDynamicRange.SDR,
    ) = VideoStreamProfile(
        mimeType = "video/hevc",
        codecString = "test",
        width = width,
        height = height,
        frameRate = frameRate,
        dynamicRange = dynamicRange,
    )
}
