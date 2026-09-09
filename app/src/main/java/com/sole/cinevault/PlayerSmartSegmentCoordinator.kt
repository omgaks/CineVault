package com.sole.cinevault

import com.sole.cinevault.segments.SmartSegmentRepository
import com.sole.cinevault.segments.SmartSegmentResult

/**
 * Slice 28: owns Smart Segment loading decisions and the credits-driven
 * next-episode decision logic that previously lived inline in
 * VideoPlayerScreen.
 *
 * Compose still owns the LaunchedEffects themselves so cancellation remains
 * lifecycle-safe; this class only owns the orchestration decisions/work.
 */
class PlayerSmartSegmentCoordinator(
    private val repository: SmartSegmentRepository,
) {
    suspend fun loadIfNeeded(
        meta: VideoWithMetadata?,
        duration: Long,
    ): SmartSegmentResult? {
        val item = meta ?: return null
        if (!shouldLoadSmartSegments(item, duration)) return null
        return repository.load(item, duration)
    }

    fun findCreditsNextEpisode(
        currentVideoPath: String,
        episodeList: List<VideoWithMetadata>,
        isCurrentTvShow: Boolean,
        showNextEpisodeOverlay: Boolean,
        nextEpisodeDismissed: Boolean,
        creditsStartMs: Long?,
        position: Long,
    ): VideoWithMetadata? =
        findNextEpisodeForCredits(
            currentVideoPath = currentVideoPath,
            episodeList = episodeList,
            isCurrentTvShow = isCurrentTvShow,
            showNextEpisodeOverlay = showNextEpisodeOverlay,
            nextEpisodeDismissed = nextEpisodeDismissed,
            creditsStartMs = creditsStartMs,
            position = position,
        )

    fun shouldResetCreditsOverlay(
        showNextEpisodeOverlay: Boolean,
        creditsStartMs: Long?,
        position: Long,
    ): Boolean =
        shouldResetNextEpisodeOverlay(
            showNextEpisodeOverlay = showNextEpisodeOverlay,
            creditsStartMs = creditsStartMs,
            position = position,
        )
}
