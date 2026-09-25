package com.sole.cinevault.glasses.stereo

import org.junit.Assert.assertEquals
import org.junit.Test

class StereoPlaybackSessionTest {
    @Test fun ordinaryVideoRemains2dOnExternalDisplay() {
        val session = StereoPlaybackSession("movie.mkv", "/movies/movie.mkv")
        assertEquals(StereoPlaybackMode.NORMAL_2D, session.resolve(true).mode)
    }

    @Test fun detectedSbsActivatesOnlyForExternalDisplay() {
        val session = StereoPlaybackSession("Avatar.3D.HSBS.mkv", "/movies/Avatar.3D.HSBS.mkv")
        assertEquals(StereoPlaybackMode.NORMAL_2D, session.resolve(false).mode)
        assertEquals(StereoPlaybackMode.SIDE_BY_SIDE, session.resolve(true).mode)
    }

    @Test fun dimensionsOnlyStrengthenExistingStereoDetection() {
        val session = StereoPlaybackSession("Avatar.3D.HSBS.mkv", "/movies/Avatar.3D.HSBS.mkv")
        assertEquals(StereoDetectionConfidence.WEAK, session.detected.confidence)
        session.updateVideoDimensions(3840, 1080)
        assertEquals(StereoDetectionConfidence.STRONG, session.detected.confidence)
    }

    @Test fun dimensionsNeverTurnOrdinaryMovieIntoStereo() {
        val session = StereoPlaybackSession("ordinary.mkv", "/movies/ordinary.mkv")
        session.updateVideoDimensions(3840, 1080)
        assertEquals(StereoPlaybackMode.NORMAL_2D, session.detected.mode)
    }

    @Test fun videoChangeClearsManualOverride() {
        val session = StereoPlaybackSession("movie.HSBS.mkv", "/movies/movie.HSBS.mkv")
        session.setUserOverride(StereoPlaybackMode.TOP_BOTTOM)
        session.onVideoChanged("next.mkv", "/movies/next.mkv")
        assertEquals(null, session.userOverride)
        assertEquals(StereoPlaybackMode.NORMAL_2D, session.resolve(true).mode)
    }
}
