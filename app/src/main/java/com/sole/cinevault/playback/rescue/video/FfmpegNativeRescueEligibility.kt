package com.sole.cinevault.playback.rescue.video

import androidx.media3.common.PlaybackException

/**
 * A narrow gate for native container rescue. Do not take over for network,
 * DRM, audio-only, or unrelated decoder errors.
 */
internal object FfmpegNativeRescueEligibility {
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
