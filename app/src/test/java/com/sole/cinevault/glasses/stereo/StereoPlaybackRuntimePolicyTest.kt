package com.sole.cinevault.glasses.stereo

import org.junit.Assert.assertEquals
import org.junit.Test

class StereoPlaybackRuntimePolicyTest {
    @Test fun externalDestinationUsesSessionDecision() {
        val session = StereoPlaybackSession("movie.HSBS.mkv", "/movie.HSBS.mkv")
        assertEquals(StereoPlaybackMode.SIDE_BY_SIDE, session.resolve(true).mode)
    }

    @Test fun hostDestinationKeepsSameSourceIn2d() {
        val session = StereoPlaybackSession("movie.HSBS.mkv", "/movie.HSBS.mkv")
        assertEquals(StereoPlaybackMode.NORMAL_2D, session.resolve(false).mode)
    }

    @Test fun sourceTransitionDropsPreviousOverride() {
        val session = StereoPlaybackSession("first.HSBS.mkv", "/first.HSBS.mkv")
        session.setUserOverride(StereoPlaybackMode.TOP_BOTTOM)
        session.onVideoChanged("second.mkv", "/second.mkv")
        assertEquals(StereoPlaybackMode.NORMAL_2D, session.resolve(true).mode)
    }
}
