package com.sole.cinevault.subtitles

import android.content.Context
import com.sole.cinevault.DualSubtitleState
import com.sole.cinevault.SubtitleAppearanceUiState
import com.sole.cinevault.SubtitleCoreUiState
import com.sole.cinevault.SubtitleTrackSelectionState
import kotlinx.coroutines.delay

/**
 * Slice 30: owns per-movie subtitle appearance restore and persistence.
 *
 * VideoPlayerScreen still owns the LaunchedEffect keys because Compose should
 * cancel/restart them when the movie or settings change. This coordinator owns
 * the actual restore mutation, debounce, snapshot construction and save.
 */
class MovieSubtitleMemoryCoordinator(
    private val context: Context,
    private val coreUi: SubtitleCoreUiState,
    private val trackUi: SubtitleTrackSelectionState,
    private val dualUi: DualSubtitleState,
    private val appearanceUi: SubtitleAppearanceUiState,
    private val getDualSecondaryColorHex: () -> String,
    private val setMovieAppearanceMemoryReady: (Boolean) -> Unit,
) {
    fun restoreAppearance(
        memoryReady: Boolean,
        memory: MovieSubtitleMemory?,
    ) {
        if (memoryReady && memory != null) {
            appearanceUi.textSizeSp = memory.textSizeSp
            appearanceUi.bottomPadding = memory.bottomPadding
            appearanceUi.preset = memory.presetName
            appearanceUi.appearance = SubtitleAppearance(
                memory.foregroundColor,
                memory.edgeType,
                memory.edgeColor,
                memory.backgroundColor,
            )
            appearanceUi.preserveOriginalStyling =
                memory.preserveOriginalStyling
        }

        setMovieAppearanceMemoryReady(true)
    }

    suspend fun saveAfterDebounce(
        videoPath: String,
        subtitleMemoryReady: Boolean,
        appearanceMemoryReady: Boolean,
    ) {
        if (!subtitleMemoryReady || !appearanceMemoryReady) return

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
                textSizeSp = appearanceUi.textSizeSp,
                bottomPadding = appearanceUi.bottomPadding,
                presetName = appearanceUi.preset,
                foregroundColor = appearanceUi.appearance.foregroundColor,
                edgeType = appearanceUi.appearance.edgeType,
                edgeColor = appearanceUi.appearance.edgeColor,
                backgroundColor = appearanceUi.appearance.backgroundColor,
                preserveOriginalStyling =
                    appearanceUi.preserveOriginalStyling,
            ),
        )
    }
}
