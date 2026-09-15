package com.sole.cinevault

data class PlaybackCompatibilityReportSummary(
    val total: Int,
    val nativePasses: Int,
    val softwareRescues: Int,
    val unstableFailures: Int,
    val pending: Int,
    val audioRescues: Int = 0,
    val mixedPipelines: Int = 0,
    val videoFailures: Int = 0,
    val audioFailures: Int = 0,
    val subtitleFailures: Int = 0,
    val otherFailures: Int = 0,
    val unknownFailures: Int = 0,
) {
    val completed: Int
        get() = total - pending

    val attributedFailures: Int
        get() = videoFailures +
            audioFailures +
            subtitleFailures +
            otherFailures +
            unknownFailures
}

fun summarizePlaybackCompatibilityReport(
    entries: List<PlaybackCompatibilityMatrixEntry>,
): PlaybackCompatibilityReportSummary {
    var nativePasses = 0
    var softwareRescues = 0
    var unstableFailures = 0
    var pending = 0
    var audioRescues = 0
    var mixedPipelines = 0
    var videoFailures = 0
    var audioFailures = 0
    var subtitleFailures = 0
    var otherFailures = 0
    var unknownFailures = 0

    entries.forEach { entry ->
        when (entry.verdict) {
            PlaybackCompatibilityVerdict.PASS_NATIVE ->
                nativePasses++
            PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE ->
                softwareRescues++
            PlaybackCompatibilityVerdict.FAIL_UNSTABLE ->
                unstableFailures++
            PlaybackCompatibilityVerdict.PENDING ->
                pending++
        }

        when (classifyPlaybackPipeline(entry.observation)) {
            PlaybackPipelineKind.AUDIO_FFMPEG_RESCUE ->
                audioRescues++
            PlaybackPipelineKind.MIXED_VIDEO_AUDIO_RESCUE -> {
                audioRescues++
                mixedPipelines++
            }
            else -> Unit
        }

        if (entry.verdict == PlaybackCompatibilityVerdict.FAIL_UNSTABLE) {
            when (entry.observation.terminalFailureStream) {
                PlaybackFailureStreamKind.VIDEO -> videoFailures++
                PlaybackFailureStreamKind.AUDIO -> audioFailures++
                PlaybackFailureStreamKind.TEXT -> subtitleFailures++
                PlaybackFailureStreamKind.OTHER -> otherFailures++
                PlaybackFailureStreamKind.UNKNOWN -> unknownFailures++
                null -> Unit
            }
        }
    }

    return PlaybackCompatibilityReportSummary(
        total = entries.size,
        nativePasses = nativePasses,
        softwareRescues = softwareRescues,
        unstableFailures = unstableFailures,
        pending = pending,
        audioRescues = audioRescues,
        mixedPipelines = mixedPipelines,
        videoFailures = videoFailures,
        audioFailures = audioFailures,
        subtitleFailures = subtitleFailures,
        otherFailures = otherFailures,
        unknownFailures = unknownFailures,
    )
}

fun playbackCompatibilitySummaryLine(
    summary: PlaybackCompatibilityReportSummary,
): String = buildList {
    add("${summary.completed}/${summary.total} completed")

    if (summary.nativePasses > 0) {
        add("${summary.nativePasses} native")
    }
    if (summary.softwareRescues > 0) {
        add("${summary.softwareRescues} video rescued")
    }
    if (summary.audioRescues > 0) {
        add("${summary.audioRescues} audio rescued")
    }
    if (summary.mixedPipelines > 0) {
        add("${summary.mixedPipelines} mixed")
    }
    if (summary.unstableFailures > 0) {
        add("${summary.unstableFailures} unstable")
    }
    if (summary.videoFailures > 0) {
        add("${summary.videoFailures} video fail")
    }
    if (summary.audioFailures > 0) {
        add("${summary.audioFailures} audio fail")
    }
    if (summary.subtitleFailures > 0) {
        add("${summary.subtitleFailures} subtitle fail")
    }
    if (summary.otherFailures > 0) {
        add("${summary.otherFailures} other fail")
    }
    if (summary.unknownFailures > 0) {
        add("${summary.unknownFailures} unknown fail")
    }
    if (summary.pending > 0) {
        add("${summary.pending} pending")
    }
}.joinToString(" · ")
