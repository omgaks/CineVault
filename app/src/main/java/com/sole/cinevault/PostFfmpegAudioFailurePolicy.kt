package com.sole.cinevault

enum class PostFfmpegAudioFailureAction {
    USE_NORMAL_RECOVERY,
    FAIL_RESCUE,
}

/**
 * Once CineVault has already rebuilt the same title with FFmpeg-first audio,
 * another renderer-level audio failure must not fall back into the generic
 * retry loop. The one-shot rescue has been consumed and is now considered
 * failed.
 */
fun decidePostFfmpegAudioFailureAction(
    attribution: PlaybackFailureAttribution,
    ffmpegRescueAttempted: Boolean,
): PostFfmpegAudioFailureAction {
    return if (
        ffmpegRescueAttempted &&
        attribution.isAudioRendererFailure
    ) {
        PostFfmpegAudioFailureAction.FAIL_RESCUE
    } else {
        PostFfmpegAudioFailureAction.USE_NORMAL_RECOVERY
    }
}
