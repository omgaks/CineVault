package com.sole.cinevault

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.library.VideoThumbnailHelper
import com.sole.cinevault.subtitles.SubtitleSyncToolsCoordinator

/**
 * Slice 55: owns tablet + glasses playback gesture wiring.
 *
 * The gesture implementations and all state still live in their existing
 * helpers/owners. This composable only centralizes which callbacks are attached
 * for tablet playback versus external-display playback.
 */
@Composable
internal fun PlayerPlaybackGestureLayer(
    context: Context,
    activity: Activity?,
    player: ExoPlayer,
    audioManager: AudioManager,
    externalDisplayActive: Boolean,
    currentVideoPath: String,
    episodeList: List<VideoWithMetadata>,
    isLandscape: Boolean,
    canChangeEpisode: Boolean,
    previewFrames: List<VideoThumbnailHelper.PreviewFrame>,
    previewPosition: Long,
    previewBitmap: Bitmap?,
    brightnessPercent: Int,
    volumePercent: Int,
    videoScale: Float,
    videoOffsetX: Float,
    videoOffsetY: Float,
    screenWidthPx: Float,
    screenHeightPx: Float,
    showControls: Boolean,
    showAudioSelector: Boolean,
    showSubtitleDock: Boolean,
    showSubtitleBloom: Boolean,
    showDualSubsWindow: Boolean,
    showSubtitleBehaviourWindow: Boolean,
    showSpeechSubtitlePanel: Boolean,
    showSubtitleTranslationPanel: Boolean,
    showSpeedMenu: Boolean,
    showSleepMenu: Boolean,
    showSrtBrowser: Boolean,
    coreUi: SubtitleCoreUiState,
    trackUi: SubtitleTrackSelectionState,
    searchUi: SubtitleAcquisitionUiState,
    driftUi: DriftCorrectionState,
    subtitleSyncTools: SubtitleSyncToolsCoordinator,
    playbackNavigationCoordinator: PlaybackNavigationCoordinator,
    onDraggingSeekbarChanged: (Boolean) -> Unit,
    onPreviewPositionChanged: (Long) -> Unit,
    onPreviewBitmapChanged: (Bitmap?) -> Unit,
    onPositionChanged: (Long) -> Unit,
    onBrightnessPercentChanged: (Int) -> Unit,
    onVolumePercentChanged: (Int) -> Unit,
    onShowBrightnessCircleChanged: (Boolean) -> Unit,
    onShowVolumeCircleChanged: (Boolean) -> Unit,
    onVideoTransformChanged: (scale: Float, offsetX: Float, offsetY: Float) -> Unit,
    onZoomModeToggle: () -> Unit,
    onShowControlsChanged: (Boolean) -> Unit,
    onShowTopBarChanged: (Boolean) -> Unit,
    onShowAudioSelectorChanged: (Boolean) -> Unit,
    onShowSubtitleDockChanged: (Boolean) -> Unit,
    onShowSubtitleBloomChanged: (Boolean) -> Unit,
    onShowDualSubsWindowChanged: (Boolean) -> Unit,
    onShowSubtitleBehaviourWindowChanged: (Boolean) -> Unit,
    onShowSpeechSubtitlePanelChanged: (Boolean) -> Unit,
    onShowSubtitleTranslationPanelChanged: (Boolean) -> Unit,
    onShowSpeedMenuChanged: (Boolean) -> Unit,
    onShowSleepMenuChanged: (Boolean) -> Unit,
    onShowSrtBrowserChanged: (Boolean) -> Unit,
    onGestureEnd: () -> Unit,
    externalControlsVisible: () -> Boolean,
    externalShowTouchPulse: () -> Unit,
    externalClickPointer: () -> Boolean,
    externalShowControls: () -> Unit,
    externalShowGestureHud: (title: String, value: String, progress: Int?) -> Unit,
    externalOpenQuickSubtitles: () -> Unit,
    externalUpdateSeekPreview: (bitmap: Bitmap?, positionMs: Long, visible: Boolean) -> Unit,
    externalMovePointer: (x: Float, y: Float) -> Unit,
    externalApplyViewportTransform: (zoom: Float, panX: Float, panY: Float) -> Unit,
    externalEnterTabletStandby: () -> Unit,
    disableGlassesSession: () -> Unit,
) {
    val view = LocalView.current
    PlayerGestureExclusionCleanupEffect(view)

    var livePreviewPosition = previewPosition
    var livePreviewBitmap = previewBitmap
    var liveBrightnessPercent = brightnessPercent
    var liveVolumePercent = volumePercent
    var liveVideoScale = videoScale
    var liveVideoOffsetX = videoOffsetX
    var liveVideoOffsetY = videoOffsetY

    val playbackGestureModifier =
        if (externalDisplayActive) {
            Modifier.glassesTouchpadGestures(
                view = view,
                gestureKey = currentVideoPath to isLandscape,
                controlsVisible = externalControlsVisible,
                canChangeEpisode = { canChangeEpisode },
                onSingleTap = {
                    externalShowTouchPulse()
                    if (externalControlsVisible()) {
                        if (!externalClickPointer()) externalShowControls()
                    } else {
                        externalShowControls()
                    }
                },
                onDoubleTap = {
                    if (player.isPlaying) player.pause() else player.play()
                    externalShowGestureHud(
                        "Playback",
                        if (player.isPlaying) "PLAY" else "PAUSE",
                        null,
                    )
                },
                onLongPress = externalOpenQuickSubtitles,
                onSeekStart = {
                    onDraggingSeekbarChanged(true)
                    livePreviewPosition = player.currentPosition
                    onPreviewPositionChanged(livePreviewPosition)
                    livePreviewBitmap =
                        VideoThumbnailHelper.nearestPreviewFrame(
                            previewFrames,
                            livePreviewPosition,
                        )
                    onPreviewBitmapChanged(livePreviewBitmap)
                    externalUpdateSeekPreview(
                        livePreviewBitmap,
                        livePreviewPosition,
                        true,
                    )
                },
                onSeekDelta = { fraction ->
                    val safeDuration = playerSafeSeekDuration(player.duration)
                    livePreviewPosition =
                        calculatePlayerSeekPreviewPosition(
                            livePreviewPosition,
                            fraction,
                            safeDuration,
                        )
                    onPreviewPositionChanged(livePreviewPosition)
                    livePreviewBitmap =
                        VideoThumbnailHelper.nearestPreviewFrame(
                            previewFrames,
                            livePreviewPosition,
                        )
                    onPreviewBitmapChanged(livePreviewBitmap)
                    externalUpdateSeekPreview(
                        livePreviewBitmap,
                        livePreviewPosition,
                        true,
                    )
                },
                onSeekEnd = {
                    player.seekTo(livePreviewPosition)
                    onPositionChanged(livePreviewPosition)
                    onDraggingSeekbarChanged(false)
                    externalUpdateSeekPreview(
                        livePreviewBitmap,
                        livePreviewPosition,
                        false,
                    )
                },
                onBrightnessDrag = { deltaY ->
                    liveBrightnessPercent =
                        adjustPlayerBrightnessPercent(
                            liveBrightnessPercent,
                            deltaY,
                        )
                    onBrightnessPercentChanged(liveBrightnessPercent)
                    activity?.window?.attributes =
                        activity?.window?.attributes?.apply {
                            screenBrightness =
                                playerWindowBrightness(liveBrightnessPercent)
                        }
                    onShowBrightnessCircleChanged(true)
                    externalShowGestureHud(
                        "Tablet brightness",
                        "$liveBrightnessPercent%",
                        liveBrightnessPercent,
                    )
                },
                onVolumeDrag = { deltaY ->
                    liveVolumePercent =
                        adjustPlayerVolumePercent(
                            liveVolumePercent,
                            deltaY,
                            maxPercent = 100,
                        )
                    onVolumePercentChanged(liveVolumePercent)
                    val maxVol =
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        playerSystemVolumeIndex(liveVolumePercent, maxVol),
                        0,
                    )
                    onShowVolumeCircleChanged(true)
                    externalShowGestureHud(
                        "Volume",
                        "$liveVolumePercent%",
                        liveVolumePercent,
                    )
                },
                onPrevious = {
                    externalShowGestureHud("Episode", "PREVIOUS", null)
                    playbackNavigationCoordinator.playPrevious()
                },
                onNext = {
                    externalShowGestureHud("Episode", "NEXT", null)
                    playbackNavigationCoordinator.playNext()
                },
                onPointerMove = {
                    externalMovePointer(it.x, it.y)
                },
                onPointerClick = {
                    externalShowTouchPulse()
                    externalClickPointer()
                },
                onPinchZoomPan = { zoom, pan ->
                    externalApplyViewportTransform(zoom, pan.x, pan.y)
                    val zoomHud =
                        calculatePlayerExternalZoomHud(liveVideoScale, zoom)
                    liveVideoScale = zoomHud.scale
                    onVideoTransformChanged(
                        liveVideoScale,
                        liveVideoOffsetX,
                        liveVideoOffsetY,
                    )
                    externalShowGestureHud(
                        "Screen size",
                        "${zoomHud.percent}%",
                        zoomHud.progressPercent,
                    )
                },
                onEmergencyReturnToTablet = {
                    externalShowGestureHud(
                        "Emergency return",
                        "TABLET",
                        null,
                    )
                    externalEnterTabletStandby()
                    disableGlassesSession()
                    Toast.makeText(
                        context,
                        "Glasses Mode ended — playback returned to tablet",
                        Toast.LENGTH_LONG,
                    ).show()
                },
                onGestureEnd = onGestureEnd,
            )
        } else {
            Modifier.videoPlaybackGestures(
                view = view,
                videoPathKey = currentVideoPath,
                episodeListKey = episodeList,
                edgeSwipeNextEnabled = { canChangeEpisode },
                onTap = {
                    when {
                        showAudioSelector ->
                            onShowAudioSelectorChanged(false)

                        coreUi.showSettings ->
                            coreUi.showSettings = false

                        trackUi.showSelector ->
                            trackUi.showSelector = false

                        searchUi.showSearch ->
                            searchUi.showSearch = false

                        driftUi.showDialog ->
                            driftUi.showDialog = false

                        coreUi.showAppearanceStudio ->
                            coreUi.showAppearanceStudio = false

                        coreUi.dialogueSyncArmed ->
                            subtitleSyncTools.cancelDialogueSync()

                        showSubtitleDock ->
                            onShowSubtitleDockChanged(false)

                        showSubtitleBloom ->
                            onShowSubtitleBloomChanged(false)

                        showDualSubsWindow ->
                            onShowDualSubsWindowChanged(false)

                        showSubtitleBehaviourWindow ->
                            onShowSubtitleBehaviourWindowChanged(false)

                        showSpeechSubtitlePanel ->
                            onShowSpeechSubtitlePanelChanged(false)

                        showSubtitleTranslationPanel ->
                            onShowSubtitleTranslationPanelChanged(false)

                        showSpeedMenu ->
                            onShowSpeedMenuChanged(false)

                        showSleepMenu ->
                            onShowSleepMenuChanged(false)

                        showSrtBrowser ->
                            onShowSrtBrowserChanged(false)

                        else -> {
                            val visible = !showControls
                            onShowControlsChanged(visible)
                            onShowTopBarChanged(visible)
                        }
                    }
                },
                onSeekBack = {
                    player.seekTo(
                        playerSeekBackPosition(player.currentPosition)
                    )
                    onPositionChanged(player.currentPosition)
                    onShowControlsChanged(true)
                    onShowTopBarChanged(true)
                },
                onSeekForward = {
                    player.seekTo(
                        playerSeekForwardPosition(
                            player.currentPosition,
                            player.duration,
                        )
                    )
                    onPositionChanged(player.currentPosition)
                    onShowControlsChanged(true)
                    onShowTopBarChanged(true)
                },
                onToggleZoomMode = {
                    onZoomModeToggle()
                    onShowControlsChanged(true)
                    onShowTopBarChanged(true)
                },
                onDragSettled = onGestureEnd,
                onEdgeSwipeNext = {
                    playbackNavigationCoordinator.playNext()
                },
                onBrightnessDrag = { deltaY ->
                    liveBrightnessPercent =
                        adjustPlayerBrightnessPercent(
                            liveBrightnessPercent,
                            deltaY,
                        )
                    onBrightnessPercentChanged(liveBrightnessPercent)
                    activity?.window?.attributes =
                        activity?.window?.attributes?.apply {
                            screenBrightness =
                                playerWindowBrightness(liveBrightnessPercent)
                        }
                    onShowBrightnessCircleChanged(true)
                },
                onVolumeDrag = { deltaY ->
                    liveVolumePercent =
                        adjustPlayerVolumePercent(
                            liveVolumePercent,
                            deltaY,
                            maxPercent = 150,
                        )
                    onVolumePercentChanged(liveVolumePercent)
                    val maxVol =
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        playerSystemVolumeIndex(liveVolumePercent, maxVol),
                        0,
                    )
                    onShowVolumeCircleChanged(true)
                },
                onPinchZoomPan = { zoom, pan ->
                    val transform =
                        calculatePlayerZoomPanTransform(
                            currentScale = liveVideoScale,
                            currentOffsetX = liveVideoOffsetX,
                            currentOffsetY = liveVideoOffsetY,
                            zoomFactor = zoom,
                            panX = pan.x,
                            panY = pan.y,
                            screenWidthPx = screenWidthPx,
                            screenHeightPx = screenHeightPx,
                        )
                    liveVideoScale = transform.scale
                    liveVideoOffsetX = transform.offsetX
                    liveVideoOffsetY = transform.offsetY
                    onVideoTransformChanged(
                        liveVideoScale,
                        liveVideoOffsetX,
                        liveVideoOffsetY,
                    )
                },
            )
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(playbackGestureModifier)
    )
}
