package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackHealthThresholdsTest {

    @Test
    fun lowRiskUsesConservativeThresholds() {
        val thresholds = playbackHealthThresholdsFor(
            assessment(VideoCompatibilityRisk.LOW)
        )

        assertEquals(
            ConservativePlaybackHealthThresholds,
            thresholds,
        )
    }

    @Test
    fun unknownRiskStaysConservative() {
        val thresholds = playbackHealthThresholdsFor(
            assessment(VideoCompatibilityRisk.UNKNOWN)
        )

        assertEquals(
            ConservativePlaybackHealthThresholds,
            thresholds,
        )
    }

    @Test
    fun elevatedRiskIsWatchedEarlierButStillRequiresRepeatedDropWindows() {
        val thresholds = playbackHealthThresholdsFor(
            assessment(VideoCompatibilityRisk.ELEVATED)
        )

        assertTrue(
            thresholds.firstFrameTimeoutMs <
                ConservativePlaybackHealthThresholds.firstFrameTimeoutMs
        )
        assertTrue(
            thresholds.startupStallTimeoutMs <
                ConservativePlaybackHealthThresholds.startupStallTimeoutMs
        )
        assertEquals(
            2,
            thresholds.requiredUnhealthyDroppedFrameWindows,
        )
    }

    @Test
    fun highRiskDoesNotBecomeSingleBurstFallback() {
        val thresholds = playbackHealthThresholdsFor(
            assessment(VideoCompatibilityRisk.HIGH)
        )

        assertEquals(
            2,
            thresholds.requiredUnhealthyDroppedFrameWindows,
        )
    }

    private fun assessment(
        risk: VideoCompatibilityRisk,
    ) = VideoPlaybackCompatibilityAssessment(
        risk = risk,
        recommendation = VideoDecoderRecommendation.WATCH_NATIVE_CLOSELY,
        factors = emptySet(),
    )
}
