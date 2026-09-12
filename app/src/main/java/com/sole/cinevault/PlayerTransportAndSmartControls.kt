package com.sole.cinevault

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.library.VideoThumbnailHelper
import com.sole.cinevault.segments.SegmentType
import com.sole.cinevault.segments.SmartSegment
import com.sole.cinevault.segments.SmartSegmentResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Slice 58: owns the primary transport/seek presentation cluster plus
 * smart-playback overlays. No player state is owned here; all mutations are
 * routed back through callbacks or existing coordinators.
 */
@Composable
internal fun BoxScope.PlayerTransportAndSmartControls(
    context: Context,
    scope: CoroutineScope,
    player: ExoPlayer,
    haptics: HapticFeedback,
    currentVideoPath: String,
    isStreamMedia: Boolean,
    isCurrentTvShow: Boolean,
    isLandscape: Boolean,
    subtitleOverlayActive: Boolean,
    showAudioSelector: Boolean,
    showSpeedMenu: Boolean,
    showSleepMenu: Boolean,
    showSrtBrowser: Boolean,
    showSeekPreview: Boolean,
    isDraggingSeekbar: Boolean,
    isDraggingSeekbarNow: () -> Boolean,
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
    sidePadding: Dp,
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
    showSubtitleActive: Boolean,
    onBack: () -> Unit,
    playbackNavigationCoordinator: PlaybackNavigationCoordinator,
    playerMenuCloseCoordinator: PlayerMenuCloseCoordinator,
    onPlayNextEpisode: (VideoWithMetadata) -> Unit,
    onCancelNextEpisode: () -> Unit,
    onPositionChanged: (Long) -> Unit,
    onShowControlsChanged: (Boolean) -> Unit,
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
    val anyMenuOpenForSmartSkip =
        showAudioSelector ||
            subtitleOverlayActive ||
            showSpeedMenu ||
            showSleepMenu ||
            showSrtBrowser

    val suppressCreditsPillForScene =
        activeSmartSegment?.type == SegmentType.CREDITS &&
            (
                smartSegmentResult.hasMidCreditsScene ||
                    smartSegmentResult.hasPostCreditsScene
            )

    val creditNoticeVisible =
        !isCurrentTvShow &&
            creditsSegment != null &&
            position >= creditsSegment.startMs &&
            (
                smartSegmentResult.hasMidCreditsScene ||
                    smartSegmentResult.hasPostCreditsScene
            ) &&
            (
                exactSceneSegment == null ||
                    position < exactSceneSegment.startMs
            )

    PlayerSmartPlaybackOverlays(
        sidePadding = sidePadding,
        showSeekPreview = showSeekPreview,
        isDraggingSeekbar = isDraggingSeekbar,
        showNextEpisodeOverlay = showNextEpisodeOverlay,
        pendingNextEpisode = pendingNextEpisode,
        nextEpisodeCountdown = nextEpisodeCountdown,
        activeSmartSegment = activeSmartSegment,
        suppressCreditsPillForScene = suppressCreditsPillForScene,
        anyMenuOpenForSmartSkip = anyMenuOpenForSmartSkip,
        creditNoticeVisible = creditNoticeVisible,
        exactSceneSegment = exactSceneSegment,
        hasMidCreditsScene = smartSegmentResult.hasMidCreditsScene,
        hasPostCreditsScene = smartSegmentResult.hasPostCreditsScene,
        position = position,
        isLandscape = isLandscape,
        onPlayNextEpisode = onPlayNextEpisode,
        onCancelNextEpisode = onCancelNextEpisode,
        onSkipSegment = { segment ->
            player.seekTo(segment.endMs)
            onPositionChanged(segment.endMs)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onShowControlsChanged(true)
        },
        onJumpToCreditScene = { scene ->
            player.seekTo(scene.startMs)
            onPositionChanged(scene.startMs)
        },
    )

    PlayerBottomTransportDock(
        visible = !showSeekPreview && !isDraggingSeekbar,
        bottomDockPadding = bottomDockPadding,
        sidePadding = sidePadding,
        scale = scale,
        smallButton = smallButton,
        playButton = playButton,
        isPlaying = isPlaying,
        isVideoEnded = isVideoEnded,
        showPrevNextButtons = showPrevNextButtons,
        hasNextVideo = hasNextVideo,
        autoPlayEnabled = autoPlayEnabled,
        showAudioSelector = showAudioSelector,
        showSubtitleActive = showSubtitleActive,
        isStreamMedia = isStreamMedia,
        onBack = onBack,
        onReplay10 = {
            player.seekTo(
                playerSeekBackPosition(player.currentPosition)
            )
            onPositionChanged(player.currentPosition)
            onShowControlsChanged(true)
        },
        onPlayPause = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (isVideoEnded) {
                player.seekTo(0)
                player.play()
                onVideoEndedChanged(false)
                onShowControlsChanged(true)
            } else {
                if (player.isPlaying) player.pause() else player.play()
                onShowControlsChanged(true)
            }
        },
        onForward10 = {
            player.seekTo(
                playerSeekForwardPosition(
                    player.currentPosition,
                    player.duration,
                )
            )
            onPositionChanged(player.currentPosition)
            onShowControlsChanged(true)
        },
        onNext = {
            if (hasNextVideo) {
                haptics.performHapticFeedback(
                    HapticFeedbackType.TextHandleMove
                )
                playbackNavigationCoordinator.playNext()
            }
        },
        onToggleAutoplay = {
            haptics.performHapticFeedback(
                HapticFeedbackType.TextHandleMove
            )
            val enabled = !autoPlayEnabled
            onAutoPlayEnabledChanged(enabled)
            onShowControlsChanged(true)
            Toast.makeText(
                context,
                if (enabled) "Autoplay on" else "Autoplay off",
                Toast.LENGTH_SHORT,
            ).show()
        },
        onAudioClick = {
            val wasOpen = showAudioSelector
            playerMenuCloseCoordinator.closeAll()
            onShowAudioSelectorChanged(!wasOpen)
            onShowControlsChanged(true)
            onMenuTouch()
        },
        onAudioCenterMeasured = onAudioCenterMeasured,
        onSubtitleClick = onSubtitleClick,
        onSubtitleLongClick = onSubtitleLongClick,
        onSubtitleCenterMeasured = onSubtitleCenterMeasured,
    )

    PlayerSeekDock(
        showSeekPreview = showSeekPreview,
        previewBitmap = previewBitmap,
        previewPosition = previewPosition,
        duration = duration,
        isLandscape = isLandscape,
        isSeekPreviewLarge = isSeekPreviewLarge,
        seekBottomPadding = seekBottomPadding,
        sidePadding = sidePadding,
        scale = scale,
        position = position,
        isDraggingSeekbar = isDraggingSeekbar,
        seed = currentVideoPath.hashCode(),
        onPreviewPositionChanged = { pos ->
            onDraggingSeekbarChanged(true)
            onShowSeekPreviewChanged(true)
            onShowControlsChanged(true)
            onShowTopBarChanged(true)
            val bounded = playerBoundedSeekPosition(pos, duration)
            onPositionChanged(bounded)
            onPreviewPositionChanged(bounded)
            VideoThumbnailHelper
                .nearestPreviewFrame(previewFrames, bounded)
                ?.let(onPreviewBitmapChanged)
        },
        onSeekFinished = { finalPos ->
            val safe =
                playerBoundedSeekPosition(finalPos, duration)
            onPositionChanged(safe)
            onPreviewPositionChanged(safe)
            player.seekTo(safe)
            onDraggingSeekbarChanged(false)

            val nearest =
                VideoThumbnailHelper.nearestPreviewFrame(
                    previewFrames,
                    safe,
                )
            if (nearest != null) {
                onPreviewBitmapChanged(nearest)
            }
            onShowSeekPreviewChanged(true)

            if (isStreamMedia) {
                scope.launch {
                    delay(playerStreamSeekPreviewHideDelayMs())
                    if (!isDraggingSeekbarNow()) {
                        onShowSeekPreviewChanged(false)
                    }
                }
            } else {
                scope.launch {
                    val bmp =
                        VideoThumbnailHelper.generateFrameAtTime(
                            context,
                            currentVideoPath,
                            safe,
                        )
                    if (bmp != null && getPreviewPosition() == safe) {
                        onPreviewBitmapChanged(bmp)
                    }
                    delay(playerLocalSeekPreviewHideDelayMs())
                    if (!isDraggingSeekbarNow()) {
                        onShowSeekPreviewChanged(false)
                    }
                }
            }

            onShowControlsChanged(true)
            onShowTopBarChanged(true)
        },
    )
}
