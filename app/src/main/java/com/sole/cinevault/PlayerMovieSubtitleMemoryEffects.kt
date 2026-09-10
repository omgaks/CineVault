package com.sole.cinevault

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.sole.cinevault.subtitles.MovieSubtitleMemory
import com.sole.cinevault.subtitles.MovieSubtitleMemoryCoordinator

/**
 * Slice 49: Compose lifecycle wrapper for per-movie subtitle appearance
 * restoration and subtitle-memory persistence.
 *
 * MovieSubtitleMemoryCoordinator still owns the actual restore/save behavior.
 * This file only removes the large effect-key wiring from VideoPlayerScreen.
 */
@Composable
fun PlayerMovieSubtitleMemoryEffects(
    context: Context,
    videoPath: String,
    displayProfileName: String,
    isLandscape: Boolean,
    movieSubtitleMemory: MovieSubtitleMemory?,
    movieSubtitleMemoryReady: Boolean,
    movieAppearanceMemoryReady: Boolean,
    coreUi: SubtitleCoreUiState,
    trackUi: SubtitleTrackSelectionState,
    dualUi: DualSubtitleState,
    appearanceUi: SubtitleAppearanceUiState,
    dualSecondaryColorHex: String,
    onMovieAppearanceMemoryReadyChanged: (Boolean) -> Unit,
) {
    val movieAppearanceProfileKey =
        "$videoPath|$displayProfileName|$isLandscape"

    val coordinator = remember {
        MovieSubtitleMemoryCoordinator(
            context = context,
            coreUi = coreUi,
            trackUi = trackUi,
            dualUi = dualUi,
            appearanceUi = appearanceUi,
            getDualSecondaryColorHex = { dualSecondaryColorHex },
            setMovieAppearanceMemoryReady =
                onMovieAppearanceMemoryReadyChanged,
        )
    }

    LaunchedEffect(
        movieAppearanceProfileKey,
        movieSubtitleMemory,
        movieSubtitleMemoryReady,
    ) {
        coordinator.restoreAppearance(
            memoryReady = movieSubtitleMemoryReady,
            memory = movieSubtitleMemory,
        )
    }

    LaunchedEffect(
        videoPath,
        movieSubtitleMemoryReady,
        movieAppearanceMemoryReady,
        coreUi.subtitlesEnabled,
        trackUi.primaryUri,
        trackUi.primaryLanguage,
        trackUi.selectedKey,
        trackUi.selectedLabel,
        trackUi.selectedSource,
        dualUi.enabled,
        dualUi.secondaryLanguage,
        dualUi.gapLines,
        dualUi.secondarySourceLabel,
        dualSecondaryColorHex,
        coreUi.syncOffset,
        appearanceUi.textSizeSp,
        appearanceUi.bottomPadding,
        appearanceUi.preset,
        appearanceUi.appearance,
        appearanceUi.preserveOriginalStyling,
    ) {
        coordinator.saveAfterDebounce(
            videoPath = videoPath,
            subtitleMemoryReady = movieSubtitleMemoryReady,
            appearanceMemoryReady = movieAppearanceMemoryReady,
        )
    }
}
