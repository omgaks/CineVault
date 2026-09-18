package com.sole.cinevault.playback.rescue.video

/**
 * Immutable hand-off request for the future FFmpeg VIDEO backend.
 *
 * B4 still does not create, start, or claim a working FFmpeg decoder. It only
 * defines the minimum playback state that must survive a backend switch.
 */
data class FfmpegVideoRescueRequest(
    val mediaUri: String,
    val resumePositionMs: Long,
    val playWhenReady: Boolean,
) {
    init {
        require(mediaUri.isNotBlank()) { "mediaUri must not be blank" }
        require(resumePositionMs >= 0L) { "resumePositionMs must be >= 0" }
    }
}
