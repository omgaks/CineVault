package com.sole.cinevault

data class VerifiedAudioRescueUi(
    val detailLabel: String?,
    val pillLabel: String?,
    val emphasize: Boolean,
)

fun verifiedAudioRescueUi(
    outcome: AudioFfmpegRescueOutcome,
): VerifiedAudioRescueUi = VerifiedAudioRescueUi(
    detailLabel = audioFfmpegRescueOutcomeLabel(outcome),
    pillLabel = audioFfmpegRescuePillLabel(outcome),
    emphasize = outcome != AudioFfmpegRescueOutcome.NOT_ATTEMPTED,
)
