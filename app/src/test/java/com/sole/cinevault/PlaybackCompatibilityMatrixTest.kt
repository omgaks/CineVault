package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCompatibilityMatrixTest {

    @Test
    fun healthyNativeObservationBecomesNativePass() {
        val entry = buildPlaybackCompatibilityMatrixEntry(
            testCase = PlaybackCompatibilityTestCase(
                testId = "hevc-main10-4k-hdr",
                sourceLabel = "HEVC Main10 4K HDR.mkv",
            ),
            device = testDevice(),
            observation = observation(
                outcome = PlaybackCompatibilityOutcome.NATIVE_HEALTHY,
            ),
        )

        assertEquals(
            PlaybackCompatibilityVerdict.PASS_NATIVE,
            entry.verdict,
        )
    }

    @Test
    fun softwareRescueIsASeparateSuccessfulVerdict() {
        val entry = buildPlaybackCompatibilityMatrixEntry(
            testCase = PlaybackCompatibilityTestCase(
                testId = "hevc-main10-4k-hdr",
            ),
            device = testDevice(),
            observation = observation(
                outcome = PlaybackCompatibilityOutcome.SOFTWARE_RESCUED,
                decoderKind = ActiveVideoDecoderKind.SOFTWARE,
                fallbackOccurred = true,
                fallbackReason =
                    PlaybackFallbackReason.DECODER_INITIALIZATION_FAILED,
            ),
        )

        assertEquals(
            PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE,
            entry.verdict,
        )
    }

    @Test
    fun unstableNativePlaybackDoesNotBecomeFalsePass() {
        val entry = buildPlaybackCompatibilityMatrixEntry(
            testCase = PlaybackCompatibilityTestCase(
                testId = "av1-4k60",
            ),
            device = testDevice(),
            observation = observation(
                outcome = PlaybackCompatibilityOutcome.NATIVE_UNSTABLE,
                totalDroppedVideoFrames = 120,
                unhealthyDroppedFrameWindows = 2,
            ),
        )

        assertEquals(
            PlaybackCompatibilityVerdict.FAIL_UNSTABLE,
            entry.verdict,
        )
    }

    @Test
    fun matrixRowContainsStableStreamAndRuntimeResult() {
        val entry = buildPlaybackCompatibilityMatrixEntry(
            testCase = PlaybackCompatibilityTestCase(
                testId = "hevc-main10-4k-hdr",
                sourceLabel = "HEVC Main10 4K HDR.mkv",
            ),
            device = testDevice(),
            observation = observation(
                outcome = PlaybackCompatibilityOutcome.SOFTWARE_RESCUED,
                decoderName = "c2.android.hevc.decoder",
                decoderKind = ActiveVideoDecoderKind.SOFTWARE,
                fallbackOccurred = true,
                fallbackReason =
                    PlaybackFallbackReason.EXCESSIVE_DROPPED_FRAMES,
                totalDroppedVideoFrames = 81,
                unhealthyDroppedFrameWindows = 2,
            ),
        )

        val row = formatPlaybackCompatibilityMatrixRow(entry)

        assertTrue(row.contains("hevc-main10-4k-hdr"))
        assertTrue(row.contains("HEVC"))
        assertTrue(row.contains("Main 10"))
        assertTrue(row.contains("10"))
        assertTrue(row.contains("L5.1"))
        assertTrue(row.contains("c2.android.hevc.decoder"))
        assertTrue(row.contains("SOFTWARE"))
        assertTrue(row.contains("PASS_SOFTWARE_RESCUE"))
    }

    @Test
    fun reportHasOneHeaderAndOneRowPerEntry() {
        val entry1 = buildPlaybackCompatibilityMatrixEntry(
            testCase = PlaybackCompatibilityTestCase("case-1"),
            device = testDevice(),
            observation = observation(
                outcome = PlaybackCompatibilityOutcome.NATIVE_HEALTHY,
            ),
        )

        val entry2 = buildPlaybackCompatibilityMatrixEntry(
            testCase = PlaybackCompatibilityTestCase("case-2"),
            device = testDevice(),
            observation = observation(
                outcome = PlaybackCompatibilityOutcome.STARTING,
            ),
        )

        val report = formatPlaybackCompatibilityMatrixReport(
            listOf(entry1, entry2)
        )

        assertEquals(3, report.lines().size)
        assertTrue(report.lines().first().startsWith("test_id\tsource"))
        assertTrue(report.lines()[1].contains("case-1"))
        assertTrue(report.lines()[2].contains("case-2"))
    }

    @Test
    fun tabsAndNewlinesInFriendlyLabelsCannotBreakReportShape() {
        val entry = buildPlaybackCompatibilityMatrixEntry(
            testCase = PlaybackCompatibilityTestCase(
                testId = "case-safe",
                sourceLabel = "Odd\tFile\nName.mkv",
            ),
            device = testDevice(),
            observation = observation(
                outcome = PlaybackCompatibilityOutcome.NATIVE_HEALTHY,
            ),
        )

        val report = formatPlaybackCompatibilityMatrixReport(listOf(entry))

        assertEquals(2, report.lines().size)
        assertTrue(report.lines()[1].contains("Odd File Name.mkv"))
    }

    private fun testDevice(): PlaybackCompatibilityDevice =
        PlaybackCompatibilityDevice(
            manufacturer = "Example",
            model = "Test Phone",
            sdkInt = 36,
        )

    private fun observation(
        outcome: PlaybackCompatibilityOutcome,
        decoderName: String? = "c2.vendor.hevc.decoder",
        decoderKind: ActiveVideoDecoderKind =
            ActiveVideoDecoderKind.HARDWARE,
        fallbackOccurred: Boolean = false,
        fallbackReason: PlaybackFallbackReason? = null,
        totalDroppedVideoFrames: Int = 0,
        unhealthyDroppedFrameWindows: Int = 0,
    ): PlaybackCompatibilityObservation =
        PlaybackCompatibilityObservation(
            key = PlaybackCompatibilityKey(
                mimeType = "video/hevc",
                codecLabel = "HEVC",
                profileLabel = "Main 10",
                levelLabel = "L5.1",
                bitDepth = 10,
                resolution = "4K",
                frameRate = 23.976f,
                dynamicRange = VideoDynamicRange.HDR10_OR_PQ,
            ),
            outcome = outcome,
            decoderName = decoderName,
            decoderKind = decoderKind,
            compatibilityRisk = VideoCompatibilityRisk.ELEVATED,
            recommendation =
                VideoDecoderRecommendation.WATCH_NATIVE_CLOSELY,
            nativeReadiness = NativeVideoPlaybackReadiness.READY,
            softwareFallbackAvailable = true,
            fallbackOccurred = fallbackOccurred,
            fallbackReason = fallbackReason,
            totalDroppedVideoFrames = totalDroppedVideoFrames,
            unhealthyDroppedFrameWindows =
                unhealthyDroppedFrameWindows,
        )
}
