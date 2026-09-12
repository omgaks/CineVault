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
    fun labelsAreStableForUi() {
        assertEquals(
            "Decode failed",
            playbackFallbackReasonLabel(PlaybackFallbackReason.DECODING_FAILED),
        )
    }
}
