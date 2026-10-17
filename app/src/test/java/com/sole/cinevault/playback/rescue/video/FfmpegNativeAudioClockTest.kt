package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Test

class FfmpegNativeAudioClockTest {
    @Test fun normalPlaybackUses48kHzFrames() {
        val clock = FfmpegNativeAudioClock()
        assertEquals(1000L, clock.positionMs(48000))
    }

    @Test fun signedPlaybackHeadDoesNotJumpBackwards() {
        val clock = FfmpegNativeAudioClock()
        val before = clock.positionMs(Int.MAX_VALUE)
        val after = clock.positionMs(Int.MIN_VALUE)
        assertEquals(before, after) // One frame is less than one millisecond.
        assertEquals(2147483648L, clock.playedFrames)
    }

    @Test fun wrappingPlaybackHeadKeepsLongMovieClockMonotonic() {
        val clock = FfmpegNativeAudioClock()
        clock.positionMs(-1)
        assertEquals(89478485L, clock.positionMs(0))
        assertEquals(4294967296L, clock.playedFrames)
    }

    @Test fun backwardSeekResetsCounterInsteadOfInventingWrap() {
        val clock = FfmpegNativeAudioClock()
        clock.positionMs(480000)
        clock.reset(2000L)
        assertEquals(2000L, clock.positionMs(0))
        assertEquals(2500L, clock.positionMs(24000))
    }

    @Test fun seekAfterWrapDiscardsPreviousEpoch() {
        val clock = FfmpegNativeAudioClock()
        clock.positionMs(-1)
        clock.positionMs(10)
        clock.reset(60000L)
        assertEquals(61000L, clock.positionMs(48000))
        assertEquals(48000L, clock.playedFrames)
    }

    @Test fun pauseKeepsTheSameMediaPosition() {
        val clock = FfmpegNativeAudioClock()
        clock.reset(30000L)
        assertEquals(31000L, clock.positionMs(48000))
        assertEquals(31000L, clock.positionMs(48000))
    }
}
