package com.sole.cinevault.playback.rescue.video

/**
 * Small coordinator between rescue policy/request creation and the future
 * decoder backend.
 *
 * It refuses to call start() unless the backend capability is positively
 * available. This keeps B3/B4's no-fake-FFmpeg guarantee intact.
 */
fun attemptFfmpegVideoRescue(
    backend: FfmpegVideoRescueBackend,
    mediaUri: String,
    resumePositionMs: Long,
    playWhenReady: Boolean,
): FfmpegVideoRescueStartResult {
    val request = createFfmpegVideoRescueRequest(
        capability = backend.capability,
        mediaUri = mediaUri,
        resumePositionMs = resumePositionMs,
        playWhenReady = playWhenReady,
    ) ?: return FfmpegVideoRescueStartResult.Rejected(
        reason = backend.capability.snapshot().detail
            ?: "FFmpeg video backend unavailable",
    )

    return backend.start(request)
}
