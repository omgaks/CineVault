package com.sole.cinevault.playback.rescue.video

/**
 * Stage B3 capability contract for a future real FFmpeg VIDEO backend.
 *
 * Important:
 * - This does not decode video.
 * - This does not claim FFmpeg video support.
 * - Default/unconfigured state is unavailable.
 *
 * The runtime backend will implement this contract in a later slice.
 */
interface FfmpegVideoBackendCapability {
    fun snapshot(): FfmpegVideoBackendCapabilitySnapshot
}

data class FfmpegVideoBackendCapabilitySnapshot(
    val backendPresent: Boolean = false,
    val decoderReady: Boolean = false,
    val detail: String? = null,
) {
    val available: Boolean
        get() = backendPresent && decoderReady
}

/** Safe default until the actual FFmpeg video backend is wired. */
object UnavailableFfmpegVideoBackendCapability : FfmpegVideoBackendCapability {
    override fun snapshot(): FfmpegVideoBackendCapabilitySnapshot =
        FfmpegVideoBackendCapabilitySnapshot(
            backendPresent = false,
            decoderReady = false,
            detail = "FFmpeg video backend not wired",
        )
}
