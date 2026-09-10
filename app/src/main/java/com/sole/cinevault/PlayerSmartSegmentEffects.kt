package com.sole.cinevault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.sole.cinevault.segments.SmartSegmentResult

/**
 * Slice 45: Compose lifecycle wrapper for Smart Segment loading and
 * credits-driven next-episode effects.
 *
 * Decision/work logic remains in PlayerSmartSegmentCoordinator. This file
 * only keeps the three lifecycle effects out of VideoPlayerScreen.
 */
@Composable
fun PlayerSmartSegmentEffects(
    coordinator: PlayerSmartSegmentCoordinator,
    currentMeta: VideoWithMetadata?,
    duration: Long,
    currentVideoPath: String,
    episodeList: List<VideoWithMetadata>,
    isCurrentTvShow: Boolean,
    showNextEpisodeOverlay: Boolean,
    nextEpisodeDismissed: Boolean,
    creditsStartMs: Long?,
    position: Long,
    onSmartSegmentLoaded: (SmartSegmentResult) -> Unit,
    onNextEpisodeTriggered: (VideoWithMetadata) -> Unit,
    onResetNextEpisodeOverlay: () -> Unit,
) {
    LaunchedEffect(
        currentMeta?.video?.path,
        playerHasSmartSegmentDuration(duration),
    ) {
        coordinator
            .loadIfNeeded(currentMeta, duration)
            ?.let(onSmartSegmentLoaded)
    }

    LaunchedEffect(
        currentVideoPath,
        position,
        creditsStartMs,
        showNextEpisodeOverlay,
    ) {
        coordinator.findCreditsNextEpisode(
            currentVideoPath = currentVideoPath,
            episodeList = episodeList,
            isCurrentTvShow = isCurrentTvShow,
            showNextEpisodeOverlay = showNextEpisodeOverlay,
            nextEpisodeDismissed = nextEpisodeDismissed,
            creditsStartMs = creditsStartMs,
            position = position,
        )?.let(onNextEpisodeTriggered)
    }

    LaunchedEffect(
        position,
        creditsStartMs,
    ) {
        if (
            coordinator.shouldResetCreditsOverlay(
                showNextEpisodeOverlay = showNextEpisodeOverlay,
                creditsStartMs = creditsStartMs,
                position = position,
            )
        ) {
            onResetNextEpisodeOverlay()
        }
    }
}
