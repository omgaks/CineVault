package com.sole.cinevault

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFailureAttributionTest {

    @Test
    fun rendererVideoFailureIsAttributedToVideoLane() {
        val result = buildPlaybackFailureAttribution(
            rendererFailure = true,
            rendererTrackType = C.TRACK_TYPE_VIDEO,
            errorCode = 4003,
        )

        assertEquals(
            PlaybackFailureStreamKind.VIDEO,
            result.streamKind,
        )
        assertTrue(result.isVideoRendererFailure)
        assertFalse(result.isAudioRendererFailure)
        assertEquals("Video renderer", playbackFailureStreamLabel(result))
    }

    @Test
    fun rendererAudioFailureIsAttributedToAudioLane() {
        val result = buildPlaybackFailureAttribution(
            rendererFailure = true,
            rendererTrackType = C.TRACK_TYPE_AUDIO,
            errorCode = 4003,
        )

        assertEquals(
            PlaybackFailureStreamKind.AUDIO,
            result.streamKind,
        )
        assertTrue(result.isAudioRendererFailure)
        assertFalse(result.isVideoRendererFailure)
        assertEquals("Audio renderer", playbackFailureStreamLabel(result))
    }

    @Test
    fun rendererTextFailureDoesNotMasqueradeAsAudioOrVideo() {
        val result = buildPlaybackFailureAttribution(
            rendererFailure = true,
            rendererTrackType = C.TRACK_TYPE_TEXT,
            errorCode = 4003,
        )

        assertEquals(
            PlaybackFailureStreamKind.TEXT,
            result.streamKind,
        )
        assertFalse(result.isAudioRendererFailure)
        assertFalse(result.isVideoRendererFailure)
    }

    @Test
    fun nonRendererFailureStaysUnknownEvenWithTrackTypeHint() {
        val result = buildPlaybackFailureAttribution(
            rendererFailure = false,
            rendererTrackType = C.TRACK_TYPE_AUDIO,
            errorCode = 2001,
        )

        assertEquals(
            PlaybackFailureStreamKind.UNKNOWN,
            result.streamKind,
        )
        assertFalse(result.isAudioRendererFailure)
        assertFalse(result.isVideoRendererFailure)
        assertNull(result.rendererTrackType)
    }

    @Test
    fun unknownRendererTrackTypeStaysUnknown() {
        val result = buildPlaybackFailureAttribution(
            rendererFailure = true,
            rendererTrackType = C.TRACK_TYPE_UNKNOWN,
            errorCode = 4003,
        )

        assertEquals(
            PlaybackFailureStreamKind.UNKNOWN,
            result.streamKind,
        )
        assertTrue(result.rendererFailure)
    }
}
