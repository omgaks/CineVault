package com.sole.cinevault.playback.rescue.video

/**
 * Extended backend boundary that can return a live rescue session.
 *
 * Keeping this separate from the B5 start() contract lets us evolve the
 * implementation progressively without breaking the already-green rescue
 * policy tests.
 */
interface FfmpegVideoSessionBackend : FfmpegVideoRescueBackend {
    fun openSession(
        request: FfmpegVideoRescueRequest,
    ): FfmpegVideoSessionOpenResult
}

sealed interface FfmpegVideoSessionOpenResult {
    data class Opened(
        val session: FfmpegVideoRescueSession,
    ) : FfmpegVideoSessionOpenResult

    data class Rejected(
        val reason: String,
    ) : FfmpegVideoSessionOpenResult
}
