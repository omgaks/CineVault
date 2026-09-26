package com.sole.cinevault.subtitles

internal fun isValidEmbeddedSubtitleSelection(
    groupIndex: Int,
    trackIndexInGroup: Int,
    textGroupTrackCounts: List<Int>,
): Boolean {
    if (groupIndex !in textGroupTrackCounts.indices) return false
    return trackIndexInGroup in 0 until textGroupTrackCounts[groupIndex]
}
