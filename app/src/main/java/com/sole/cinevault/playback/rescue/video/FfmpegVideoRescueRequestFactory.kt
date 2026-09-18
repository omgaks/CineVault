package com.sole.cinevault.playback.rescue.video

/**
 * Builds a B4 FFmpeg-video hand-off only after B3 has positively verified that
 * the real backend and its decoder are ready.
 *
 * Returning null is intentional: an unavailable/unready backend must never be
 * turned into a rescue attempt merely because the policy reached FFmpeg.
 */
fun createFfmpegVideoRescueRequest(
    capability: FfmpegVideoBackendCapability,
    mediaUri: String,
    resumePositionMs: Long,
    playWhenReady: Boolean,
): FfmpegVideoRescueRequest? {
    if (!capability.snapshot().available) return null

    return FfmpegVideoRescueRequest(
        mediaUri = mediaUri,
        resumePositionMs = resumePositionMs.coerceAtLeast(0L),
        playWhenReady = playWhenReady,
    )
}
