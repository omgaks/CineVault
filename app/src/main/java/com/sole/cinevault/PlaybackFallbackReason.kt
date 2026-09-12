package com.sole.cinevault

enum class PlaybackFallbackReason {
    DECODER_INIT_FAILED,
    DECODER_QUERY_FAILED,
    DECODING_FAILED,
    FORMAT_UNSUPPORTED,
    NATIVE_DECODER_UNAVAILABLE,
    EXCESSIVE_DROPPED_FRAMES,
    STARTUP_STALLED,
    FIRST_VIDEO_FRAME_MISSING,
    UNKNOWN_DECODER_FAILURE,
}

fun playbackFallbackReasonForErrorCode(
    errorCode: Int,
): PlaybackFallbackReason {
    return when (errorCode) {
        androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
            PlaybackFallbackReason.DECODER_INIT_FAILED

        androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
            PlaybackFallbackReason.DECODER_QUERY_FAILED

        androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ->
            PlaybackFallbackReason.DECODING_FAILED

        androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            PlaybackFallbackReason.FORMAT_UNSUPPORTED

        else ->
            PlaybackFallbackReason.UNKNOWN_DECODER_FAILURE
    }
}

fun playbackFallbackReasonLabel(
    reason: PlaybackFallbackReason?,
): String? {
    return when (reason) {
        PlaybackFallbackReason.DECODER_INIT_FAILED -> "Decoder init failed"
        PlaybackFallbackReason.DECODER_QUERY_FAILED -> "Decoder query failed"
        PlaybackFallbackReason.DECODING_FAILED -> "Decode failed"
        PlaybackFallbackReason.FORMAT_UNSUPPORTED -> "Format unsupported"
        PlaybackFallbackReason.NATIVE_DECODER_UNAVAILABLE -> "Native decoder unavailable"
        PlaybackFallbackReason.EXCESSIVE_DROPPED_FRAMES -> "Hardware playback unstable"
        PlaybackFallbackReason.STARTUP_STALLED -> "Hardware startup stalled"
        PlaybackFallbackReason.FIRST_VIDEO_FRAME_MISSING -> "Video frame not rendered"
        PlaybackFallbackReason.UNKNOWN_DECODER_FAILURE -> "Decoder failure"
        null -> null
    }
}
