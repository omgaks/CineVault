package com.sole.cinevault

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.sole.cinevault.subtitles.MovieSubtitleMemoryCoordinator

/**
 * Per-movie subtitle session persistence. Display appearance is intentionally
 * excluded so PHONE/TABLET/EXTERNAL profiles remain the single source of truth.
 *
 * MovieSubtitleMemoryCoordinator still owns the actual restore/save behavior.
 * This file only removes the large effect-key wiring from VideoPlayerScreen.
 */
@Composable
fun PlayerMovieSubtitleMemoryEffects(
    context: Context,
    videoPath: String,
    movieSubtitleMemoryReady: Boolean,
    coreUi: SubtitleCoreUiState,
    trackUi: SubtitleTrackSelectionState,
    dualUi: DualSubtitleState,
    dualSecondaryColorHex: String,
) {
    val currentDualSecondaryColorHex by rememberUpdatedState(dualSecondaryColorHex)
    val coordinator = remember(context, coreUi, trackUi, dualUi) {
        MovieSubtitleMemoryCoordinator(
            context = context,
            coreUi = coreUi,
            trackUi = trackUi,
            dualUi = dualUi,
            getDualSecondaryColorHex = { currentDualSecondaryColorHex },
        )
    }

    LaunchedEffect(
        videoPath,
        movieSubtitleMemoryReady,
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
    ) {
        coordinator.saveAfterDebounce(
            videoPath = videoPath,
            subtitleMemoryReady = movieSubtitleMemoryReady,
        )
    }
}
