package com.sole.cinevault

enum class NativeVideoPlaybackReadiness {
    READY,
    MARGINAL,
    SOFTWARE_FALLBACK_NEEDED,
    UNKNOWN,
}

/**
 * Converts the selected-track capability report into a simple playback decision.
 *
 * READY:
 *   At least one decoder fully supports the exact selected video format.
 *
 * MARGINAL:
 *   Android has a functionally compatible decoder, but Media3 does not consider
 *   the full format performantly supported. CineVault should still try native
 *   playback first while keeping software fallback ready.
 *
 * SOFTWARE_FALLBACK_NEEDED:
 *   No compatible native decoder exists for the selected video format.
 *
 * UNKNOWN:
 *   Capability could not be determined reliably.
 */
fun decideNativeVideoPlaybackReadiness(
    report: VideoDecoderCapabilityReport?,
): NativeVideoPlaybackReadiness {
    return when (report?.status) {
        VideoDecoderCapabilityStatus.SUPPORTED ->
            NativeVideoPlaybackReadiness.READY

        VideoDecoderCapabilityStatus.FUNCTIONAL_ONLY ->
            NativeVideoPlaybackReadiness.MARGINAL

        VideoDecoderCapabilityStatus.NO_COMPATIBLE_DECODER ->
            NativeVideoPlaybackReadiness.SOFTWARE_FALLBACK_NEEDED

        VideoDecoderCapabilityStatus.UNKNOWN,
        null ->
            NativeVideoPlaybackReadiness.UNKNOWN
    }
}
