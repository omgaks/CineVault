package com.sole.cinevault.playback.rescue.video

import org.junit.Assert.assertEquals
import org.junit.Test

class FfmpegVideoClockPositionTest {
    @Test fun audioAheadOfOriginUsesMediaTime() {
        assertEquals(250L, FfmpegVideoClockPosition.resolve(1000L, 200L, 900L, 1250L))
    }

    @Test fun audioBehindSeekOriginFallsBackToMonotonicClock() {
        assertEquals(80L, FfmpegVideoClockPosition.resolve(5000L, 200L, 280L, 4800L))
    }

    @Test fun missingAudioUsesMonotonicClock() {
        assertEquals(45L, FfmpegVideoClockPosition.resolve(1000L, 200L, 245L, null))
    }

    @Test fun clockRollbackNeverProducesNegativeTime() {
        assertEquals(0L, FfmpegVideoClockPosition.resolve(1000L, 200L, 190L, null))
    }
    @Test fun videoTailKeepsTheLastAudioAnchorAtDoubleSpeed() {
        assertEquals(900L, FfmpegVideoClockPosition.resolve(1000L, 200L, 400L, null, 2f, 1500L))
    }

    @Test fun audioClockAlreadyIncludesSpeedAndMustNotBeMultipliedTwice() {
        assertEquals(500L, FfmpegVideoClockPosition.resolve(1000L, 200L, 400L, 1500L, 2f))
    }

    @Test fun invalidFallbackSpeedPreservesNormalTiming() {
        assertEquals(200L, FfmpegVideoClockPosition.resolve(1000L, 200L, 400L, null, Float.NaN))
    }
}
