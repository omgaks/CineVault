package com.sole.cinevault

data class PlaybackCompatibilityMatrixGroup(
    val title: String,
    val entries: List<PlaybackCompatibilityMatrixRow>,
)

data class PlaybackCompatibilityMatrixRow(
    val testId: String,
    val sourceLabel: String,
    val streamSummary: String,
    val decoderSummary: String,
    val verdict: PlaybackCompatibilityVerdict,
    val failureSummary: String? = null,
)

fun buildPlaybackCompatibilityMatrixGroups(
    entries: List<PlaybackCompatibilityMatrixEntry>,
): List<PlaybackCompatibilityMatrixGroup> =
    entries
        .groupBy { matrixGroupTitle(it.observation.key) }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        .map { (title, groupedEntries) ->
            PlaybackCompatibilityMatrixGroup(
                title = title,
                entries = groupedEntries
                    .sortedWith(
                        compareBy<PlaybackCompatibilityMatrixEntry>(
                            { compatibilityVerdictSortRank(it.verdict) },
                            { it.testCase.sourceLabel.orEmpty().lowercase() },
                            { it.testCase.testId.lowercase() },
                        )
                    )
                    .map(::presentCompatibilityMatrixRow),
            )
        }

fun playbackCompatibilityVerdictLabel(
    verdict: PlaybackCompatibilityVerdict,
): String = when (verdict) {
    PlaybackCompatibilityVerdict.PASS_NATIVE -> "NATIVE"
    PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE -> "RESCUED"
    PlaybackCompatibilityVerdict.FAIL_UNSTABLE -> "UNSTABLE"
    PlaybackCompatibilityVerdict.PENDING -> "PENDING"
}

private fun matrixGroupTitle(
    key: PlaybackCompatibilityKey,
): String = buildList {
    add(key.codecLabel?.takeIf { it.isNotBlank() } ?: "Unknown codec")
    key.profileLabel?.takeIf { it.isNotBlank() }?.let(::add)
    key.bitDepth?.let { add("$it-bit") }
}.joinToString(" · ")

private fun presentCompatibilityMatrixRow(
    entry: PlaybackCompatibilityMatrixEntry,
): PlaybackCompatibilityMatrixRow {
    val observation = entry.observation
    val key = observation.key

    val streamSummary = buildList {
        add(key.resolution)
        key.frameRate?.let { add(formatCompatibilityMatrixFps(it)) }
        if (key.dynamicRange != VideoDynamicRange.SDR) {
            add(
                key.dynamicRange.name
                    .replace('_', ' ')
                    .replace(" OR ", "/")
            )
        }
    }.joinToString(" · ")

    val decoderSummary = buildList {
        add(
            when (observation.decoderKind) {
                ActiveVideoDecoderKind.HARDWARE -> "HW"
                ActiveVideoDecoderKind.SOFTWARE -> "SW"
                ActiveVideoDecoderKind.UNKNOWN -> "?"
            }
        )
        observation.decoderName
            ?.takeIf { it.isNotBlank() }
            ?.let(::add)
    }.joinToString(" · ")

    val failureSummary = observation.terminalFailureStream?.let { stream ->
        buildString {
            append(playbackFailureStreamLabel(stream))
            observation.audioMimeType
                ?.takeIf { stream == PlaybackFailureStreamKind.AUDIO }
                ?.let {
                    val descriptor = PlaybackStreamDescriptor(
                        kind = PlaybackStreamKind.AUDIO,
                        mimeType = it,
                        codecString = observation.audioCodecString,
                        language = observation.audioLanguage,
                        selected = true,
                    )
                    assessAudioStreamCapability(descriptor)
                        ?.codecLabel
                        ?.takeIf { label -> label.isNotBlank() }
                        ?.let { label -> append(" · ").append(label) }
                }
            observation.terminalFailureErrorCode
                ?.let { append(" · error ").append(it) }
        }
    }

    return PlaybackCompatibilityMatrixRow(
        testId = entry.testCase.testId,
        sourceLabel = entry.testCase.sourceLabel
            ?.takeIf { it.isNotBlank() }
            ?: entry.testCase.testId,
        streamSummary = streamSummary,
        decoderSummary = decoderSummary,
        verdict = entry.verdict,
        failureSummary = failureSummary,
    )
}

private fun playbackFailureStreamLabel(
    stream: PlaybackFailureStreamKind,
): String = when (stream) {
    PlaybackFailureStreamKind.VIDEO -> "VIDEO"
    PlaybackFailureStreamKind.AUDIO -> "AUDIO"
    PlaybackFailureStreamKind.TEXT -> "SUBTITLE"
    PlaybackFailureStreamKind.OTHER -> "OTHER"
    PlaybackFailureStreamKind.UNKNOWN -> "PLAYBACK"
}

private fun compatibilityVerdictSortRank(
    verdict: PlaybackCompatibilityVerdict,
): Int = when (verdict) {
    PlaybackCompatibilityVerdict.FAIL_UNSTABLE -> 0
    PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE -> 1
    PlaybackCompatibilityVerdict.PASS_NATIVE -> 2
    PlaybackCompatibilityVerdict.PENDING -> 3
}

private fun formatCompatibilityMatrixFps(
    fps: Float,
): String {
    val rounded = kotlin.math.round(fps * 100f) / 100f
    return if (rounded % 1f == 0f) {
        "${rounded.toInt()} fps"
    } else {
        "$rounded fps"
    }
}
