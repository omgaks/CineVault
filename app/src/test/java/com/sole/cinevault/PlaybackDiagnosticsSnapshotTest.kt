package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDiagnosticsSnapshotTest {

    @Test
    fun snapshotContainsStreamDecoderAndRiskInformation() {
        val state = PlayerPlaybackRecoveryState().apply {
            engineMode = PlaybackEngineMode.HARDWARE
            videoDecoderCapabilityReport = VideoDecoderCapabilityReport(
                mimeType = "video/hevc",
                status = VideoDecoderCapabilityStatus.SUPPORTED,
                decoders = listOf(
                    VideoDecoderCandidate(
                        name = "hardware.hevc",
                        hardwareAccelerated = true,
                        softwareOnly = false,
                        formatSupported = true,
                        functionallySupported = true,
                    )
                ),
                streamProfile = VideoStreamProfile(
                    mimeType = "video/hevc",
                    codecString = "hvc1.2.4.L153.B0",
                    width = 3840,
                    height = 2160,
                    frameRate = 23.976f,
                    dynamicRange = VideoDynamicRange.HDR10_OR_PQ,
                ),
            )
            activeVideoDecoderStatus = ActiveVideoDecoderStatus(
                decoderName = "c2.vendor.hevc.decoder",
                kind = ActiveVideoDecoderKind.HARDWARE,
            )
        }

        val snapshot = buildPlaybackDiagnosticsSnapshot(state)

        assertEquals("video/hevc", snapshot.mimeType)
        assertEquals("hvc1.2.4.L153.B0", snapshot.codecString)
        assertEquals("4K", snapshot.resolution)
        assertEquals(23.976f, snapshot.frameRate)
        assertEquals(
            VideoDynamicRange.HDR10_OR_PQ,
            snapshot.dynamicRange,
        )
        assertEquals(
            "c2.vendor.hevc.decoder",
            snapshot.decoderName,
        )
        assertEquals(
            ActiveVideoDecoderKind.HARDWARE,
            snapshot.activeDecoderKind,
        )
        assertEquals(
            VideoCompatibilityRisk.ELEVATED,
            snapshot.compatibilityRisk,
        )
        assertFalse(snapshot.fallbackOccurred)
        assertNull(snapshot.fallbackReason)
    }

    @Test
    fun snapshotShowsSoftwareFallbackAndReason() {
        val state = PlayerPlaybackRecoveryState().apply {
            engineMode = PlaybackEngineMode.SOFTWARE
            fallbackOccurred = true
            fallbackReason = PlaybackFallbackReason.DECODER_INIT_FAILED
            activeVideoDecoderStatus = ActiveVideoDecoderStatus(
                decoderName = "c2.android.hevc.decoder",
                kind = ActiveVideoDecoderKind.SOFTWARE,
            )
        }

        val snapshot = buildPlaybackDiagnosticsSnapshot(state)

        assertEquals(
            PlaybackEngineMode.SOFTWARE,
            snapshot.decoderMode,
        )
        assertEquals(
            ActiveVideoDecoderKind.SOFTWARE,
            snapshot.activeDecoderKind,
        )
        assertTrue(snapshot.fallbackOccurred)
        assertEquals(
            PlaybackFallbackReason.DECODER_INIT_FAILED,
            snapshot.fallbackReason,
        )
        assertEquals("Unknown", snapshot.resolution)
        assertEquals(
            VideoCompatibilityRisk.UNKNOWN,
            snapshot.compatibilityRisk,
        )
    }
}
