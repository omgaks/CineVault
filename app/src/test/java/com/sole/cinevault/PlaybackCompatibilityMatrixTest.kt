package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCompatibilityMatrixTest {

    @Test
    fun healthyNativeObservationBecomesNativePass() {
        val entry = buildPlaybackCompatibilityMatrixEntry(
            PlaybackCompatibilityTestCase("hevc-main10-4k-hdr", "HEVC Main10 4K HDR.mkv"),
            testDevice(),
            observation(PlaybackCompatibilityOutcome.NATIVE_HEALTHY),
        )
        assertEquals(PlaybackCompatibilityVerdict.PASS_NATIVE, entry.verdict)
    }

    @Test
    fun softwareRescueIsASeparateSuccessfulVerdict() {
        val entry = buildPlaybackCompatibilityMatrixEntry(
            PlaybackCompatibilityTestCase("hevc-main10-4k-hdr"),
            testDevice(),
            observation(
                outcome = PlaybackCompatibilityOutcome.SOFTWARE_RESCUED,
                decoderKind = ActiveVideoDecoderKind.SOFTWARE,
                fallbackOccurred = true,
                fallbackReason = PlaybackFallbackReason.DECODER_INIT_FAILED,
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
            PlaybackCompatibilityTestCase("av1-4k60"),
            testDevice(),
            observation(
                outcome = PlaybackCompatibilityOutcome.NATIVE_UNSTABLE,
                totalDroppedVideoFrames = 120,
                unhealthyDroppedFrameWindows = 2,
            ),
        )
        assertEquals(PlaybackCompatibilityVerdict.FAIL_UNSTABLE, entry.verdict)
    }

    @Test
    fun matrixRowContainsStableStreamAndRuntimeResult() {
        val entry = buildPlaybackCompatibilityMatrixEntry(
            PlaybackCompatibilityTestCase(
                "hevc-main10-4k-hdr",
                "HEVC Main10 4K HDR.mkv",
            ),
            testDevice(),
            observation(
                outcome = PlaybackCompatibilityOutcome.SOFTWARE_RESCUED,
                decoderName = "c2.android.hevc.decoder",
                decoderKind = ActiveVideoDecoderKind.SOFTWARE,
                fallbackOccurred = true,
                fallbackReason = PlaybackFallbackReason.EXCESSIVE_DROPPED_FRAMES,
                totalDroppedVideoFrames = 81,
                unhealthyDroppedFrameWindows = 2,
            ),
        )

        val row = formatPlaybackCompatibilityMatrixRow(entry)
        assertTrue(row.contains("hevc-main10-4k-hdr"))
        assertTrue(row.contains("HEVC"))
        assertTrue(row.contains("Main 10"))
        assertTrue(row.contains("L5.1"))
        assertTrue(row.contains("c2.android.hevc.decoder"))
        assertTrue(row.contains("SOFTWARE"))
        assertTrue(row.contains("PASS_SOFTWARE_RESCUE"))
    }

    @Test
    fun terminalFailureIsIncludedInRawTsvReport() {
        val entry = buildPlaybackCompatibilityMatrixEntry(
            PlaybackCompatibilityTestCase("dts-hd-terminal"),
            testDevice(),
            observation(
                outcome = PlaybackCompatibilityOutcome.NATIVE_UNSTABLE,
                terminalFailureStream = PlaybackFailureStreamKind.AUDIO,
                terminalFailureErrorCode = 4003,
                audioMimeType = "audio/vnd.dts.hd",
                audioCodecString = "dtsh",
            ),
        )

        val header = playbackCompatibilityMatrixHeader()
        val row = formatPlaybackCompatibilityMatrixRow(entry)
        val headerCells = header.split('\t')
        val rowCells = row.split('\t')

        assertEquals(headerCells.size, rowCells.size)
        assertTrue(header.contains("terminal_failure_stream"))
        assertTrue(header.contains("terminal_failure_error"))
        assertEquals(
            "AUDIO",
            rowCells[headerCells.indexOf("terminal_failure_stream")],
        )
        assertEquals(
            "4003",
            rowCells[headerCells.indexOf("terminal_failure_error")],
        )
    }

    @Test
    fun reportHasOneHeaderAndOneRowPerEntry() {
        val entry1 = buildPlaybackCompatibilityMatrixEntry(
            PlaybackCompatibilityTestCase("case-1"),
            testDevice(),
            observation(PlaybackCompatibilityOutcome.NATIVE_HEALTHY),
        )
        val entry2 = buildPlaybackCompatibilityMatrixEntry(
            PlaybackCompatibilityTestCase("case-2"),
            testDevice(),
            observation(PlaybackCompatibilityOutcome.STARTING),
        )

        val report = formatPlaybackCompatibilityMatrixReport(listOf(entry1, entry2))
        assertEquals(3, report.lines().size)
        assertTrue(report.lines().first().startsWith("test_id\tsource"))
        assertTrue(report.lines()[1].contains("case-1"))
        assertTrue(report.lines()[2].contains("case-2"))
    }

    @Test
    fun tabsAndNewlinesInFriendlyLabelsCannotBreakReportShape() {
        val entry = buildPlaybackCompatibilityMatrixEntry(
            PlaybackCompatibilityTestCase(
                "case-safe",
                "Odd\tFile\nName.mkv",
            ),
            testDevice(),
            observation(PlaybackCompatibilityOutcome.NATIVE_HEALTHY),
        )

        val report = formatPlaybackCompatibilityMatrixReport(listOf(entry))
        assertEquals(2, report.lines().size)
        assertTrue(report.lines()[1].contains("Odd File Name.mkv"))
    }

    private fun testDevice() =
        PlaybackCompatibilityDevice("Example", "Test Phone", 36)

    private fun observation(
        outcome: PlaybackCompatibilityOutcome,
        decoderName: String? = "c2.vendor.hevc.decoder",
        decoderKind: ActiveVideoDecoderKind = ActiveVideoDecoderKind.HARDWARE,
        fallbackOccurred: Boolean = false,
        fallbackReason: PlaybackFallbackReason? = null,
        totalDroppedVideoFrames: Int = 0,
        unhealthyDroppedFrameWindows: Int = 0,
        terminalFailureStream: PlaybackFailureStreamKind? = null,
        terminalFailureErrorCode: Int? = null,
        audioMimeType: String? = null,
        audioCodecString: String? = null,
    ) = PlaybackCompatibilityObservation(
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
        recommendation = VideoDecoderRecommendation.WATCH_NATIVE_CLOSELY,
        nativeReadiness = NativeVideoPlaybackReadiness.READY,
        softwareFallbackAvailable = true,
        fallbackOccurred = fallbackOccurred,
        fallbackReason = fallbackReason,
        totalDroppedVideoFrames = totalDroppedVideoFrames,
        unhealthyDroppedFrameWindows = unhealthyDroppedFrameWindows,
        audioMimeType = audioMimeType,
        audioCodecString = audioCodecString,
        terminalFailureStream = terminalFailureStream,
        terminalFailureErrorCode = terminalFailureErrorCode,
    )
}
