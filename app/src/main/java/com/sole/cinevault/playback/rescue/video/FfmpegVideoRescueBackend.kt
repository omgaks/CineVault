package com.sole.cinevault.playback.rescue.video

/**
 * Runtime boundary for the future FFmpeg VIDEO decoder implementation.
 *
 * B5 intentionally defines the boundary only. The actual decoder/native
 * integration comes later and must live behind this interface rather than in
 * VideoPlayerScreen.
 */
interface FfmpegVideoRescueBackend {
    val capability: FfmpegVideoBackendCapability

    /**
     * Accepts a verified rescue request.
     *
     * Implementations must return a result rather than throwing routine
     * availability/startup failures into the player UI layer.
     */
    fun start(request: FfmpegVideoRescueRequest): FfmpegVideoRescueStartResult
}

sealed interface FfmpegVideoRescueStartResult {
    data object Started : FfmpegVideoRescueStartResult

    data class Rejected(
        val reason: String,
    ) : FfmpegVideoRescueStartResult
}

/**
 * Safe production default until a real FFmpeg VIDEO implementation is wired.
 */
object UnavailableFfmpegVideoRescueBackend : FfmpegVideoRescueBackend {
    override val capability: FfmpegVideoBackendCapability =
        UnavailableFfmpegVideoBackendCapability

    override fun start(
        request: FfmpegVideoRescueRequest,
    ): FfmpegVideoRescueStartResult =
        FfmpegVideoRescueStartResult.Rejected(
            reason = "FFmpeg video backend not wired",
        )
}
