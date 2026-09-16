package com.sole.cinevault

internal data class AudioRescueTerminalPresentation(
    val userMessage: String,
    val diagnosticTag: String,
)

internal fun AudioRescueTerminalFailure.toPresentation():
    AudioRescueTerminalPresentation =
    when (reason) {
        AudioRescueTerminalFailureReason
            .AUDIO_RENDERER_FAILED_AFTER_FFMPEG_RESCUE ->
            AudioRescueTerminalPresentation(
                userMessage =
                    "Audio playback failed after FFmpeg rescue.",
                diagnosticTag =
                    "audio_renderer_failed_after_ffmpeg_rescue",
            )
    }
