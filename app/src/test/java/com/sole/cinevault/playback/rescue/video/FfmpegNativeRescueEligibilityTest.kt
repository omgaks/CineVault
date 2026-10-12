package com.sole.cinevault.playback.rescue.video

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegNativeRescueEligibilityTest {
    private val malformed = PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED

    @Test fun malformedLocalContainerWithNativeLibraryIsEligible() {
        assertTrue(FfmpegNativeRescueEligibility.shouldAttempt(malformed, "/storage/emulated/0/Movies/movie.mkv", true, false))
    }

    @Test fun missingNativeLibraryCannotTakeOver() {
        assertFalse(FfmpegNativeRescueEligibility.shouldAttempt(malformed, "/Movies/movie.mkv", false, false))
    }

    @Test fun unrelatedErrorsCannotTakeOver() {
        assertFalse(FfmpegNativeRescueEligibility.shouldAttempt(PlaybackException.ERROR_CODE_DECODING_FAILED, "/Movies/movie.mkv", true, false))
    }

    @Test fun repeatedRescueAttemptIsBlocked() {
        assertFalse(FfmpegNativeRescueEligibility.shouldAttempt(malformed, "/Movies/movie.mkv", true, true))
    }

    @Test fun remoteUriCannotTakeOver() {
        assertFalse(FfmpegNativeRescueEligibility.shouldAttempt(malformed, "https://example.com/movie.mkv", true, false))
    }
    @Test fun extractorBoundsFailureWrappedAsIoIsEligible() {
        val cause = ArrayIndexOutOfBoundsException("negative extractor offset").apply {
            stackTrace = arrayOf(StackTraceElement("androidx.media3.extractor.mkv.MatroskaExtractor", "read", "MatroskaExtractor.java", 100))
        }
        assertTrue(FfmpegNativeRescueEligibility.isContainerFailure(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, cause))
    }

    @Test fun unrelatedBoundsFailureIsNotAContainerFailure() {
        val cause = ArrayIndexOutOfBoundsException().apply {
            stackTrace = arrayOf(StackTraceElement("com.sole.cinevault.Other", "read", "Other.kt", 1))
        }
        assertFalse(FfmpegNativeRescueEligibility.isContainerFailure(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED, cause))
    }

    @Test fun ordinaryIoFailureIsNotAContainerFailure() {
        assertFalse(FfmpegNativeRescueEligibility.isContainerFailure(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED, java.io.IOException("unreadable")))
    }
}
