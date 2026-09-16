package com.sole.cinevault

internal enum class AudioRescueTerminalFailureReason {
    AUDIO_RENDERER_FAILED_AFTER_FFMPEG_RESCUE,
}

internal data class AudioRescueTerminalFailure(
    val reason: AudioRescueTerminalFailureReason,
    val rescueWasAttempted: Boolean,
)

internal fun terminalAudioRescueFailureOrNull(
    action: PostFfmpegAudioFailureAction,
): AudioRescueTerminalFailure? =
    when (action) {
        PostFfmpegAudioFailureAction.FAIL_RESCUE ->
            AudioRescueTerminalFailure(
                reason =
                    AudioRescueTerminalFailureReason
                        .AUDIO_RENDERER_FAILED_AFTER_FFMPEG_RESCUE,
                rescueWasAttempted = true,
            )

        PostFfmpegAudioFailureAction.USE_NORMAL_RECOVERY ->
            null
    }
