package com.sole.cinevault

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Rational
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.library.VideoThumbnailHelper
import com.sole.cinevault.segments.SmartSegment
import com.sole.cinevault.segments.SmartSegmentResult
import kotlinx.coroutines.CoroutineScope

/**
 * Slice 59: main player chrome host.
 *
 * Owns visibility derivation and presentation wiring for the top controls,
 * transient status pills, and the transport/seek cluster extracted in Slice 58.
 * It owns no player state.
 */
@Composable
internal fun BoxScope.PlayerMainControlsChrome(
    context: Context,
    activity: Activity?,
    scope: CoroutineScope,
    player: ExoPlayer,
    haptics: HapticFeedback,
    currentMeta: VideoWithMetadata?,
    currentTitle: String,
    currentVideoPath: String,
    isStreamMedia: Boolean,
    isCurrentTvShow: Boolean,
    isLandscape: Boolean,
    isZoomMode: Boolean,
    showControls: Boolean,
    isDraggingSeekbar: Boolean,
    isDraggingSeekbarNow: () -> Boolean,
    showAudioSelector: Boolean,
    showSpeedMenu: Boolean,
    showSleepMenu: Boolean,
    showSrtBrowser: Boolean,
    showSeekPreview: Boolean,
    subtitleSettingsVisible: Boolean,
    trackSelectorVisible: Boolean,
    subtitleSearchVisible: Boolean,
    driftDialogVisible: Boolean,
    appearanceStudioVisible: Boolean,
    dialogueSyncArmed: Boolean,
    showSubtitleDock: Boolean,
    showSubtitleBloom: Boolean,
    showDualSubsWindow: Boolean,
    showSubtitleBehaviourWindow: Boolean,
    showSpeechSubtitlePanel: Boolean,
    showSubtitleTranslationPanel: Boolean,
    externalDisplayActive: Boolean,
    autoSubtitleStatus: String,
    playbackSpeed: Float,
    sleepTimerActive: Boolean,
    topClusterPaddingTop: Dp,
    sidePadding: Dp,
    topIconSize: Dp,
    showNextEpisodeOverlay: Boolean,
    pendingNextEpisode: VideoWithMetadata?,
    nextEpisodeCountdown: Int,
    activeSmartSegment: SmartSegment?,
    exactSceneSegment: SmartSegment?,
    creditsSegment: SmartSegment?,
    smartSegmentResult: SmartSegmentResult,
    position: Long,
    duration: Long,
    previewBitmap: Bitmap?,
    previewPosition: Long,
    getPreviewPosition: () -> Long,
    isSeekPreviewLarge: Boolean,
    previewFrames: List<VideoThumbnailHelper.PreviewFrame>,
    bottomDockPadding: Dp,
    seekBottomPadding: Dp,
    scale: Float,
    smallButton: Dp,
    playButton: Dp,
    isPlaying: Boolean,
    isVideoEnded: Boolean,
    showPrevNextButtons: Boolean,
    hasNextVideo: Boolean,
    autoPlayEnabled: Boolean,
    onBack: () -> Unit,
    playbackNavigationCoordinator: PlaybackNavigationCoordinator,
    playerMenuCloseCoordinator: PlayerMenuCloseCoordinator,
    onShowSpeedMenuChanged: (Boolean) -> Unit,
    onShowSleepMenuChanged: (Boolean) -> Unit,
    onShowControlsChanged: (Boolean) -> Unit,
    onClusterHeightMeasured: (Float) -> Unit,
    onPlayNextEpisode: (VideoWithMetadata) -> Unit,
    onCancelNextEpisode: () -> Unit,
    onPositionChanged: (Long) -> Unit,
    onShowTopBarChanged: (Boolean) -> Unit,
    onVideoEndedChanged: (Boolean) -> Unit,
    onAutoPlayEnabledChanged: (Boolean) -> Unit,
    onShowAudioSelectorChanged: (Boolean) -> Unit,
    onMenuTouch: () -> Unit,
    onAudioCenterMeasured: (Float) -> Unit,
    onSubtitleClick: () -> Unit,
    onSubtitleLongClick: () -> Unit,
    onSubtitleCenterMeasured: (Float) -> Unit,
    onDraggingSeekbarChanged: (Boolean) -> Unit,
    onShowSeekPreviewChanged: (Boolean) -> Unit,
    onPreviewPositionChanged: (Long) -> Unit,
    onPreviewBitmapChanged: (Bitmap?) -> Unit,
) {
    val subtitleOverlayActive =
        subtitleSettingsVisible ||
            trackSelectorVisible ||
            subtitleSearchVisible ||
            driftDialogVisible ||
            appearanceStudioVisible ||
            dialogueSyncArmed ||
            showSubtitleDock ||
            showSubtitleBloom ||
            showDualSubsWindow ||
            showSubtitleBehaviourWindow ||
            showSpeechSubtitlePanel ||
            showSubtitleTranslationPanel

    val mainControlsVisible =
        !subtitleOverlayActive &&
            shouldShowMainPlayerControls(
                externalDisplayActive = externalDisplayActive,
                showControls = showControls,
                isDraggingSeekbar = isDraggingSeekbar,
                showAudioSelector = showAudioSelector,
                showSubtitleSettings = subtitleSettingsVisible,
                showTrackSelector = trackSelectorVisible,
                showDriftDialog = driftDialogVisible,
                showAppearanceStudio = appearanceStudioVisible,
                dialogueSyncArmed = dialogueSyncArmed,
                showSpeedMenu = showSpeedMenu,
                showSleepMenu = showSleepMenu,
                showSubtitleSearch = subtitleSearchVisible,
                isInPipMode = CineVaultPlayerHolder.isInPipMode,
            )

    PlayerControlsVisibilityShell(
        visible = mainControlsVisible,
    ) {
        PlayerTopControlCluster(
            isLandscape = isLandscape,
            topRowVisible = !showSeekPreview,
            topClusterPaddingTop = topClusterPaddingTop,
            sidePadding = sidePadding,
            topIconSize = topIconSize,
            currentMeta = currentMeta,
            title = currentTitle,
            playbackSpeed = playbackSpeed,
            sleepTimerActive = sleepTimerActive,
            showSpeedMenu = showSpeedMenu,
            showSleepMenu = showSleepMenu,
            onSpeedClick = {
                val wasOpen = showSpeedMenu
                playerMenuCloseCoordinator.closeAll()
                onShowSpeedMenuChanged(!wasOpen)
                onShowControlsChanged(true)
            },
            onSleepClick = {
                val wasOpen = showSleepMenu
                playerMenuCloseCoordinator.closeAll()
                onShowSleepMenuChanged(!wasOpen)
                onShowControlsChanged(true)
            },
            onPipClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val actions =
                        buildPipActions(context, player.isPlaying)
                    activity?.enterPictureInPictureMode(
                        PictureInPictureParams.Builder()
                            .setAspectRatio(Rational(16, 9))
                            .setActions(actions)
                            .build()
                    )
                }
            },
            onClusterHeightMeasured = onClusterHeightMeasured,
        )

        PlayerTransientStatusPills(
            autoSubtitleStatus = autoSubtitleStatus,
            showSeekPreview = showSeekPreview,
            isLandscape = isLandscape,
            isZoomMode = isZoomMode,
        )

        PlayerTransportAndSmartControls(
            context = context,
            scope = scope,
            player = player,
            haptics = haptics,
            currentVideoPath = currentVideoPath,
            isStreamMedia = isStreamMedia,
            isCurrentTvShow = isCurrentTvShow,
            isLandscape = isLandscape,
            subtitleOverlayActive = subtitleOverlayActive,
            showAudioSelector = showAudioSelector,
            showSpeedMenu = showSpeedMenu,
            showSleepMenu = showSleepMenu,
            showSrtBrowser = showSrtBrowser,
            showSeekPreview = showSeekPreview,
            isDraggingSeekbar = isDraggingSeekbar,
            isDraggingSeekbarNow = isDraggingSeekbarNow,
            showNextEpisodeOverlay = showNextEpisodeOverlay,
            pendingNextEpisode = pendingNextEpisode,
            nextEpisodeCountdown = nextEpisodeCountdown,
            activeSmartSegment = activeSmartSegment,
            exactSceneSegment = exactSceneSegment,
            creditsSegment = creditsSegment,
            smartSegmentResult = smartSegmentResult,
            position = position,
            duration = duration,
            previewBitmap = previewBitmap,
            previewPosition = previewPosition,
            getPreviewPosition = getPreviewPosition,
            isSeekPreviewLarge = isSeekPreviewLarge,
            previewFrames = previewFrames,
            sidePadding = sidePadding,
            bottomDockPadding = bottomDockPadding,
            seekBottomPadding = seekBottomPadding,
            scale = scale,
            smallButton = smallButton,
            playButton = playButton,
            isPlaying = isPlaying,
            isVideoEnded = isVideoEnded,
            showPrevNextButtons = showPrevNextButtons,
            hasNextVideo = hasNextVideo,
            autoPlayEnabled = autoPlayEnabled,
            showSubtitleActive =
                subtitleSettingsVisible ||
                    trackSelectorVisible ||
                    subtitleSearchVisible ||
                    driftDialogVisible ||
                    appearanceStudioVisible,
            onBack = onBack,
            playbackNavigationCoordinator = playbackNavigationCoordinator,
            playerMenuCloseCoordinator = playerMenuCloseCoordinator,
            onPlayNextEpisode = onPlayNextEpisode,
            onCancelNextEpisode = onCancelNextEpisode,
            onPositionChanged = onPositionChanged,
            onShowControlsChanged = onShowControlsChanged,
            onShowTopBarChanged = onShowTopBarChanged,
            onVideoEndedChanged = onVideoEndedChanged,
            onAutoPlayEnabledChanged = onAutoPlayEnabledChanged,
            onShowAudioSelectorChanged = onShowAudioSelectorChanged,
            onMenuTouch = onMenuTouch,
            onAudioCenterMeasured = onAudioCenterMeasured,
            onSubtitleClick = onSubtitleClick,
            onSubtitleLongClick = onSubtitleLongClick,
            onSubtitleCenterMeasured = onSubtitleCenterMeasured,
            onDraggingSeekbarChanged = onDraggingSeekbarChanged,
            onShowSeekPreviewChanged = onShowSeekPreviewChanged,
            onPreviewPositionChanged = onPreviewPositionChanged,
            onPreviewBitmapChanged = onPreviewBitmapChanged,
        )
    }
}
