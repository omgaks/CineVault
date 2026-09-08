package com.sole.cinevault.subtitles

import android.content.Context
import android.widget.Toast
import com.sole.cinevault.AudioSyncHolder
import com.sole.cinevault.DriftCorrectionState
import com.sole.cinevault.SubtitleAppearanceUiState
import com.sole.cinevault.SubtitleCoreUiState
import com.sole.cinevault.SubtitleTrackSelectionState

/**
 * Slice 23: owns the full "Reset subtitle settings" operation.
 *
 * This used to live as a local function inside VideoPlayerScreen and directly
 * reset visual styling, subtitle offset, drift correction and audio-sync state
 * together. Keeping that orchestration here gives both current and future reset
 * entry points one source of truth.
 */
class SubtitleResetCoordinator(
    private val context: Context,
    private val displayProfileType: DisplayProfileType,
    private val isLandscape: Boolean,
    private val appearanceUi: SubtitleAppearanceUiState,
    private val coreUi: SubtitleCoreUiState,
    private val trackUi: SubtitleTrackSelectionState,
    private val driftUi: DriftCorrectionState,
    private val setAudioSyncMs: (Int) -> Unit,
    private val setShowControls: (Boolean) -> Unit,
    private val incrementMenuTouchKey: () -> Unit,
) {
    fun reset() {
        clearSubtitleProfileSettings(
            context,
            displayProfileType,
            isLandscape,
        )

        val defaults = defaultSubtitleProfileSettings(
            displayProfileType,
            isLandscape,
        )

        appearanceUi.textSizeSp = defaults.fontSizeSp
        appearanceUi.bottomPadding = defaults.bottomPadding
        appearanceUi.preset = defaults.presetName
        appearanceUi.appearance = SubtitleAppearance(
            defaults.foregroundColor,
            defaults.edgeType,
            defaults.edgeColor,
            defaults.backgroundColor,
        )
        appearanceUi.preserveOriginalStyling = false

        coreUi.syncOffset = 0f
        trackUi.appliedOffsetMs = 0L

        driftUi.scale = 1f
        driftUi.appliedScale = 1f
        driftUi.pointA = null
        driftUi.pointB = null

        AudioSyncHolder.offsetUs = 0L
        setAudioSyncMs(0)

        Toast.makeText(
            context,
            "Subtitle settings reset for ${displayProfileType.label}",
            Toast.LENGTH_SHORT,
        ).show()

        setShowControls(true)
        incrementMenuTouchKey()
    }
}
