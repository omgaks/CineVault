package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRescueExecutionPolicyTest {
    @Test fun noneExecutesNothing() {
        assertEquals(
            PlaybackRescueExecutionPlan(false, false),
            PlaybackRescueExecutionPolicy.forLane(PlaybackRescueLane.NONE),
        )
    }

    @Test fun softwareVideoExecutesOnlyVideoTransition() {
        assertEquals(
            PlaybackRescueExecutionPlan(true, false),
            PlaybackRescueExecutionPolicy.forLane(PlaybackRescueLane.SOFTWARE_VIDEO),
        )
    }

    @Test fun ffmpegAudioExecutesOnlyAudioTransition() {
        assertEquals(
            PlaybackRescueExecutionPlan(false, true),
            PlaybackRescueExecutionPolicy.forLane(PlaybackRescueLane.FFMPEG_AUDIO),
        )
    }

    @Test fun mixedExecutesBothIndependentTransitions() {
        assertEquals(
            PlaybackRescueExecutionPlan(true, true),
            PlaybackRescueExecutionPolicy.forLane(PlaybackRescueLane.MIXED),
        )
    }
}
