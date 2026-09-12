package com.sole.cinevault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.sole.cinevault.library.VideoFile
import com.sole.cinevault.segments.SmartSegment
import com.sole.cinevault.segments.SmartSegmentRepository
import com.sole.cinevault.segments.SmartSegmentResult
import kotlinx.coroutines.delay

internal data class PlayerEpisodeRuntime(
    val currentMeta: VideoWithMetadata?,
    val activeSmartSegment: SmartSegment?,
    val exactSceneSegment: SmartSegment?,
    val creditsSegment: SmartSegment?,
    val showPrevNextButtons: Boolean,
    val hasNextVideo: Boolean,
)

/**
 * Slice 61: playlist + Smart Segment + next-episode runtime.
 *
 * Keeps the related episode decision/effect pipeline in one place while the
 * parent screen continues to own all mutable state.
 */
@Composable
internal fun rememberPlayerEpisodeRuntime(
    smartSegmentRepository: SmartSegmentRepository,
    currentVideo: VideoFile,
    episodeList: List<VideoWithMetadata>,
    isCurrentTvShow: Boolean,
    isRestrictedFolderMedia: Boolean,
    smartSegmentResult: SmartSegmentResult,
    duration: Long,
    position: Long,
    showNextEpisodeOverlay: Boolean,
    nextEpisodeDismissed: Boolean,
    pendingNextEpisode: VideoWithMetadata?,
    isPlaying: Boolean,
    isVideoEnded: Boolean,
    onSmartSegmentResultChanged: (SmartSegmentResult) -> Unit,
    onPendingNextEpisodeChanged: (VideoWithMetadata?) -> Unit,
    onNextEpisodeCountdownChanged: (Int) -> Unit,
    onShowNextEpisodeOverlayChanged: (Boolean) -> Unit,
    onCurrentMediaTypeChanged: (String) -> Unit,
    onCurrentVideoChanged: (VideoFile) -> Unit,
    onPlayNext: (VideoWithMetadata) -> Unit,
): PlayerEpisodeRuntime {
    val pendingNextEpisodeState = rememberUpdatedState(pendingNextEpisode)
    val showNextEpisodeOverlayState = rememberUpdatedState(showNextEpisodeOverlay)
    val isPlayingState = rememberUpdatedState(isPlaying)
    val isVideoEndedState = rememberUpdatedState(isVideoEnded)
    val playlistNavigation = remember(
        currentVideo.path,
        currentVideo.name,
        episodeList,
        isCurrentTvShow,
        isRestrictedFolderMedia,
    ) {
        derivePlayerPlaylistNavigation(
            currentVideo = currentVideo,
            episodeList = episodeList,
            isCurrentTvShow = isCurrentTvShow,
            isRestrictedFolderMedia = isRestrictedFolderMedia,
        )
    }

    val playerSmartSegmentCoordinator = remember(smartSegmentRepository) {
        PlayerSmartSegmentCoordinator(smartSegmentRepository)
    }

    val smartPlaybackSegments = deriveSmartPlaybackSegments(
        result = smartSegmentResult,
        position = position,
    )

    PlayerSmartSegmentEffects(
        coordinator = playerSmartSegmentCoordinator,
        currentMeta = playlistNavigation.currentMeta,
        duration = duration,
        currentVideoPath = currentVideo.path,
        episodeList = episodeList,
        isCurrentTvShow = isCurrentTvShow,
        showNextEpisodeOverlay = showNextEpisodeOverlay,
        nextEpisodeDismissed = nextEpisodeDismissed,
        creditsStartMs = smartPlaybackSegments.creditsSegment?.startMs,
        position = position,
        onSmartSegmentLoaded = onSmartSegmentResultChanged,
        onNextEpisodeTriggered = { next ->
            onPendingNextEpisodeChanged(next)
            onNextEpisodeCountdownChanged(15)
            onShowNextEpisodeOverlayChanged(true)
        },
        onResetNextEpisodeOverlay = {
            onShowNextEpisodeOverlayChanged(false)
            onPendingNextEpisodeChanged(null)
            onNextEpisodeCountdownChanged(0)
        },
    )

    val nextEpisodeCoordinator = remember {
        NextEpisodeCoordinator(
            getPendingNextEpisode = { pendingNextEpisodeState.value },
            getShowNextEpisodeOverlay = { showNextEpisodeOverlayState.value },
            setShowNextEpisodeOverlay = onShowNextEpisodeOverlayChanged,
            setPendingNextEpisode = onPendingNextEpisodeChanged,
            setCurrentMediaType = onCurrentMediaTypeChanged,
            setCurrentVideo = onCurrentVideoChanged,
            onPlayNext = onPlayNext,
        )
    }

    LaunchedEffect(showNextEpisodeOverlay, pendingNextEpisode) {
        if (nextEpisodeCoordinator.shouldRunCountdown()) {
            var count = 15
            while (count > 0) {
                onNextEpisodeCountdownChanged(count)
                delay(playerNextEpisodeCountdownIntervalMs())
                if (!nextEpisodeCoordinator.shouldRunCountdown()) {
                    return@LaunchedEffect
                }
                if (isPlayingState.value || isVideoEndedState.value) {
                    count--
                }
            }
            nextEpisodeCoordinator.playPendingNextEpisode()
        }
    }

    return PlayerEpisodeRuntime(
        currentMeta = playlistNavigation.currentMeta,
        activeSmartSegment = smartPlaybackSegments.activeSegment,
        exactSceneSegment = smartPlaybackSegments.exactSceneSegment,
        creditsSegment = smartPlaybackSegments.creditsSegment,
        showPrevNextButtons = playlistNavigation.showPrevNextButtons,
        hasNextVideo = playlistNavigation.hasNextVideo,
    )
}
