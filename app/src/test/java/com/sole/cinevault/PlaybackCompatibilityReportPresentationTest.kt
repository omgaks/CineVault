package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCompatibilityReportPresentationTest {

    @Test
    fun summarySeparatesNativeRescueFailureAndPending() {
        val entries = listOf(
            entry(PlaybackCompatibilityVerdict.PASS_NATIVE),
            entry(PlaybackCompatibilityVerdict.PASS_NATIVE),
            entry(PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE),
            entry(PlaybackCompatibilityVerdict.FAIL_UNSTABLE),
            entry(PlaybackCompatibilityVerdict.PENDING),
        )

        val summary = summarizePlaybackCompatibilityReport(entries)

        assertEquals(5, summary.total)
        assertEquals(4, summary.completed)
        assertEquals(2, summary.nativePasses)
        assertEquals(1, summary.softwareRescues)
        assertEquals(1, summary.unstableFailures)
        assertEquals(1, summary.pending)
    }

    @Test
    fun summaryLineStaysCompactAndOmitsZeroCategories() {
        val summary = PlaybackCompatibilityReportSummary(
            total = 3,
            nativePasses = 2,
            softwareRescues = 1,
            unstableFailures = 0,
            pending = 0,
        )

        assertEquals(
            "3/3 completed · 2 native · 1 rescued",
            playbackCompatibilitySummaryLine(summary),
        )
    }

    @Test
    fun emptyReportHasUsefulZeroSummary() {
        val summary = summarizePlaybackCompatibilityReport(emptyList())

        assertEquals(
            "0/0 completed",
            playbackCompatibilitySummaryLine(summary),
        )
    }

    private fun entry(
        verdict: PlaybackCompatibilityVerdict,
    ): PlaybackCompatibilityMatrixEntry {
        val outcome = when (verdict) {
            PlaybackCompatibilityVerdict.PENDING ->
                PlaybackCompatibilityOutcome.STARTING
            PlaybackCompatibilityVerdict.PASS_NATIVE ->
                PlaybackCompatibilityOutcome.NATIVE_HEALTHY
            PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE ->
                PlaybackCompatibilityOutcome.SOFTWARE_RESCUED
            PlaybackCompatibilityVerdict.FAIL_UNSTABLE ->
                PlaybackCompatibilityOutcome.NATIVE_UNSTABLE
        }

        val observation = PlaybackCompatibilityObservation(
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
            decoderName = "decoder",
            decoderKind = ActiveVideoDecoderKind.HARDWARE,
            compatibilityRisk = VideoCompatibilityRisk.LOW,
            recommendation =
                VideoDecoderRecommendation.PREFER_HARDWARE,
            nativeReadiness = NativeVideoPlaybackReadiness.READY,
            softwareFallbackAvailable = true,
            fallbackOccurred = false,
            fallbackReason = null,
            totalDroppedVideoFrames = 0,
            unhealthyDroppedFrameWindows = 0,
        )

        return PlaybackCompatibilityMatrixEntry(
            testCase = PlaybackCompatibilityTestCase("case-$verdict"),
            device = PlaybackCompatibilityDevice(
                manufacturer = "Example",
                model = "Phone",
                sdkInt = 36,
            ),
            observation = observation,
            verdict = verdict,
        )
    }
}
