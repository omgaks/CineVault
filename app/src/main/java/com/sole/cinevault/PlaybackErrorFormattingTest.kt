package com.sole.cinevault

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorFormattingTest {

    @Test
    fun fileNotFoundExplainsLikelyRecovery() {
        val error = playbackError(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)

        val message = friendlyPlaybackError(error)

        assertTrue(message.contains("File not found"))
        assertTrue(message.contains("moved, renamed"))
        assertTrue(message.contains(error.errorCodeName))
    }

    @Test
    fun unspecifiedIoIncludesUnderlyingCause() {
        val error = playbackError(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            IllegalStateException("USB drive disconnected"),
        )

        val message = friendlyPlaybackError(error)

        assertTrue(message.contains("USB drive disconnected"))
    }

    @Test
    fun unknownErrorFallsBackToOriginalMessage() {
        val error = PlaybackException(
            "Decoder reported a custom failure",
            null,
            PlaybackException.ERROR_CODE_UNSPECIFIED,
        )

        val message = friendlyPlaybackError(error)

        assertTrue(message.startsWith("Decoder reported a custom failure"))
        assertTrue(message.contains(error.errorCodeName))
    }

    @Test
    fun onlyRecoverableFailuresAreMarkedTransient() {
        assertTrue(
            isTransientPlaybackError(
                playbackError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT),
            ),
        )
        assertTrue(
            isTransientPlaybackError(
                playbackError(PlaybackException.ERROR_CODE_TIMEOUT),
            ),
        )
        assertFalse(
            isTransientPlaybackError(
                playbackError(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED),
            ),
        )
        assertFalse(
            isTransientPlaybackError(
                playbackError(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND),
            ),
        )
    }

    @Test
    fun ordinaryThrowableHasNoHttpStatusDetail() {
        val nested = IllegalArgumentException("outer", IllegalStateException("inner"))

        assertEquals(null, findHttpStatusDetail(nested))
    }

    private fun playbackError(code: Int, cause: Throwable? = null): PlaybackException =
        PlaybackException("test failure", cause, code)
}
