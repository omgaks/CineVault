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
    }

    @Test
    fun mimeTypeIdentifiesAudioRenderer() {
        assertEquals(
            C.TRACK_TYPE_AUDIO,
            inferRendererTrackType(
                sampleMimeType = "audio/vnd.dts.hd",
                rendererName = "FfmpegAudioRenderer",
            ),
        )
    }

    @Test
    fun mimeTypeIdentifiesVideoRenderer() {
        assertEquals(
            C.TRACK_TYPE_VIDEO,
            inferRendererTrackType(
                sampleMimeType = "video/hevc",
                rendererName = "MediaCodecVideoRenderer",
            ),
        )
    }

    @Test
    fun rendererNameIsFallbackWhenFormatIsMissing() {
        assertEquals(
            C.TRACK_TYPE_AUDIO,
            inferRendererTrackType(
                sampleMimeType = null,
                rendererName = "MediaCodecAudioRenderer",
            ),
        )
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
