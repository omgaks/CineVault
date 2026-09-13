package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackDiagnosticsLiveStatusTest {

    @Test
    fun healthyHardwarePlaybackShowsReadyAndRescueAvailability() {
        val status = buildPlaybackDiagnosticsLiveStatus(
            baseSnapshot(
                nativeReadiness = NativeVideoPlaybackReadiness.READY,
                softwareFallbackAvailable = true,
                firstVideoFrameRendered = true,
            )
        )

        assertEquals("Native decoder ready", status.readiness)
        assertEquals("SW rescue available", status.recovery)
        assertEquals(
            "First frame rendered · healthy",
            status.health,
        )
    }

    @Test
    fun unhealthyDroppedFrameWindowsAreVisible() {
        val status = buildPlaybackDiagnosticsLiveStatus(
            baseSnapshot(
                totalDroppedVideoFrames = 57,
                unhealthyDroppedFrameWindows = 2,
            )
        )

        assertEquals(
            "57 dropped · 2 unhealthy windows",
            status.health,
        )
    }

    @Test
    fun softwareFallbackReportsActiveReason() {
        val status = buildPlaybackDiagnosticsLiveStatus(
            baseSnapshot(
                fallbackOccurred = true,
                fallbackReason =
                    PlaybackFallbackReason.EXCESSIVE_DROPPED_FRAMES,
            )
        )

        assertEquals(
            "SW active · Hardware playback unstable",
            status.recovery,
        )
    }

    @Test
    fun unavailableSoftwareRescueIsNotOverstated() {
        val status = buildPlaybackDiagnosticsLiveStatus(
            baseSnapshot(
                softwareFallbackAvailable = false,
                nativeReadiness =
                    NativeVideoPlaybackReadiness.MARGINAL,
            )
        )

        assertEquals("Native decoder marginal", status.readiness)
        assertEquals(
            "No SW rescue for this stream",
            status.recovery,
        )
    }

    private fun baseSnapshot(
        nativeReadiness: NativeVideoPlaybackReadiness =
            NativeVideoPlaybackReadiness.UNKNOWN,
        softwareFallbackAvailable: Boolean = false,
        totalDroppedVideoFrames: Int = 0,
        unhealthyDroppedFrameWindows: Int = 0,
        startupPlaybackConfirmed: Boolean = false,
        firstVideoFrameRendered: Boolean = false,
        fallbackOccurred: Boolean = false,
        fallbackReason: PlaybackFallbackReason? = null,
    ): PlaybackDiagnosticsSnapshot =
        PlaybackDiagnosticsSnapshot(
            mimeType = "video/hevc",
            codecString = "hvc1",
            resolution = "4K",
            frameRate = 23.976f,
            dynamicRange = VideoDynamicRange.HDR10_OR_PQ,
            decoderName = "c2.vendor.hevc.decoder",
            decoderMode = if (fallbackOccurred) {
                PlaybackEngineMode.SOFTWARE
            } else {
                PlaybackEngineMode.HARDWARE
            },
            activeDecoderKind = if (fallbackOccurred) {
                ActiveVideoDecoderKind.SOFTWARE
            } else {
                ActiveVideoDecoderKind.HARDWARE
            },
            compatibilityRisk = VideoCompatibilityRisk.ELEVATED,
            decoderRecommendation =
                VideoDecoderRecommendation.WATCH_NATIVE_CLOSELY,
            fallbackOccurred = fallbackOccurred,
            fallbackReason = fallbackReason,
            nativeReadiness = nativeReadiness,
            softwareFallbackAvailable = softwareFallbackAvailable,
            totalDroppedVideoFrames = totalDroppedVideoFrames,
            unhealthyDroppedFrameWindows =
                unhealthyDroppedFrameWindows,
            startupPlaybackConfirmed = startupPlaybackConfirmed,
            firstVideoFrameRendered = firstVideoFrameRendered,
        )
}
