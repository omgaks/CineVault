package com.sole.cinevault

data class PlaybackCompatibilityReportSummary(
    val total: Int,
    val nativePasses: Int,
    val softwareRescues: Int,
    val unstableFailures: Int,
    val pending: Int,
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
    }

    return PlaybackCompatibilityReportSummary(
        total = entries.size,
        nativePasses = nativePasses,
        softwareRescues = softwareRescues,
        unstableFailures = unstableFailures,
        pending = pending,
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
        add("${summary.softwareRescues} rescued")
    }

    if (summary.unstableFailures > 0) {
        add("${summary.unstableFailures} unstable")
    }

    if (summary.pending > 0) {
        add("${summary.pending} pending")
    }
}.joinToString(" · ")
