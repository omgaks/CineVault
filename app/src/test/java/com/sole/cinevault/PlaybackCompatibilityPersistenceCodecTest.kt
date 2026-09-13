package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCompatibilityPersistenceCodecTest {

    @Test
    fun entriesRoundTripWithoutLosingCompatibilityDetails() {
        val original = entry(
            testId = "hevc-main10-4k",
            source = "HEVC\nMain10\\Reference",
            verdict =
                PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE,
        )

        val decoded = decodePlaybackCompatibilityEntries(
            encodePlaybackCompatibilityEntries(listOf(original))
        )

        assertEquals(listOf(original), decoded)
    }

    @Test
    fun emptyAndUnknownVersionsReturnEmptyList() {
        assertTrue(
            decodePlaybackCompatibilityEntries(null).isEmpty()
        )
        assertTrue(
            decodePlaybackCompatibilityEntries("").isEmpty()
        )
        assertTrue(
            decodePlaybackCompatibilityEntries(
                "CVCOMPAT999\nbad"
            ).isEmpty()
        )
    }

    @Test
    fun malformedRowsAreSkippedWithoutLosingValidRows() {
        val valid = entry(
            testId = "av1-4k60",
            source = "AV1 4K60",
            verdict = PlaybackCompatibilityVerdict.PASS_NATIVE,
        )

        val encoded = encodePlaybackCompatibilityEntries(
            listOf(valid)
        ) + "\nmalformed-row"

        val decoded = decodePlaybackCompatibilityEntries(encoded)

        assertEquals(listOf(valid), decoded)
    }

    private fun entry(
        testId: String,
        source: String,
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

        return PlaybackCompatibilityMatrixEntry(
            testCase = PlaybackCompatibilityTestCase(
                testId = testId,
                sourceLabel = source,
            ),
            device = PlaybackCompatibilityDevice(
                manufacturer = "Example",
                model = "Phone X",
                sdkInt = 36,
            ),
            observation = PlaybackCompatibilityObservation(
                key = PlaybackCompatibilityKey(
                    mimeType = "video/hevc",
                    codecLabel = "HEVC",
                    profileLabel = "Main 10",
                    levelLabel = "L5.1",
                    bitDepth = 10,
                    resolution = "3840×2160",
                    frameRate = 23.976f,
                    dynamicRange = VideoDynamicRange.HDR10_OR_PQ,
                ),
                outcome = outcome,
                decoderName = "c2.android.hevc.decoder",
                decoderKind = if (
                    verdict ==
                    PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE
                ) {
                    ActiveVideoDecoderKind.SOFTWARE
                } else {
                    ActiveVideoDecoderKind.HARDWARE
                },
                compatibilityRisk =
                    VideoCompatibilityRisk.ELEVATED,
                recommendation =
                    VideoDecoderRecommendation.WATCH_NATIVE_CLOSELY,
                nativeReadiness =
                    NativeVideoPlaybackReadiness.READY,
                softwareFallbackAvailable = true,
                fallbackOccurred =
                    verdict ==
                        PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE,
                fallbackReason = if (
                    verdict ==
                    PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE
                ) {
                    PlaybackFallbackReason.DECODER_INIT_FAILED
                } else {
                    null
                },
                totalDroppedVideoFrames = 7,
                unhealthyDroppedFrameWindows = 0,
            ),
            verdict = verdict,
        )
    }
}
