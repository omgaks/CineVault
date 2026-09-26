package com.sole.cinevault

import androidx.media3.common.C
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlePlaybackRecoveryPolicyTest {

    @Test
    fun textRendererFailureRecoversByDisablingOnlySubtitles() {
        val attribution = PlaybackFailureAttribution(
            streamKind = PlaybackFailureStreamKind.TEXT,
            rendererFailure = true,
            rendererTrackType = C.TRACK_TYPE_TEXT,
            errorCode = 4003,
        )

        assertTrue(shouldRecoverByDisablingSubtitles(attribution))
    }

    @Test
    fun videoRendererFailureIsNotConsumedBySubtitleRecovery() {
        val attribution = PlaybackFailureAttribution(
            streamKind = PlaybackFailureStreamKind.VIDEO,
            rendererFailure = true,
            rendererTrackType = C.TRACK_TYPE_VIDEO,
            errorCode = 4003,
        )

        assertFalse(shouldRecoverByDisablingSubtitles(attribution))
    }

    @Test
    fun audioRendererFailureIsNotConsumedBySubtitleRecovery() {
        val attribution = PlaybackFailureAttribution(
            streamKind = PlaybackFailureStreamKind.AUDIO,
            rendererFailure = true,
            rendererTrackType = C.TRACK_TYPE_AUDIO,
            errorCode = 4003,
        )

        assertFalse(shouldRecoverByDisablingSubtitles(attribution))
    }

    @Test
    fun nonRendererFailureIsNotConsumedEvenWhenAttributedToText() {
        val attribution = PlaybackFailureAttribution(
            streamKind = PlaybackFailureStreamKind.TEXT,
            rendererFailure = false,
            rendererTrackType = C.TRACK_TYPE_TEXT,
            errorCode = 4003,
        )

        assertFalse(shouldRecoverByDisablingSubtitles(attribution))
    }
}
