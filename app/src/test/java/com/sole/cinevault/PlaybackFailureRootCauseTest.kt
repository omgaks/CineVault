package com.sole.cinevault

import androidx.media3.common.ParserException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PlaybackFailureRootCauseTest {

    private fun diagnostic(detail: String?) = PlaybackFailureDiagnostic(
        streamKind = PlaybackFailureStreamKind.UNKNOWN,
        severity = PlaybackFailureSeverity.TERMINAL,
        errorCode = 3001,
        recoveryAction = PlaybackRecoveryAction.FAIL,
        rendererFailure = false,
        detail = detail,
    )

    @Test
    fun parserExceptionMessageIsReported() {
        val parser = ParserException.createForMalformedContainer("Unexpected element ID 0x1F", null)
        val wrapper = RuntimeException(parser)
        val cause = playbackFailureRootCause(wrapper)!!
        // Media3 may decorate ParserException messages (e.g. contentIsMalformed / dataType),
        // so only the leading, human-readable part is asserted.
        assertTrue(cause, cause.startsWith("ParserException: Unexpected element ID 0x1F"))
    }

    @Test
    fun chainIsListedOuterToInnerWithoutDuplicates() {
        val inner = IOException("bad EBML size")
        val middle = IllegalStateException("bad EBML size", inner)
        val outer = RuntimeException("wrapper", middle)
        val cause = playbackFailureRootCause(outer)!!
        assertTrue(cause.startsWith("IllegalStateException: bad EBML size"))
        assertTrue(cause.contains(" ← IOException: bad EBML size"))
    }

    @Test
    fun noMessageAnywhereGivesNull() {
        assertNull(playbackFailureRootCause(RuntimeException(IOException())))
        assertNull(playbackFailureRootCause(RuntimeException("only the top-level message")))
    }

    @Test
    fun whitespaceIsCollapsedAndLongTextIsTruncated() {
        val long = "x".repeat(500)
        val cause = playbackFailureRootCause(RuntimeException(IOException("a\n  b\t$long")), maxLength = 60)!!
        assertEquals(60, cause.length)
        assertTrue(cause.endsWith("…"))
        assertFalse(cause.contains("\n"))
        assertTrue(cause.startsWith("IOException: a b "))
    }

    @Test
    fun shortSummaryStaysPillSafeAndDetailedSummaryAddsCause() {
        val withCause = diagnostic("ParserException: Unexpected element ID")
        assertEquals("Playback · failed · error 3001", playbackFailureDiagnosticSummary(withCause))
        assertEquals(
            "Playback · failed · error 3001\nCause: ParserException: Unexpected element ID",
            playbackFailureDiagnosticDetailedSummary(withCause),
        )
        assertEquals(
            "Playback · failed · error 3001",
            playbackFailureDiagnosticDetailedSummary(diagnostic(null)),
        )
    }

    @Test
    fun malformedContainerMessageNoLongerClaimsTheFileIsDownloading() {
        val text = friendlyPlaybackErrorForCode(
            errorCode = androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            errorCodeName = "ERROR_CODE_PARSING_CONTAINER_MALFORMED",
        )
        assertTrue(text.contains("playback report"))
        assertTrue(text.contains("ERROR_CODE_PARSING_CONTAINER_MALFORMED"))
        assertFalse(text.contains("Media3"))
    }
}
