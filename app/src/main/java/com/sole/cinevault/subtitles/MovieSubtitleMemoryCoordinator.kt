package com.sole.cinevault.subtitles

import android.content.Context
import com.sole.cinevault.DualSubtitleState
import com.sole.cinevault.SubtitleAppearanceUiState
import com.sole.cinevault.SubtitleCoreUiState
import com.sole.cinevault.SubtitleTrackSelectionState
import kotlinx.coroutines.delay

/**
 * Owns per-movie subtitle appearance restore and persistence.
 *
 * Position is normalized on both restore and save so legacy values that could
 * push subtitles off-screen are repaired once and are not persisted again.
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
            appearanceUi.textSizeSp = memory.textSizeSp.coerceIn(12f, 32f)
            appearanceUi.bottomPadding =
                SubtitlePositionPolicy.sanitize(
                    memory.bottomPadding,
                    appearanceUi.textSizeSp,
                )
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

        val safeTextSize = appearanceUi.textSizeSp.coerceIn(12f, 32f)
        val safeBottomPadding =
            SubtitlePositionPolicy.sanitize(
                appearanceUi.bottomPadding,
                safeTextSize,
            )

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
                textSizeSp = safeTextSize,
                bottomPadding = safeBottomPadding,
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
