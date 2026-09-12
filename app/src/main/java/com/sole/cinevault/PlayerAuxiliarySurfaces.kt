package com.sole.cinevault

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.media3.exoplayer.ExoPlayer

/**
 * Slice 64: presentation host for auxiliary playback surfaces.
 *
 * Mutable state remains owned by VideoPlayerScreen. This host only groups
 * subtitle gesture handling, transient playback/status overlays, and the
 * speed/sleep menus that sit above the video surface.
 */
@Composable
internal fun BoxScope.PlayerAuxiliarySurfaces(
    player: ExoPlayer,
    haptics: HapticFeedback,
    isStreamMedia: Boolean,
    bottomDockPadding: Dp,
    playButtonSize: Dp,
    coreUi: SubtitleCoreUiState,
    appearanceUi: SubtitleAppearanceUiState,
    studioUi: SubtitleStudioUiState,
    isLandscape: Boolean,
    hudSize: Dp,
    showBrightnessCircle: Boolean,
    brightnessPercent: Int,
    showVolumeCircle: Boolean,
    volumePercent: Int,
    edgeSwipeHint: String,
    showGlassesConnectedHint: Boolean,
    showBufferingSpinner: Boolean,
    stuckBufferingHint: Boolean,
    playerErrorMessage: String?,
    sleepTimerActive: Boolean,
    sleepTimerRemainingMs: Long,
    translationSuccessLanguage: String?,
    translationSuccessBottomPadding: Dp,
    showSpeedMenu: Boolean,
    showSleepMenu: Boolean,
    playbackSpeed: Float,
    sleepTimerMinutes: Int,
    topClusterPaddingTop: Dp,
    clusterHeightPx: Float,
    sidePadding: Dp,
    smallMenuWidth: Dp,
    smallMenuMaxHeight: Dp,
    onShowControls: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onDismissSpeedMenu: () -> Unit,
    onSleepSelected: (Int) -> Unit,
    onDismissSleepMenu: () -> Unit,
) {
    PlayerSubtitleGestureOverlay(
        player = player,
        haptics = haptics,
        isStreamMedia = isStreamMedia,
        bottomDockPadding = bottomDockPadding,
        playButtonSize = playButtonSize,
        coreUi = coreUi,
        appearanceUi = appearanceUi,
        studioUi = studioUi,
        onShowControls = onShowControls,
    )

    PlayerPlaybackStatusOverlays(
        isLandscape = isLandscape,
        hudSize = hudSize,
        showBrightnessCircle = showBrightnessCircle,
        brightnessPercent = brightnessPercent,
        showVolumeCircle = showVolumeCircle,
        volumePercent = volumePercent,
        edgeSwipeHint = edgeSwipeHint,
        showGlassesConnectedHint = showGlassesConnectedHint,
        showBufferingSpinner = showBufferingSpinner,
        stuckBufferingHint = stuckBufferingHint,
        playerErrorMessage = playerErrorMessage,
        sleepTimerActive = sleepTimerActive,
        sleepTimerRemainingMs = sleepTimerRemainingMs,
        translationSuccessLanguage = translationSuccessLanguage,
        translationSuccessBottomPadding = translationSuccessBottomPadding,
        onBack = onBack,
        onRetry = onRetry,
    )

    PlayerSpeedAndSleepMenus(
        showSpeedMenu = showSpeedMenu,
        showSleepMenu = showSleepMenu,
        playbackSpeed = playbackSpeed,
        sleepTimerMinutes = sleepTimerMinutes,
        topClusterPaddingTop = topClusterPaddingTop,
        clusterHeightPx = clusterHeightPx,
        isLandscape = isLandscape,
        sidePadding = sidePadding,
        smallMenuWidth = smallMenuWidth,
        smallMenuMaxHeight = smallMenuMaxHeight,
        onSpeedSelected = onSpeedSelected,
        onDismissSpeedMenu = onDismissSpeedMenu,
        onSleepSelected = onSleepSelected,
        onDismissSleepMenu = onDismissSleepMenu,
    )
}
