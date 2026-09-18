package com.sole.cinevault.playback.rescue.video

/**
 * Opens a live FFmpeg video rescue session only when B3 capability verification
 * and B4 request construction both succeed.
 */
fun openFfmpegVideoRescueSession(
    backend: FfmpegVideoSessionBackend,
    mediaUri: String,
    resumePositionMs: Long,
    playWhenReady: Boolean,
): FfmpegVideoSessionOpenResult {
    val request = createFfmpegVideoRescueRequest(
        capability = backend.capability,
        mediaUri = mediaUri,
        resumePositionMs = resumePositionMs,
        playWhenReady = playWhenReady,
    ) ?: return FfmpegVideoSessionOpenResult.Rejected(
        reason = backend.capability.snapshot().detail
            ?: "FFmpeg video backend unavailable",
    )

    return backend.openSession(request)
}
