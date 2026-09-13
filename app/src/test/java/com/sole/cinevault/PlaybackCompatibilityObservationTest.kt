package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCompatibilityObservationTest {

    @Test
    fun sameStreamProducesSameKeyAcrossDifferentDecoders() {
        val hardware = buildPlaybackCompatibilityObservation(
            baseSnapshot(
                decoderName = "c2.qti.hevc.decoder",
                decoderMode = PlaybackEngineMode.HARDWARE,
                decoderKind = ActiveVideoDecoderKind.HARDWARE,
                firstVideoFrameRendered = true,
            )
        )

        val software = buildPlaybackCompatibilityObservation(
            baseSnapshot(
                decoderName = "c2.android.hevc.decoder",
                decoderMode = PlaybackEngineMode.SOFTWARE,
                decoderKind = ActiveVideoDecoderKind.SOFTWARE,
                fallbackOccurred = true,
            )
        )

        assertEquals(hardware.key, software.key)
        assertEquals("HEVC", hardware.key.codecLabel)
        assertEquals("Main 10", hardware.key.profileLabel)
        assertEquals(10, hardware.key.bitDepth)
        assertEquals("L5.1", hardware.key.levelLabel)
    }

    @Test
    fun healthyNativePlaybackIsClassifiedForMatrix() {
        val observation = buildPlaybackCompatibilityObservation(
            baseSnapshot(
                nativeReadiness = NativeVideoPlaybackReadiness.READY,
                firstVideoFrameRendered = true,
            )
        )

        assertEquals(
            PlaybackCompatibilityOutcome.NATIVE_HEALTHY,
            observation.outcome,
        )
    }

    @Test
    fun unhealthyNativePlaybackIsSeparatedFromHealthyNative() {
        val observation = buildPlaybackCompatibilityObservation(
            baseSnapshot(
                nativeReadiness = NativeVideoPlaybackReadiness.READY,
                firstVideoFrameRendered = true,
                totalDroppedVideoFrames = 81,
                unhealthyDroppedFrameWindows = 2,
            )
        )

        assertEquals(
            PlaybackCompatibilityOutcome.NATIVE_UNSTABLE,
            observation.outcome,
        )
        assertEquals(81, observation.totalDroppedVideoFrames)
        assertEquals(2, observation.unhealthyDroppedFrameWindows)
    }

    @Test
    fun softwareFallbackWinsOverEarlierNativeHealthSignals() {
        val observation = buildPlaybackCompatibilityObservation(
            baseSnapshot(
                decoderName = "c2.android.hevc.decoder",
                decoderMode = PlaybackEngineMode.SOFTWARE,
                decoderKind = ActiveVideoDecoderKind.SOFTWARE,
                fallbackOccurred = true,
                fallbackReason =
                    PlaybackFallbackReason.EXCESSIVE_DROPPED_FRAMES,
                firstVideoFrameRendered = true,
            )
        )

        assertEquals(
            PlaybackCompatibilityOutcome.SOFTWARE_RESCUED,
            observation.outcome,
        )
        assertEquals(
            PlaybackFallbackReason.EXCESSIVE_DROPPED_FRAMES,
            observation.fallbackReason,
        )
    }

    @Test
    fun marginalReadinessIsRecordedAsNativeUnstableBeforeFailure() {
        val observation = buildPlaybackCompatibilityObservation(
            baseSnapshot(
                nativeReadiness = NativeVideoPlaybackReadiness.MARGINAL,
                softwareFallbackAvailable = true,
            )
        )

        assertEquals(
            PlaybackCompatibilityOutcome.NATIVE_UNSTABLE,
            observation.outcome,
        )
    }

    @Test
    fun untouchedStartupSnapshotRemainsStarting() {
        val observation = buildPlaybackCompatibilityObservation(
            baseSnapshot()
        )

        assertEquals(
            PlaybackCompatibilityOutcome.STARTING,
            observation.outcome,
        )
    }

    private fun baseSnapshot(
        decoderName: String? = "c2.qti.hevc.decoder",
        decoderMode: PlaybackEngineMode = PlaybackEngineMode.HARDWARE,
        decoderKind: ActiveVideoDecoderKind =
            ActiveVideoDecoderKind.HARDWARE,
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
            codecString = "hvc1.2.4.L153.B0",
            resolution = "4K",
            frameRate = 23.976f,
            dynamicRange = VideoDynamicRange.HDR10_OR_PQ,
            decoderName = decoderName,
            decoderMode = decoderMode,
            activeDecoderKind = decoderKind,
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
