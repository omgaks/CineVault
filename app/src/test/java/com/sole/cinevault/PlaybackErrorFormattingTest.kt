package com.sole.cinevault

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorFormattingTest {

    @Test
    fun fileNotFoundExplainsLikelyRecovery() {
        val message = friendlyPlaybackErrorForCode(
            errorCode = PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            errorCodeName = "ERROR_CODE_IO_FILE_NOT_FOUND",
        )

        assertTrue(message.contains("File not found"))
        assertTrue(message.contains("moved, renamed"))
        assertTrue(message.contains("ERROR_CODE_IO_FILE_NOT_FOUND"))
    }

    @Test
    fun unspecifiedIoIncludesUnderlyingCause() {
        val message = friendlyPlaybackErrorForCode(
            errorCode = PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            errorCodeName = "ERROR_CODE_IO_UNSPECIFIED",
            causeMessage = "USB drive disconnected",
        )

        assertTrue(message.contains("USB drive disconnected"))
    }

    @Test
    fun unknownErrorFallsBackToOriginalMessage() {
        val message = friendlyPlaybackErrorForCode(
            errorCode = PlaybackException.ERROR_CODE_UNSPECIFIED,
            errorCodeName = "ERROR_CODE_UNSPECIFIED",
            originalMessage = "Decoder reported a custom failure",
        )

        assertTrue(message.startsWith("Decoder reported a custom failure"))
        assertTrue(message.contains("ERROR_CODE_UNSPECIFIED"))
    }

    @Test
    fun onlyRecoverableFailuresAreMarkedTransient() {
        assertTrue(
            isTransientPlaybackErrorCode(
                PlaybackException
                    .ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            ),
        )

        assertTrue(
            isTransientPlaybackErrorCode(
                PlaybackException.ERROR_CODE_TIMEOUT,
            ),
        )

        assertFalse(
            isTransientPlaybackErrorCode(
                PlaybackException
                    .ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            ),
        )

        assertFalse(
            isTransientPlaybackErrorCode(
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            ),
        )
    }

    @Test
    fun ordinaryThrowableHasNoHttpStatusDetail() {
        val nested = IllegalArgumentException(
            "outer",
            IllegalStateException("inner"),
        )

        assertEquals(null, findHttpStatusDetail(nested))
    }
}
