package com.sole.cinevault

internal fun buildSubtitleQuickMenuStatusText(
    subtitlesEnabled: Boolean,
    selectedLabel: String,
    selectedSource: String,
    hasInternalSubtitles: Boolean,
): String {
    return when {
        !subtitlesEnabled -> "Subtitles off"
        selectedLabel.isNotBlank() -> "$selectedLabel · $selectedSource"
        hasInternalSubtitles -> "Embedded track active"
        else -> "No subtitle selected"
    }
}
