package com.sole.cinevault

internal fun findNextEpisodeForCredits(
    currentVideoPath: String,
    episodeList: List<VideoWithMetadata>,
    isCurrentTvShow: Boolean,
    showNextEpisodeOverlay: Boolean,
    nextEpisodeDismissed: Boolean,
    creditsStartMs: Long?,
    position: Long,
): VideoWithMetadata? {
    if (!isCurrentTvShow) return null
    if (showNextEpisodeOverlay) return null
    if (nextEpisodeDismissed) return null

    val creditsStart = creditsStartMs ?: return null
    if (position < creditsStart) return null

    val currentIndex = episodeList.indexOfFirst {
        it.video.path == currentVideoPath
    }
    if (currentIndex < 0) return null

    return episodeList.getOrNull(currentIndex + 1)
}
