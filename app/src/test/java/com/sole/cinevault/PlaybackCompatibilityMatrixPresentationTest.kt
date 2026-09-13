package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCompatibilityMatrixPresentationTest {

    @Test
    fun groupsByCodecProfileAndBitDepth() {
        val groups = buildPlaybackCompatibilityMatrixGroups(
            listOf(
                entry(
                    id = "hevc-a",
                    codec = "HEVC",
                    profile = "Main 10",
                    bitDepth = 10,
                    verdict = PlaybackCompatibilityVerdict.PASS_NATIVE,
                ),
                entry(
                    id = "hevc-b",
                    codec = "HEVC",
                    profile = "Main 10",
                    bitDepth = 10,
                    verdict =
                        PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE,
                ),
                entry(
                    id = "av1-a",
                    codec = "AV1",
                    profile = "Main",
                    bitDepth = 10,
                    verdict = PlaybackCompatibilityVerdict.PASS_NATIVE,
                ),
            )
        )

        assertEquals(2, groups.size)
        assertEquals("AV1 · Main · 10-bit", groups[0].title)
        assertEquals("HEVC · Main 10 · 10-bit", groups[1].title)
        assertEquals(2, groups[1].entries.size)
    }

    @Test
    fun unstableAndRescuedRowsSortBeforeNativeAndPending() {
        val group = buildPlaybackCompatibilityMatrixGroups(
            listOf(
                entry(
                    "pending",
                    verdict = PlaybackCompatibilityVerdict.PENDING,
                ),
                entry(
                    "native",
                    verdict = PlaybackCompatibilityVerdict.PASS_NATIVE,
                ),
                entry(
                    "rescued",
                    verdict =
                        PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE,
                ),
                entry(
                    "unstable",
                    verdict = PlaybackCompatibilityVerdict.FAIL_UNSTABLE,
                ),
            )
        ).single()

        assertEquals(
            listOf("unstable", "rescued", "native", "pending"),
            group.entries.map { it.testId },
        )
    }

    @Test
    fun rowPresentsStreamAndDecoderDetails() {
        val row = buildPlaybackCompatibilityMatrixGroups(
            listOf(
                entry(
                    id = "hevc-hdr",
                    source = "HEVC HDR Reference",
                    codec = "HEVC",
                    profile = "Main 10",
                    bitDepth = 10,
                    verdict =
                        PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE,
                    decoderKind = ActiveVideoDecoderKind.SOFTWARE,
                    decoderName = "c2.android.hevc.decoder",
                )
            )
        ).single().entries.single()

        assertEquals("HEVC HDR Reference", row.sourceLabel)
        assertEquals("3840×2160 · 23.98 fps · HDR10/PQ", row.streamSummary)
        assertEquals(
            "SW · c2.android.hevc.decoder",
            row.decoderSummary,
        )
        assertEquals(
            "RESCUED",
            playbackCompatibilityVerdictLabel(row.verdict),
        )
    }

    private fun entry(
        id: String,
        source: String = id,
        codec: String = "HEVC",
        profile: String = "Main 10",
        bitDepth: Int = 10,
        verdict: PlaybackCompatibilityVerdict,
        decoderKind: ActiveVideoDecoderKind =
            ActiveVideoDecoderKind.HARDWARE,
        decoderName: String = "c2.vendor.decoder",
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
                testId = id,
                sourceLabel = source,
            ),
            device = PlaybackCompatibilityDevice(
                manufacturer = "Example",
                model = "Phone",
                sdkInt = 36,
            ),
            observation = PlaybackCompatibilityObservation(
                key = PlaybackCompatibilityKey(
                    mimeType = "video/hevc",
                    codecLabel = codec,
                    profileLabel = profile,
                    levelLabel = "L5.1",
                    bitDepth = bitDepth,
                    resolution = "3840×2160",
                    frameRate = 23.976f,
                    dynamicRange = VideoDynamicRange.HDR10_OR_PQ,
                ),
                outcome = outcome,
                decoderName = decoderName,
                decoderKind = decoderKind,
                compatibilityRisk = VideoCompatibilityRisk.LOW,
                recommendation =
                    VideoDecoderRecommendation.PREFER_HARDWARE,
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
                totalDroppedVideoFrames = 0,
                unhealthyDroppedFrameWindows = 0,
            ),
            verdict = verdict,
        )
    }
}
