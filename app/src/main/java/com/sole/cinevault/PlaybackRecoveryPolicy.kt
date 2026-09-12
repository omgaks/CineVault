package com.sole.cinevault

import androidx.media3.common.PlaybackException

/**
 * Playback Resilience Phase 1.
 *
 * This is the pure decision layer that decides whether CineVault should retry
 * the current engine, move from the normal MediaCodec path to a software
 * fallback engine, or stop and surface the error.
 *
 * Keeping this decision pure is deliberate: the actual player/renderer switch
 * is wired separately, while retry-loop prevention remains independently
 * testable.
 */
enum class PlaybackEngineMode {
    HARDWARE,
    SOFTWARE,
}

enum class PlaybackRecoveryAction {
    RETRY_CURRENT,
    SWITCH_TO_SOFTWARE,
    FAIL,
}

data class PlaybackRecoveryDecision(
    val action: PlaybackRecoveryAction,
    val nextRetryCount: Int,
)

private const val MAX_TRANSIENT_PLAYBACK_RETRIES = 2

fun decidePlaybackRecovery(
    errorCode: Int,
    currentRetryCount: Int,
    engineMode: PlaybackEngineMode,
    softwareFallbackAvailable: Boolean,
): PlaybackRecoveryDecision {
    if (
        isTransientPlaybackErrorCode(errorCode) &&
        currentRetryCount < MAX_TRANSIENT_PLAYBACK_RETRIES
    ) {
        return PlaybackRecoveryDecision(
            action = PlaybackRecoveryAction.RETRY_CURRENT,
            nextRetryCount = currentRetryCount + 1,
        )
    }

    if (
        isDecoderPlaybackErrorCode(errorCode) &&
        engineMode == PlaybackEngineMode.HARDWARE &&
        softwareFallbackAvailable
    ) {
        return PlaybackRecoveryDecision(
            action = PlaybackRecoveryAction.SWITCH_TO_SOFTWARE,
            nextRetryCount = 0,
        )
    }

    return PlaybackRecoveryDecision(
        action = PlaybackRecoveryAction.FAIL,
        nextRetryCount = currentRetryCount,
    )
}

fun isDecoderPlaybackErrorCode(errorCode: Int): Boolean {
    return when (errorCode) {
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> true

        else -> false
    }
}
