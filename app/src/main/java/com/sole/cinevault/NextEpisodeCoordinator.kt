package com.sole.cinevault

/**
 * Slice 27: owns the decision and state transition at the end of the
 * next-episode countdown.
 *
 * The countdown itself remains a LaunchedEffect in VideoPlayerScreen so Compose
 * continues to cancel it automatically when its keys change. This coordinator
 * centralises the actual "is there still a next episode?" check and the final
 * navigation/state mutation.
 */
class NextEpisodeCoordinator(
    private val getPendingNextEpisode: () -> EpisodePlaybackItem?,
    private val getShowNextEpisodeOverlay: () -> Boolean,
    private val setShowNextEpisodeOverlay: (Boolean) -> Unit,
    private val setPendingNextEpisode: (EpisodePlaybackItem?) -> Unit,
    private val setCurrentMediaType: (String) -> Unit,
    private val setCurrentVideo: (VideoFile) -> Unit,
    private val onPlayNext: (EpisodePlaybackItem) -> Unit,
) {
    fun shouldRunCountdown(): Boolean =
        getShowNextEpisodeOverlay() && getPendingNextEpisode() != null

    fun playPendingNextEpisode() {
        val next = getPendingNextEpisode() ?: return

        setShowNextEpisodeOverlay(false)
        setPendingNextEpisode(null)
        setCurrentMediaType(next.type)
        setCurrentVideo(next.video)
        onPlayNext(next)
    }
}
