package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegVideoPlaybackClockTest {

    @Test
    fun startPreservesPositionAndRunsClock() {
        val clock = FfmpegVideoPlaybackClockState()

        clock.start(12_345L)

        assertEquals(12_345L, clock.positionMs)
        assertTrue(clock.isRunning)
    }

    @Test
    fun negativeStartAndSeekAreClamped() {
        val clock = FfmpegVideoPlaybackClockState()

        clock.start(-100L)
        assertEquals(0L, clock.positionMs)

        clock.seekTo(-500L)
        assertEquals(0L, clock.positionMs)
    }

    @Test
    fun pauseAndResumeChangeRunningStateWithoutLosingPosition() {
        val clock = FfmpegVideoPlaybackClockState()
        clock.start(44_000L)

        clock.pause()
        assertFalse(clock.isRunning)
        assertEquals(44_000L, clock.positionMs)

        clock.resume()
        assertTrue(clock.isRunning)
        assertEquals(44_000L, clock.positionMs)
    }

    @Test
    fun stopClearsClockState() {
        val clock = FfmpegVideoPlaybackClockState()
        clock.start(88_000L)

        clock.stop()

        assertFalse(clock.isRunning)
        assertEquals(0L, clock.positionMs)
    }

    @Test
    fun forwardPresentationTimestampUpdatesClock() {
        val clock = FfmpegVideoPlaybackClockState()
        clock.start(10_000L)
        val timeline = FfmpegVideoTimelineCoordinator(clock)

        timeline.onPresentationTimestamp(
            FfmpegVideoPresentationTimestamp(10_500L),
        )

        assertEquals(10_500L, clock.positionMs)
    }

    @Test
    fun smallBackwardTimestampJitterIsIgnored() {
        val clock = FfmpegVideoPlaybackClockState()
        clock.start(10_000L)
        val timeline = FfmpegVideoTimelineCoordinator(
            clock = clock,
            backwardJitterToleranceMs = 250L,
        )

        timeline.onPresentationTimestamp(
            FfmpegVideoPresentationTimestamp(9_900L),
        )

        assertEquals(10_000L, clock.positionMs)
    }

    @Test
    fun largeBackwardTimestampDiscontinuityIsAccepted() {
        val clock = FfmpegVideoPlaybackClockState()
        clock.start(10_000L)
        val timeline = FfmpegVideoTimelineCoordinator(
            clock = clock,
            backwardJitterToleranceMs = 250L,
        )

        timeline.onPresentationTimestamp(
            FfmpegVideoPresentationTimestamp(8_000L),
        )

        assertEquals(8_000L, clock.positionMs)
    }

    @Test
    fun explicitSeekAlwaysUpdatesClock() {
        val clock = FfmpegVideoPlaybackClockState()
        clock.start(50_000L)
        val timeline = FfmpegVideoTimelineCoordinator(clock)

        timeline.onExplicitSeek(5_000L)

        assertEquals(5_000L, clock.positionMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativePresentationTimestampIsRejected() {
        FfmpegVideoPresentationTimestamp(-1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeJitterToleranceIsRejected() {
        FfmpegVideoTimelineCoordinator(
            clock = FfmpegVideoPlaybackClockState(),
            backwardJitterToleranceMs = -1L,
        )
    }
}
