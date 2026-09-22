package com.sole.cinevault

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.glasses.halo.HaloPlayerInputBridge
import com.sole.cinevault.glasses.halo.HaloPlayerLiveBindings
import com.sole.cinevault.glasses.halo.HaloPlayerLiveSessionFactory
import com.sole.cinevault.glasses.halo.haloPlayerLiveDragGestures
import com.sole.cinevault.glasses.halo.haloPlayerSupportGestures
import com.sole.cinevault.library.VideoThumbnailHelper

/**
 * D5-6: live D5 Halo integration for external-display playback.
 *
 * Canonical Halo owns pointer movement and target activation.
 * Existing playback gestures keep brightness, volume, seek and support gestures.
 * Transient UI keeps priority over background hide/toggle behavior.
 * Local phone/tablet playback remains unchanged.
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
    transientUi: PlayerTransientUiSnapshot,
    onDismissTransientUi: () -> Unit,
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
    onGestureEnd: () -> Unit,
    externalControlsVisible: () -> Boolean,
    externalShowTouchPulse: () -> Unit,
    externalClickPointer: () -> Boolean,
    externalShowControls: () -> Unit,
    externalHideControls: () -> Unit,
    externalShowGestureHud: (title: String, value: String, progress: Int?) -> Unit,
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

    val currentShowControls by rememberUpdatedState(showControls)
    val currentTransientUi by rememberUpdatedState(transientUi)

    val haloInputBridge =
        HaloPlayerInputBridge(
            externalDisplayActive = externalDisplayActive,
            externalTargetSurfaceAvailable = externalDisplayActive,
            transientUiVisible = transientUi.anyVisible,
        )

    val haloLiveBindings =
        HaloPlayerLiveBindings(
            bridge = haloInputBridge,
            moveCanonicalPointer = externalMovePointer,
            clickCanonicalTarget = externalClickPointer,
        )

    val liveHaloSession =
        remember(currentVideoPath, isLandscape) {
            HaloPlayerLiveSessionFactory.create(
                brightnessDrag = { deltaY ->
                    liveBrightnessPercent =
                        adjustPlayerBrightnessPercent(liveBrightnessPercent, deltaY)
                    onBrightnessPercentChanged(liveBrightnessPercent)
                    activity?.window?.attributes =
                        activity?.window?.attributes?.apply {
                            screenBrightness = playerWindowBrightness(liveBrightnessPercent)
                        }
                    onShowBrightnessCircleChanged(true)
                    externalShowGestureHud(
                        "Tablet brightness",
                        "$liveBrightnessPercent%",
                        liveBrightnessPercent,
                    )
                },
                volumeDrag = { deltaY ->
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
                seekTo = { targetMs ->
                    val safeDuration = playerSafeSeekDuration(player.duration)
                    livePreviewPosition =
                        targetMs.coerceIn(0L, safeDuration.coerceAtLeast(0L))
                    onPreviewPositionChanged(livePreviewPosition)
                    livePreviewBitmap =
                        VideoThumbnailHelper.nearestPreviewFrame(
                            previewFrames,
                            livePreviewPosition,
                        )
                    onPreviewBitmapChanged(livePreviewBitmap)
                    onDraggingSeekbarChanged(true)
                    externalUpdateSeekPreview(
                        livePreviewBitmap,
                        livePreviewPosition,
                        true,
                    )
                    player.seekTo(livePreviewPosition)
                    onPositionChanged(livePreviewPosition)
                },
                userActivity = { externalShowControls() },
            )
        }

    val playbackGestureModifier =
        if (externalDisplayActive) {
            Modifier
                .haloPlayerLiveDragGestures(
                    gestureKey = currentVideoPath to isLandscape,
                    playbackPositionMs = { player.currentPosition },
                    durationMs = { playerSafeSeekDuration(player.duration) },
                    liveSession = liveHaloSession,
                    onCanonicalPointerMove = { delta ->
                        haloLiveBindings.movePointer(delta.x, delta.y)
                    },
                    onGestureEnd = {
                        onDraggingSeekbarChanged(false)
                        externalUpdateSeekPreview(
                            livePreviewBitmap,
                            livePreviewPosition,
                            false,
                        )
                        onGestureEnd()
                    },
                )
                .haloPlayerSupportGestures(
                    gestureKey = currentVideoPath to isLandscape,
                    view = view,
                    controlsVisible = externalControlsVisible,
                    clickHaloTarget = { haloLiveBindings.clickTarget() },
                    onShowControls = externalShowControls,
                    onHideControls = {
                        if (haloLiveBindings.backgroundTapMayToggleControls()) {
                            externalHideControls()
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
                    onTouchPulse = externalShowTouchPulse,
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
                        liveHaloSession.reset()
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
                )
        } else {
            Modifier.videoPlaybackGestures(
                view = view,
                videoPathKey = currentVideoPath,
                episodeListKey = episodeList,
                edgeSwipeNextEnabled = { canChangeEpisode },
                onTap = {
                    if (currentTransientUi.anyVisible) {
                        onDismissTransientUi()
                        onShowControlsChanged(true)
                        onShowTopBarChanged(true)
                    } else {
                        val visible = !currentShowControls
                        onShowControlsChanged(visible)
                        onShowTopBarChanged(visible)
                    }
                },
                onSeekBack = {
                    player.seekTo(playerSeekBackPosition(player.currentPosition))
                    onPositionChanged(player.currentPosition)
                },
                onSeekForward = {
                    player.seekTo(
                        playerSeekForwardPosition(
                            player.currentPosition,
                            player.duration,
                        )
                    )
                    onPositionChanged(player.currentPosition)
                },
                onToggleZoomMode = {
                    onZoomModeToggle()
                    onShowControlsChanged(true)
                    onShowTopBarChanged(true)
                },
                onDragSettled = onGestureEnd,
                onEdgeSwipeNext = { playbackNavigationCoordinator.playNext() },
                onBrightnessDrag = { deltaY ->
                    liveBrightnessPercent =
                        adjustPlayerBrightnessPercent(liveBrightnessPercent, deltaY)
                    onBrightnessPercentChanged(liveBrightnessPercent)
                    activity?.window?.attributes =
                        activity?.window?.attributes?.apply {
                            screenBrightness = playerWindowBrightness(liveBrightnessPercent)
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
