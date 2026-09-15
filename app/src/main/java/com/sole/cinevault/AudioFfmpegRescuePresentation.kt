package com.sole.cinevault

fun audioFfmpegRescueOutcomeLabel(
    outcome: AudioFfmpegRescueOutcome,
): String? = when (outcome) {
    AudioFfmpegRescueOutcome.NOT_ATTEMPTED -> null
    AudioFfmpegRescueOutcome.PENDING -> "FFmpeg audio rescue pending"
    AudioFfmpegRescueOutcome.CONFIRMED -> "FFmpeg audio rescued"
    AudioFfmpegRescueOutcome.FAILED -> "FFmpeg audio rescue failed"
}

fun audioFfmpegRescuePillLabel(
    outcome: AudioFfmpegRescueOutcome,
): String? = when (outcome) {
    AudioFfmpegRescueOutcome.NOT_ATTEMPTED -> null
    AudioFfmpegRescueOutcome.PENDING -> "FFMPEG SWITCH"
    AudioFfmpegRescueOutcome.CONFIRMED -> "FFMPEG AUDIO"
    AudioFfmpegRescueOutcome.FAILED -> "AUDIO FAILED"
}
