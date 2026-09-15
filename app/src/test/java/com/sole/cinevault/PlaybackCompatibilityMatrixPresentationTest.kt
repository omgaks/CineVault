package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackCompatibilityMatrixPresentationTest {

    @Test
    fun groupsByCodecProfileAndBitDepth() {
        val groups = buildPlaybackCompatibilityMatrixGroups(
            listOf(
                entry("hevc-a", verdict = PlaybackCompatibilityVerdict.PASS_NATIVE),
                entry("hevc-b", verdict = PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE),
                entry(
                    "av1-a",
                    codec = "AV1",
                    profile = "Main",
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
                entry("pending", verdict = PlaybackCompatibilityVerdict.PENDING),
                entry("native", verdict = PlaybackCompatibilityVerdict.PASS_NATIVE),
                entry("rescued", verdict = PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE),
                entry("unstable", verdict = PlaybackCompatibilityVerdict.FAIL_UNSTABLE),
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
                    verdict = PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE,
                    decoderKind = ActiveVideoDecoderKind.SOFTWARE,
                    decoderName = "c2.android.hevc.decoder",
                )
            )
        ).single().entries.single()

        assertEquals("HEVC HDR Reference", row.sourceLabel)
        assertEquals("3840×2160 · 23.98 fps · HDR10/PQ", row.streamSummary)
        assertEquals("SW · c2.android.hevc.decoder", row.decoderSummary)
        assertEquals("RESCUED", playbackCompatibilityVerdictLabel(row.verdict))
        assertNull(row.failureSummary)
    }

    @Test
    fun terminalAudioFailureShowsCodecAndError() {
        val row = buildPlaybackCompatibilityMatrixGroups(
            listOf(
                entry(
                    id = "dts-hd-fail",
                    verdict = PlaybackCompatibilityVerdict.FAIL_UNSTABLE,
                    terminalFailureStream = PlaybackFailureStreamKind.AUDIO,
                    terminalFailureErrorCode = 4003,
                    audioMimeType = "audio/vnd.dts.hd",
                    audioCodecString = "dtsh",
                )
            )
        ).single().entries.single()

        assertEquals("AUDIO · DTS-HD · error 4003", row.failureSummary)
    }

    @Test
    fun terminalSubtitleFailureUsesFriendlyStreamLabel() {
        val row = buildPlaybackCompatibilityMatrixGroups(
            listOf(
                entry(
                    id = "subtitle-fail",
                    verdict = PlaybackCompatibilityVerdict.FAIL_UNSTABLE,
                    terminalFailureStream = PlaybackFailureStreamKind.TEXT,
                    terminalFailureErrorCode = 4003,
                )
            )
        ).single().entries.single()

        assertEquals("SUBTITLE · error 4003", row.failureSummary)
    }

    private fun entry(
        id: String,
        source: String = id,
        codec: String = "HEVC",
        profile: String = "Main 10",
        bitDepth: Int = 10,
        verdict: PlaybackCompatibilityVerdict,
        decoderKind: ActiveVideoDecoderKind = ActiveVideoDecoderKind.HARDWARE,
        decoderName: String = "c2.vendor.decoder",
        terminalFailureStream: PlaybackFailureStreamKind? = null,
        terminalFailureErrorCode: Int? = null,
        audioMimeType: String? = null,
        audioCodecString: String? = null,
    ): PlaybackCompatibilityMatrixEntry {
        val outcome = when (verdict) {
            PlaybackCompatibilityVerdict.PENDING -> PlaybackCompatibilityOutcome.STARTING
            PlaybackCompatibilityVerdict.PASS_NATIVE -> PlaybackCompatibilityOutcome.NATIVE_HEALTHY
            PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE -> PlaybackCompatibilityOutcome.SOFTWARE_RESCUED
            PlaybackCompatibilityVerdict.FAIL_UNSTABLE -> PlaybackCompatibilityOutcome.NATIVE_UNSTABLE
        }

        return PlaybackCompatibilityMatrixEntry(
            testCase = PlaybackCompatibilityTestCase(id, source),
            device = PlaybackCompatibilityDevice("Example", "Phone", 36),
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
                recommendation = VideoDecoderRecommendation.PREFER_HARDWARE,
                nativeReadiness = NativeVideoPlaybackReadiness.READY,
                softwareFallbackAvailable = true,
                fallbackOccurred = verdict == PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE,
                fallbackReason = if (verdict == PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE) {
                    PlaybackFallbackReason.DECODER_INIT_FAILED
                } else null,
                totalDroppedVideoFrames = 0,
                unhealthyDroppedFrameWindows = 0,
                audioMimeType = audioMimeType,
                audioCodecString = audioCodecString,
                terminalFailureStream = terminalFailureStream,
                terminalFailureErrorCode = terminalFailureErrorCode,
            ),
            verdict = verdict,
        )
    }
}
