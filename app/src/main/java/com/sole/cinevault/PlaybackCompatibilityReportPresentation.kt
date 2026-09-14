package com.sole.cinevault

data class PlaybackCompatibilityReportSummary(
    val total: Int,
    val nativePasses: Int,
    val softwareRescues: Int,
    val unstableFailures: Int,
    val pending: Int,
    val audioRescues: Int = 0,
    val mixedPipelines: Int = 0,
) {
    val completed: Int
        get() = total - pending
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
    }

    return PlaybackCompatibilityReportSummary(
        total = entries.size,
        nativePasses = nativePasses,
        softwareRescues = softwareRescues,
        unstableFailures = unstableFailures,
        pending = pending,
        audioRescues = audioRescues,
        mixedPipelines = mixedPipelines,
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

    if (summary.pending > 0) {
        add("${summary.pending} pending")
    }
}.joinToString(" · ")
