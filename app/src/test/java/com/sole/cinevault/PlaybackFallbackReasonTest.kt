package com.sole.cinevault

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackFallbackReasonTest {

    @Test
    fun mapsDecoderInitFailure() {
        assertEquals(
            PlaybackFallbackReason.DECODER_INIT_FAILED,
            playbackFallbackReasonForErrorCode(
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
            ),
        )
    }

    @Test
    fun mapsUnsupportedFormatFailure() {
        assertEquals(
            PlaybackFallbackReason.FORMAT_UNSUPPORTED,
            playbackFallbackReasonForErrorCode(
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
            ),
        )
    }

    @Test
    fun unknownErrorFallsBackToGenericDecoderFailure() {
        assertEquals(
            PlaybackFallbackReason.UNKNOWN_DECODER_FAILURE,
            playbackFallbackReasonForErrorCode(Int.MIN_VALUE),
        )
    }

    @Test
    fun proactiveFallbackHasClearUiLabel() {
        assertEquals(
            "Native decoder unavailable",
            playbackFallbackReasonLabel(
                PlaybackFallbackReason.NATIVE_DECODER_UNAVAILABLE
            ),
        )
    }

    @Test
    fun droppedFrameFallbackHasClearUiLabel() {
        assertEquals(
            "Hardware playback unstable",
            playbackFallbackReasonLabel(
                PlaybackFallbackReason.EXCESSIVE_DROPPED_FRAMES
            ),
        )
    }

    @Test
    fun startupStallFallbackHasClearUiLabel() {
        assertEquals(
            "Hardware startup stalled",
            playbackFallbackReasonLabel(
                PlaybackFallbackReason.STARTUP_STALLED
            ),
        )
    }

    @Test
    fun missingFirstFrameFallbackHasClearUiLabel() {
        assertEquals(
            "Video frame not rendered",
            playbackFallbackReasonLabel(
                PlaybackFallbackReason.FIRST_VIDEO_FRAME_MISSING
            ),
        )
    }

    @Test
    fun labelsAreStableForUi() {
        assertEquals(
            "Decode failed",
            playbackFallbackReasonLabel(PlaybackFallbackReason.DECODING_FAILED),
        )
    }
}
