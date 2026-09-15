package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCompatibilitySessionRecorderTest {
    private val device = PlaybackCompatibilityDevice(
        manufacturer = "Example",
        model = "Test Phone",
        sdkInt = 36,
    )

    @Test
    fun startingResultIsUpgradedToHealthyNativeResult() {
        val recorder = PlaybackCompatibilitySessionRecorder(device)
        val testCase = PlaybackCompatibilityTestCase("hevc-main10")
        recorder.recordObservation(
            testCase,
            observation(PlaybackCompatibilityOutcome.STARTING),
        )
        recorder.recordObservation(
            testCase,
            observation(PlaybackCompatibilityOutcome.NATIVE_HEALTHY),
        )
        assertEquals(
            PlaybackCompatibilityVerdict.PASS_NATIVE,
            recorder.entryFor("hevc-main10")?.verdict,
        )
    }

    @Test
    fun olderStartupSignalCannotDowngradeSoftwareRescue() {
        val recorder = PlaybackCompatibilitySessionRecorder(device)
        val testCase = PlaybackCompatibilityTestCase("av1-4k60")
        recorder.recordObservation(
            testCase,
            observation(
                PlaybackCompatibilityOutcome.SOFTWARE_RESCUED,
                decoderKind = ActiveVideoDecoderKind.SOFTWARE,
            ),
        )
        val selected = recorder.recordObservation(
            testCase,
            observation(PlaybackCompatibilityOutcome.STARTING),
        )
        assertEquals(
            PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE,
            selected.verdict,
        )
    }

    @Test
    fun equalUnstableOutcomeKeepsTerminalFailureEvidence() {
        val recorder = PlaybackCompatibilitySessionRecorder(device)
        val testCase = PlaybackCompatibilityTestCase("dts-hd-failure")
        recorder.recordObservation(
            testCase,
            observation(
                PlaybackCompatibilityOutcome.NATIVE_UNSTABLE,
                terminalFailureStream = PlaybackFailureStreamKind.AUDIO,
                terminalFailureErrorCode = 4003,
            ),
        )
        recorder.recordObservation(
            testCase,
            observation(
                PlaybackCompatibilityOutcome.NATIVE_UNSTABLE,
                droppedFrames = 90,
                unhealthyWindows = 2,
            ),
        )
        val saved = recorder.entryFor("dts-hd-failure")!!.observation
        assertEquals(PlaybackFailureStreamKind.AUDIO, saved.terminalFailureStream)
        assertEquals(4003, saved.terminalFailureErrorCode)
    }

    @Test
    fun equalUnstableOutcomeUpgradesToTerminalFailureEvidence() {
        val recorder = PlaybackCompatibilitySessionRecorder(device)
        val testCase = PlaybackCompatibilityTestCase("audio-terminal")
        recorder.recordObservation(
            testCase,
            observation(
                PlaybackCompatibilityOutcome.NATIVE_UNSTABLE,
                droppedFrames = 50,
                unhealthyWindows = 1,
            ),
        )
        recorder.recordObservation(
            testCase,
            observation(
                PlaybackCompatibilityOutcome.NATIVE_UNSTABLE,
                terminalFailureStream = PlaybackFailureStreamKind.AUDIO,
                terminalFailureErrorCode = 4003,
            ),
        )
        val saved = recorder.entryFor("audio-terminal")!!.observation
        assertEquals(PlaybackFailureStreamKind.AUDIO, saved.terminalFailureStream)
        assertEquals(4003, saved.terminalFailureErrorCode)
    }

    @Test
    fun equalOutcomeRefreshesRuntimeHealthDetailsWhenEvidenceIsEqual() {
        val recorder = PlaybackCompatibilitySessionRecorder(device)
        val testCase = PlaybackCompatibilityTestCase("vp9-profile2")
        recorder.recordObservation(
            testCase,
            observation(
                PlaybackCompatibilityOutcome.NATIVE_UNSTABLE,
                droppedFrames = 50,
                unhealthyWindows = 1,
            ),
        )
        recorder.recordObservation(
            testCase,
            observation(
                PlaybackCompatibilityOutcome.NATIVE_UNSTABLE,
                droppedFrames = 110,
                unhealthyWindows = 2,
            ),
        )
        val latest = recorder.entryFor("vp9-profile2")!!
        assertEquals(110, latest.observation.totalDroppedVideoFrames)
        assertEquals(2, latest.observation.unhealthyDroppedFrameWindows)
    }

    @Test
    fun softwareRescueCanUpgradeUnstableNativeResult() {
        val recorder = PlaybackCompatibilitySessionRecorder(device)
        val testCase = PlaybackCompatibilityTestCase("hevc-rescue")
        recorder.recordObservation(
            testCase,
            observation(PlaybackCompatibilityOutcome.NATIVE_UNSTABLE),
        )
        recorder.recordObservation(
            testCase,
            observation(
                PlaybackCompatibilityOutcome.SOFTWARE_RESCUED,
                decoderKind = ActiveVideoDecoderKind.SOFTWARE,
            ),
        )
        assertEquals(
            PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE,
            recorder.entryFor("hevc-rescue")?.verdict,
        )
    }

    @Test
    fun recorderKeepsOnlyConfiguredNumberOfLatestCases() {
        val recorder = PlaybackCompatibilitySessionRecorder(
            device = device,
            maxEntries = 2,
        )
        recorder.recordObservation(
            PlaybackCompatibilityTestCase("case-1"),
            observation(PlaybackCompatibilityOutcome.NATIVE_HEALTHY),
        )
        recorder.recordObservation(
            PlaybackCompatibilityTestCase("case-2"),
            observation(PlaybackCompatibilityOutcome.NATIVE_HEALTHY),
        )
        recorder.recordObservation(
            PlaybackCompatibilityTestCase("case-3"),
            observation(PlaybackCompatibilityOutcome.NATIVE_HEALTHY),
        )
        assertEquals(
            listOf("case-2", "case-3"),
            recorder.entries().map { it.testCase.testId },
        )
    }

    @Test
    fun reportUsesRecordedEntriesAndDeviceIdentity() {
        val recorder = PlaybackCompatibilitySessionRecorder(device)
        recorder.recordObservation(
            PlaybackCompatibilityTestCase("hevc-main10", "Sample.mkv"),
            observation(PlaybackCompatibilityOutcome.NATIVE_HEALTHY),
        )
        val report = recorder.report()
        assertTrue(report.contains("hevc-main10"))
        assertTrue(report.contains("Example Test Phone"))
        assertTrue(report.contains("PASS_NATIVE"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun recorderRejectsZeroCapacity() {
        PlaybackCompatibilitySessionRecorder(device, maxEntries = 0)
    }

    private fun observation(
        outcome: PlaybackCompatibilityOutcome,
        decoderKind: ActiveVideoDecoderKind = ActiveVideoDecoderKind.HARDWARE,
        droppedFrames: Int = 0,
        unhealthyWindows: Int = 0,
        terminalFailureStream: PlaybackFailureStreamKind? = null,
        terminalFailureErrorCode: Int? = null,
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
            decoderName = when (decoderKind) {
                ActiveVideoDecoderKind.HARDWARE -> "c2.vendor.hevc.decoder"
                ActiveVideoDecoderKind.SOFTWARE -> "c2.android.hevc.decoder"
                ActiveVideoDecoderKind.UNKNOWN -> null
            },
            decoderKind = decoderKind,
            compatibilityRisk = VideoCompatibilityRisk.ELEVATED,
            recommendation = VideoDecoderRecommendation.WATCH_NATIVE_CLOSELY,
            nativeReadiness = NativeVideoPlaybackReadiness.READY,
            softwareFallbackAvailable = true,
            fallbackOccurred =
                outcome == PlaybackCompatibilityOutcome.SOFTWARE_RESCUED,
            fallbackReason = if (
                outcome == PlaybackCompatibilityOutcome.SOFTWARE_RESCUED
            ) PlaybackFallbackReason.DECODER_INIT_FAILED else null,
            totalDroppedVideoFrames = droppedFrames,
            unhealthyDroppedFrameWindows = unhealthyWindows,
            terminalFailureStream = terminalFailureStream,
            terminalFailureErrorCode = terminalFailureErrorCode,
        )
}
