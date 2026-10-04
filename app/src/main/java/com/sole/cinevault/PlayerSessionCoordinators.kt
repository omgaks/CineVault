package com.sole.cinevault

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.sole.cinevault.library.VideoFile
import com.sole.cinevault.subtitles.SubtitleSearchCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

internal data class PlayerSessionCoordinatorBundle(
    val sessionActions: PlayerSessionActionsCoordinator,
    val navigation: PlaybackNavigationCoordinator,
    val subtitleSearch: SubtitleSearchCoordinator,
)

/**
 * Slice 62: cohesive session-level coordinator wiring.
 *
 * Groups playback-speed/sleep actions, playlist navigation, and subtitle
 * search coordination. State still belongs to VideoPlayerScreen.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun rememberPlayerSessionCoordinators(
    context: Context,
    scope: CoroutineScope,
    player: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    haptics: HapticFeedback,
    episodeList: List<VideoWithMetadata>,
    currentVideo: VideoFile,
    isStreamMedia: Boolean,
    playbackSpeed: Float,
    sleepTimerActive: Boolean,
    sleepTimerRemainingMs: Long,
    trackUi: SubtitleTrackSelectionState,
    coreUi: SubtitleCoreUiState,
    searchUi: SubtitleAcquisitionUiState,
    studioUi: SubtitleStudioUiState,
    onPlaybackSpeedChanged: (Float) -> Unit,
    onSleepTimerMinutesChanged: (Int) -> Unit,
    onSleepTimerActiveChanged: (Boolean) -> Unit,
    onSleepTimerRemainingMsChanged: (Long) -> Unit,
    onShowSpeedMenuChanged: (Boolean) -> Unit,
    onShowSleepMenuChanged: (Boolean) -> Unit,
    onShowControlsChanged: (Boolean) -> Unit,
    onCurrentVideoChanged: (VideoFile) -> Unit,
    onCurrentMediaTypeChanged: (String) -> Unit,
    onEdgeSwipeHintChanged: (String) -> Unit,
    onPlayerErrorMessageChanged: (String?) -> Unit,
    onVideoEndedChanged: (Boolean) -> Unit,
    onPendingSrtUriChanged: (android.net.Uri?) -> Unit,
    onPlayNext: (VideoWithMetadata) -> Unit,
    onExternalSubtitleReplaced: () -> Unit = {},
): PlayerSessionCoordinatorBundle {
    // These coordinators are remembered for the player lifetime, so any values
    // they read later through callbacks must stay fresh across recomposition.
    val currentEpisodeList = rememberUpdatedState(episodeList)
    val currentVideoState = rememberUpdatedState(currentVideo)
    val currentIsStreamMedia = rememberUpdatedState(isStreamMedia)
    val currentPlaybackSpeed = rememberUpdatedState(playbackSpeed)
    val currentSleepTimerActive = rememberUpdatedState(sleepTimerActive)
    val currentSleepTimerRemainingMs = rememberUpdatedState(sleepTimerRemainingMs)
    val sessionActions = remember(player) {
        PlayerSessionActionsCoordinator(
            context = context,
            exoPlayer = player,
            performSelectionHaptic = {
                haptics.performHapticFeedback(
                    HapticFeedbackType.TextHandleMove
                )
            },
            setPlaybackSpeedState = onPlaybackSpeedChanged,
            setSleepTimerMinutes = onSleepTimerMinutesChanged,
            getSleepTimerActive = { currentSleepTimerActive.value },
            setSleepTimerActive = onSleepTimerActiveChanged,
            getSleepTimerRemainingMs = { currentSleepTimerRemainingMs.value },
            setSleepTimerRemainingMs = onSleepTimerRemainingMsChanged,
            closeSpeedMenu = { onShowSpeedMenuChanged(false) },
            closeSleepMenu = { onShowSleepMenuChanged(false) },
            showControls = { onShowControlsChanged(true) },
        )
    }

    LaunchedEffect(sleepTimerActive, sleepTimerRemainingMs) {
        if (sessionActions.shouldTickSleepTimer()) {
            delay(playerSleepTimerTickIntervalMs())
            sessionActions.tickSleepTimer()
        }
    }

    val externalSubtitleSelector = remember(player, trackSelector) {
        ExternalSubtitleSelector(context, player, trackSelector)
    }

    val latestExternalSubtitleReplaced = rememberUpdatedState(onExternalSubtitleReplaced)

    // Draws external subtitles itself so changing one never restarts the video.
    val subtitleOverlay = remember(player, trackSelector) {
        ExternalSubtitleOverlay(player, trackSelector) { studioUi.playerView?.subtitleView }
    }
    androidx.compose.runtime.DisposableEffect(subtitleOverlay) {
        onDispose { subtitleOverlay.release() }
    }

    val navigation = remember(player, externalSubtitleSelector, subtitleOverlay) {
        PlaybackNavigationCoordinator(
            context = context,
            scope = scope,
            exoPlayer = player,
            trackUi = trackUi,
            coreUi = coreUi,
            getEpisodeList = { currentEpisodeList.value },
            getCurrentVideo = { currentVideoState.value },
            getIsStreamMedia = { currentIsStreamMedia.value },
            getPlaybackSpeed = { currentPlaybackSpeed.value },
            setCurrentVideo = onCurrentVideoChanged,
            setCurrentMediaType = onCurrentMediaTypeChanged,
            setEdgeSwipeHint = onEdgeSwipeHintChanged,
            setPlayerErrorMessage = onPlayerErrorMessageChanged,
            setIsVideoEnded = onVideoEndedChanged,
            onPlayNext = onPlayNext,
            externalSubtitleSelector = externalSubtitleSelector,
            subtitleOverlay = subtitleOverlay,
            trackSelector = trackSelector,
        )
    }

    val subtitleSearch = remember(player, trackSelector) {
        SubtitleSearchCoordinator(
            context = context,
            scope = scope,
            exoPlayer = player,
            trackSelector = trackSelector,
            coreUi = coreUi,
            trackUi = trackUi,
            searchUi = searchUi,
            studioUi = studioUi,
            getCurrentVideoPath = { currentVideoState.value.path },
            setShowControls = onShowControlsChanged,
            setPendingSrtUri = onPendingSrtUriChanged,
            playSubtitle = { subtitleUri, resumePosition, isOriginalSubtitle ->
                navigation.playCurrentVideoWithSubtitle(
                    subtitleUri,
                    resumePosition,
                    isOriginalSubtitle,
                )
            },
            externalOverlay = subtitleOverlay,
            onExternalSubtitleReplaced = { latestExternalSubtitleReplaced.value() },
        )
    }

    return PlayerSessionCoordinatorBundle(
        sessionActions = sessionActions,
        navigation = navigation,
        subtitleSearch = subtitleSearch,
    )
}
