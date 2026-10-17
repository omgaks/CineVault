package com.sole.cinevault.playback.rescue.video

import androidx.media3.common.PlaybackException

/**
 * A narrow gate for native container rescue. Do not take over for network,
 * DRM, audio-only, or unrelated decoder errors.
 */
internal object FfmpegNativeRescueEligibility {
    /** Media3 can wrap extractor bounds failures as IO_UNSPECIFIED (2000). */
    fun isContainerFailure(error: PlaybackException): Boolean =
        isContainerFailure(error.errorCode, error.cause)

    fun isContainerFailure(errorCode: Int, failureCause: Throwable?): Boolean {
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED) return true
        if (errorCode != PlaybackException.ERROR_CODE_IO_UNSPECIFIED) return false
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        var cause: Throwable? = failureCause
        while (cause != null && seen.add(cause)) {
            if (cause is ArrayIndexOutOfBoundsException && cause.stackTrace.any {
                    it.className.startsWith("androidx.media3.extractor.")
                }) return true
            cause = cause.cause
        }
        return false
    }

    fun shouldAttempt(
        errorCode: Int,
        localPath: String,
        nativeLibraryAvailable: Boolean,
        alreadyAttempted: Boolean,
    ): Boolean =
        errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED &&
            localPath.isNotBlank() &&
            !localPath.contains("://") &&
            nativeLibraryAvailable &&
            !alreadyAttempted
}
