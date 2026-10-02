package com.sole.cinevault.subtitles

import android.content.Context
import com.sole.cinevault.DualSubtitleState
import com.sole.cinevault.SubtitleCoreUiState
import com.sole.cinevault.SubtitleTrackSelectionState
import kotlinx.coroutines.delay

/**
 * Owns per-movie subtitle session persistence only.
 * Display-specific appearance is owned exclusively by subtitle display profiles.
 */
class MovieSubtitleMemoryCoordinator(
    private val context: Context,
    private val coreUi: SubtitleCoreUiState,
    private val trackUi: SubtitleTrackSelectionState,
    private val dualUi: DualSubtitleState,
    private val getDualSecondaryColorHex: () -> String,
) {
    suspend fun saveAfterDebounce(
        videoPath: String,
        subtitleMemoryReady: Boolean,
    ) {
        if (!subtitleMemoryReady) return

        delay(600)

        saveMovieSubtitleMemory(
            context = context,
            videoPath = videoPath,
            memory = MovieSubtitleMemory(
                subtitlesEnabled = coreUi.subtitlesEnabled,
                primaryUri = trackUi.primaryUri?.toString(),
                primaryLanguage = trackUi.primaryLanguage,
                selectedKey = trackUi.selectedKey,
                selectedLabel = trackUi.selectedLabel,
                selectedSource = trackUi.selectedSource,
                dualEnabled = dualUi.enabled,
                dualSecondaryLanguage = dualUi.secondaryLanguage,
                dualGapLines = dualUi.gapLines,
                dualSecondarySource = dualUi.secondarySourceLabel,
                dualSecondaryColorHex = getDualSecondaryColorHex(),
                syncOffsetSeconds = coreUi.syncOffset,
            ),
        )
    }
}
