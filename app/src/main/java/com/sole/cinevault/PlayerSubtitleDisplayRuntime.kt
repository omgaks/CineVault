package com.sole.cinevault

import android.content.Context
import androidx.compose.runtime.Composable
import com.sole.cinevault.subtitles.*

/**
 * Slice 65: owns the subtitle display-profile lifecycle around the player.
 *
 * This keeps profile selection, per-movie subtitle appearance persistence
 * and reset-coordinator wiring together while VideoPlayerScreen continues
 * to own the mutable UI state itself.
 */
@Composable
internal fun rememberPlayerSubtitleDisplayRuntime(
    context: Context,
    externalDisplayConnected: Boolean,
    isSmallPhone: Boolean,
    isLandscape: Boolean,
    videoPath: String,
    movieSubtitleMemory: MovieSubtitleMemory?,
    movieSubtitleMemoryReady: Boolean,
    movieAppearanceMemoryReady: Boolean,
    coreUi: SubtitleCoreUiState,
    trackUi: SubtitleTrackSelectionState,
    dualUi: DualSubtitleState,
    appearanceUi: SubtitleAppearanceUiState,
    driftUi: DriftCorrectionState,
    dualSecondaryColorHex: String,
    onMovieAppearanceMemoryReadyChanged: (Boolean) -> Unit,
    setAudioSyncMs: (Int) -> Unit,
    setShowControls: (Boolean) -> Unit,
    incrementMenuTouchKey: () -> Unit,
): SubtitleResetCoordinator {
    val displayProfileType = RememberPlayerSubtitleDisplayProfile(
        context = context,
        externalDisplayConnected = externalDisplayConnected,
        isSmallPhone = isSmallPhone,
        isLandscape = isLandscape,
        appearanceUi = appearanceUi,
    )

    PlayerMovieSubtitleMemoryEffects(
        context = context,
        videoPath = videoPath,
        displayProfileName = displayProfileType.name,
        isLandscape = isLandscape,
        movieSubtitleMemory = movieSubtitleMemory,
        movieSubtitleMemoryReady = movieSubtitleMemoryReady,
        movieAppearanceMemoryReady = movieAppearanceMemoryReady,
        coreUi = coreUi,
        trackUi = trackUi,
        dualUi = dualUi,
        appearanceUi = appearanceUi,
        dualSecondaryColorHex = dualSecondaryColorHex,
        onMovieAppearanceMemoryReadyChanged = onMovieAppearanceMemoryReadyChanged,
    )

    return SubtitleResetCoordinator(
        context = context,
        displayProfileType = displayProfileType,
        isLandscape = isLandscape,
        appearanceUi = appearanceUi,
        coreUi = coreUi,
        trackUi = trackUi,
        driftUi = driftUi,
        setAudioSyncMs = setAudioSyncMs,
        setShowControls = setShowControls,
        incrementMenuTouchKey = incrementMenuTouchKey,
    )
}
